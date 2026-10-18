package app.zapresponde

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.LocalDateTime

class ReplyService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pending = HashMap<String, Job>()
    private val seen = ArrayDeque<String>()

    private class Approval(val msg: Incoming, val action: Notification.Action, val pkg: String,
                           val decision: Decision, val before: ChatState?)
    private val approvals = HashMap<Int, Approval>()
    private var nextId = 1

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        instance = this
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        Health.mark(this, "connectedAt")
        Store.log("(sistema)", "", "Serviço de notificações conectado")
    }

    override fun onListenerDisconnected() {
        Health.mark(this, "disconnectedAt")
        Store.log("(sistema)", "", "Android desconectou o serviço; pedindo reconexão")
        if (Build.VERSION.SDK_INT >= 24) requestRebind(ComponentName(this, ReplyService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in APPS) return
        Health.mark(this, "notifAt")
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val action = replyAction(n)
        val msg = parse(sbn) ?: run {
            if (n.category == Notification.CATEGORY_MESSAGE) {
                Health.count(this, "unreadable")
                Store.log("(não lida)", "", "Notificação de mensagem sem título ou texto: não deu para ler")
            }
            return
        }
        if (action != null) Health.mark(this, "replyNotifAt")
        if (action == null) {
            // A real message without Reply is worth seeing in the diagnostic; WhatsApp's own notices are noise.
            if (n.category == Notification.CATEGORY_MESSAGE) Store.log(msg.chat, msg.text, "Sem botão Responder na notificação")
            return
        }

        // WhatsApp re-posts the same notification with the whole history; handle each message once.
        val key = "${msg.chat}|${msg.text}|${lastMessageTime(n)}"
        if (key in seen) return
        seen.addLast(key); if (seen.size > 200) seen.removeFirst()

        val cfg = Store.config
        val now = System.currentTimeMillis()
        val cs = Store.state.chats[msg.chat]
        if (msg.fromMe && cfg.takeoverOn && msg.text !in cs?.lastTexts.orEmpty() && now - (cs?.lastBotReply ?: 0) > 15_000) {
            pending.remove(msg.chat)?.cancel()
            Store.pauseChat(msg.chat, now + cfg.takeoverMin * 60_000L)
            Store.log(msg.chat, msg.text, "Mensagem sua: conversa pausada por ${cfg.takeoverMin} min")
        }
        // The freight sheet changes all the time; read it fresh so a new load is not answered as "indisponível".
        if (cfg.freteOn && cfg.sheetOn && cfg.sheetUrl.isNotBlank() && (Engine.freteRoute(msg.text) != null ||
                (cfg.freteLivreOn && Engine.freteLivre(msg.text, Store.sheet.consulta) != null))
        ) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { Sheets.refresh(cfg, fast = true) }.onFailure {
                        Store.log(msg.chat, msg.text, "Planilha não atualizou na hora (${it.message}); usando a última cópia")
                    }
                }
                answer(msg, action, sbn)
            }
            return
        }
        answer(msg, action, sbn)
    }

    private fun answer(msg: Incoming, action: Notification.Action, sbn: StatusBarNotification) {
        val cfg = Store.config
        val before = synchronized(Store) { Store.state.chats[msg.chat]?.let(::copyOf) }
        val d = synchronized(Store) {
            Engine.decide(
                msg, LocalDateTime.now(), System.currentTimeMillis(), cfg, Store.allRules(),
                Store.sheet.consulta, Store.state, phoneNames = Contacts.names(this),
            ).also { Store.saveState() }
        }
        Log.d(TAG, "${msg.chat}: ${d.reason}")
        if (d.replies.isEmpty()) { Store.log(msg.chat, msg.text, d.reason); return }
        val open = sbn.notification.contentIntent
        if (cfg.alertOn) d.alert?.let { Alerts.show(this, "Carga pedida", it, open) }

        val list = cfg.approvalList.lines().map { Engine.normalize(it) }.filter { it.isNotEmpty() }
        if (cfg.approvalOn && (list.isEmpty() || Engine.normalize(msg.chat) in list)) {
            val id = nextId++
            approvals[id] = Approval(msg, action, sbn.packageName, d, before)
            Store.log(msg.chat, msg.text, "Aguardando sua aprovação (${d.rule})")
            Alerts.approval(this, id, msg.chat, d.replies.joinToString("\n---\n"), open)
            return
        }
        deliver(msg, action, sbn.packageName, d, before)
    }

    /** "Enviar" or "Descartar" tapped on an approval notification. */
    fun approve(id: Int, send: Boolean) {
        val a = approvals.remove(id) ?: return
        Alerts.cancel(this, id)
        if (send) deliver(a.msg, a.action, a.pkg, a.decision.copy(delayMs = 0), a.before)
        else { rollback(a.msg.chat, a.before); Store.log(a.msg.chat, a.msg.text, "Resposta descartada por você") }
    }

    private fun copyOf(cs: ChatState): ChatState = Store.json.decodeFromString(Store.json.encodeToString(cs))

    /** A reply that was not sent gives back what its decision consumed ("uma vez", counters), keeping a newer pause. */
    private fun rollback(chat: String, before: ChatState?) = synchronized(Store) {
        val pause = Store.state.chats[chat]?.pauseUntil ?: 0
        Store.state.chats[chat] = before?.let(::copyOf) ?: ChatState()
        Store.state.chats[chat]!!.pauseUntil = maxOf(pause, before?.pauseUntil ?: 0)
        Store.saveState()
    }

    private fun deliver(msg: Incoming, action: Notification.Action, pkg: String, d: Decision, before: ChatState?) {
        val cfg = Store.config
        // A newer message from the same chat replaces a reply that is still waiting.
        pending.remove(msg.chat)?.cancel()
        pending[msg.chat] = scope.launch {
            try {
                delay(d.delayMs)
                d.replies.forEachIndexed { i, text ->
                    if (i > 0) delay(1500)
                    if (!send(action, text)) {
                        rollback(msg.chat, before)
                        Store.log(msg.chat, msg.text, "Falhou ao enviar pelo WhatsApp (estado devolvido)")
                        return@launch
                    }
                }
            } catch (e: CancellationException) {
                // No rollback here: the newer message (or your pause) already changed this chat's state.
                Store.log(msg.chat, msg.text, "Resposta cancelada antes de enviar")
                throw e
            }
            pending.remove(msg.chat)
            Store.log(msg.chat, msg.text, "Respondido (${d.rule})")
            val entry = HistoryEntry(
                System.currentTimeMillis(), APPS[pkg] ?: pkg, msg.chat, msg.isGroup,
                msg.text, d.replies.joinToString("\n---\n"), d.rule,
            )
            Store.addHistory(entry)
            val date = Sheets.historyRow(entry)[0]
            if (cfg.sheetOn && cfg.sheetLogOn) Store.queue(historico = Sheets.historyRow(entry))
            d.formRow?.let { Store.queue(pedido = listOf(date) + it) }
            val lead = d.leadRow?.takeIf { cfg.sheetOn && cfg.leadsOn }?.also { Store.queue(lead = listOf(date) + it) }
            if (cfg.sheetOn && (cfg.sheetLogOn || d.formRow != null || lead != null)) Sheets.syncNow(this@ReplyService)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val cfg = Store.config
        // Only real chats: WhatsApp's own notices ("Procurando novas mensagens") have no Reply button.
        if (!cfg.takeoverOn || sbn.packageName !in APPS || replyAction(sbn.notification) == null) return
        val msg = parse(sbn) ?: return
        val now = System.currentTimeMillis()
        val lastBot = Store.state.chats[msg.chat]?.lastBotReply ?: 0
        // Tapping the notification means you took over. WhatsApp also clears it when the chat is merely read,
        // including on WhatsApp Web, so that only counts if the user asked for it (off by default).
        // Our own reply also makes WhatsApp clear it, so ignore removals right after we answered.
        val tookOver = reason == REASON_CLICK || (cfg.takeoverOnRead && reason == REASON_APP_CANCEL && now - lastBot > 15_000)
        if (tookOver) {
            pending.remove(msg.chat)?.cancel()
            Store.pauseChat(msg.chat, now + cfg.takeoverMin * 60_000L)
        }
    }

    private fun send(action: Notification.Action, text: String): Boolean = try {
        val inputs = action.remoteInputs
        val bundle = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(inputs, intent, bundle)
        action.actionIntent.send(this, 0, intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "falha ao enviar", e); false
    }

    companion object {
        private const val TAG = "ZapResponde"
        var instance: ReplyService? = null
            private set
        val APPS = mapOf("com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business")

        fun replyAction(n: Notification): Notification.Action? =
            n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
                ?: Notification.WearableExtender(n).actions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }

        private fun lastMessage(n: Notification): Bundle? =
            n.extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? Bundle

        private fun lastMessageTime(n: Notification) = lastMessage(n)?.getLong("time") ?: n.`when`

        fun parse(sbn: StatusBarNotification): Incoming? {
            val n = sbn.notification
            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
            var text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null
            var sender = title
            var fromMe = false
            lastMessage(n)?.let { b ->
                b.getCharSequence("text")?.let { text = it.toString() }
                b.getCharSequence("sender")?.let { sender = it.toString() }
                // MessagingStyle leaves the sender empty on messages the phone owner sent.
                fromMe = b.getCharSequence("sender") == null && b.get("sender_person") == null
            }
            val conv = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            var isGroup = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
            var chat = if (isGroup) conv ?: title else title
            // Older format: title "Group: Sender"
            if (!isGroup && conv == null && ": " in title && sender == title) {
                isGroup = true
                chat = title.substringBefore(": "); sender = title.substringAfter(": ")
            }
            // Group titles can carry a counter like "Família (3 mensagens)".
            chat = chat.replace(Regex("\\s*\\(\\d+ \\w+\\)$"), "")
            if (text.isBlank()) return null
            return Incoming(chat, sender, text, isGroup, APPS[sbn.packageName] ?: sbn.packageName, fromMe)
        }
    }
}
