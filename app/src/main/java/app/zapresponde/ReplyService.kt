package app.zapresponde

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class ReplyService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pending = HashMap<String, Job>()
    private val seen = ArrayDeque<String>()

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in APPS) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val action = replyAction(n) ?: return
        val msg = parse(sbn) ?: return

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
        }
        // The freight sheet changes all the time; read it fresh so a new load is not answered as "indisponível".
        if (cfg.freteOn && cfg.sheetOn && cfg.sheetUrl.isNotBlank() && Engine.freteRoute(msg.text) != null) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { Sheets.refresh(cfg) } }
                answer(msg, action, sbn)
            }
            return
        }
        answer(msg, action, sbn)
    }

    private fun answer(msg: Incoming, action: Notification.Action, sbn: StatusBarNotification) {
        val cfg = Store.config
        val d = synchronized(Store) {
            Engine.decide(
                msg, LocalDateTime.now(), System.currentTimeMillis(), cfg, Store.allRules(),
                Store.sheet.consulta, Store.state, phoneNames = Contacts.names(this),
            ).also { Store.saveState() }
        }
        Log.d(TAG, "${msg.chat}: ${d.reason}")
        if (d.replies.isEmpty()) return

        // A newer message from the same chat replaces a reply that is still waiting.
        pending.remove(msg.chat)?.cancel()
        pending[msg.chat] = scope.launch {
            delay(d.delayMs)
            d.replies.forEachIndexed { i, text ->
                if (i > 0) delay(1500)
                if (!send(action, text)) return@launch
            }
            pending.remove(msg.chat)
            val entry = HistoryEntry(
                System.currentTimeMillis(), APPS[sbn.packageName]!!, msg.chat, msg.isGroup,
                msg.text, d.replies.joinToString("\n---\n"), d.rule,
            )
            Store.addHistory(entry)
            if (cfg.sheetOn && cfg.sheetLogOn) Store.queue(historico = Sheets.historyRow(entry))
            d.formRow?.let { Store.queue(pedido = listOf(Sheets.historyRow(entry)[0]) + it) }
            if (cfg.sheetOn && (cfg.sheetLogOn || d.formRow != null)) Sheets.syncNow(this@ReplyService)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val cfg = Store.config
        if (!cfg.takeoverOn || sbn.packageName !in APPS) return
        val msg = parse(sbn) ?: return
        val now = System.currentTimeMillis()
        val lastBot = Store.state.chats[msg.chat]?.lastBotReply ?: 0
        // Tapping the notification, or WhatsApp clearing it because the chat was opened, means you took over.
        // Our own reply also makes WhatsApp clear it, so ignore removals right after we answered.
        val tookOver = reason == REASON_CLICK || (reason == REASON_APP_CANCEL && now - lastBot > 15_000)
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
