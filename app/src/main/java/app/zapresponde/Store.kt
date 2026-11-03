package app.zapresponde

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SheetCache(
    val rules: List<Rule> = emptyList(),
    val consulta: List<List<String>> = emptyList(),
    val syncedAt: Long = 0,
    val error: String = "",
)

/** Rows waiting to be appended to a tab, kept on disk so nothing is lost without internet. */
@Serializable
data class Outbox(
    val historico: List<List<String>> = emptyList(),
    val pedidos: List<List<String>> = emptyList(),
    val leads: List<List<String>> = emptyList(),
)

/** What "Exportar backup" writes: all settings and the app's own rules (sheet rules live in the sheet). */
@Serializable
data class Backup(val app: String = "ZapResponde", val version: Int = 1, val config: Config = Config(), val rules: List<Rule> = emptyList())

/** Single in-process store. Compose reads the mutableState fields; everything is persisted as JSON files. */
object Store {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var dir: File

    var config by mutableStateOf(Config())
        private set
    var rules by mutableStateOf(listOf<Rule>())
        private set
    var sheet by mutableStateOf(SheetCache())
        private set
    var history by mutableStateOf(listOf<HistoryEntry>())
        private set
    var events = listOf<LogEntry>()
        private set
    var state = State()
        private set
    var outbox = Outbox()
        private set

    @Synchronized
    fun init(ctx: Context) {
        if (::dir.isInitialized) return
        dir = ctx.applicationContext.filesDir
        config = load("config.json") ?: Config()
        rules = load("rules.json") ?: defaultRules()
        sheet = load("sheet.json") ?: SheetCache()
        history = load("history.json") ?: emptyList()
        events = load("events.json") ?: emptyList()
        state = load("state.json") ?: State()
        outbox = load("outbox.json") ?: Outbox()
        undo = load("undo.json") ?: Undo()
        traceLog = load("trace.json") ?: emptyList()
        Assistant.onTrace = { trace("IA", it) }
        // The app closed by an error last time: the handler below left the reason in crash.txt.
        File(dir, "crash.txt").takeIf { it.exists() }?.let { f ->
            log(INTERNAL, "O app fechou por erro", f.readText().lines().first().take(300), f.readText().lines().drop(1).take(6))
            f.delete()
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            // Written right away (not through the writer thread): the process is about to die.
            runCatching { File(dir, "crash.txt").writeText("${e.javaClass.simpleName}: ${e.message}\n" + e.stackTrace.take(6).joinToString("\n")) }
            previous?.uncaughtException(t, e)
        }
    }

    /** Chat name of the internal-error records ("Funcionamento interno" in the diagnostic). */
    const val INTERNAL = "(erro interno)"

    /** Records an error the app survived: where it happened and why, with the first lines of the stack. */
    fun error(where: String, e: Throwable) {
        android.util.Log.w("ZapResponde", where, e)
        log(INTERNAL, where, "${e.javaClass.simpleName}: ${e.message}".take(300), e.stackTrace.take(4).map { "at $it" })
        trace("ERRO", "$where: ${e.javaClass.name}: ${e.message} ← " + e.stackTrace.take(10).joinToString(" ← "))
    }

    /**
     * Internal monitoring: everything the app does, newest first, the last 3000 lines. Saved at most every 5 s on its
     * own thread (encoding thousands of lines on each event would slow replies and the screen).
     */
    var traceLog = listOf<TraceEntry>()
        private set
    private val traceSaver = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    private var traceDirty = false

    fun trace(cat: String, msg: String) {
        synchronized(this) {
            traceLog = (listOf(TraceEntry(System.currentTimeMillis(), cat, msg.take(800))) + traceLog).take(3000)
            if (traceDirty || !::dir.isInitialized) return
            traceDirty = true
        }
        traceSaver.schedule({
            val text = synchronized(this) { traceDirty = false; json.encodeToString(traceLog) }
            val tmp = File(dir, "trace.json.tmp"); tmp.writeText(text); tmp.renameTo(File(dir, "trace.json"))
        }, 5, java.util.concurrent.TimeUnit.SECONDS)
    }

    @Synchronized
    fun clearTrace(): Int { val n = traceLog.size; traceLog = emptyList(); save("trace.json", traceLog); return n }

    private inline fun <reified T> load(name: String): T? = runCatching {
        json.decodeFromString<T>(File(dir, name).readText())
    }.getOrNull()

    /** Files are written on one background thread, in order, so a reply or the screen never waits for the disk. */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    private inline fun <reified T> save(name: String, value: T) {
        val text = json.encodeToString(value)
        writer.execute {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, name))
        }
    }

    /** Settings and rules before the last change the AI applied, so "Desfazer" can bring them back (also after a restart). */
    @Serializable
    data class Undo(val time: Long = 0, val backup: String = "")

    var undo by mutableStateOf(Undo())
        private set

    @Synchronized
    fun saveUndo() { undo = Undo(System.currentTimeMillis(), exportBackup()); save("undo.json", undo) }

    @Synchronized
    fun undoAi() {
        if (undo.backup.isEmpty()) return
        importBackup(undo.backup, "desfazer da IA")
        undo = Undo(); save("undo.json", undo)
    }

    fun exportBackup(): String = json.encodeToString(Backup(config = config.copy(aiKey = ""), rules = rules))

    /** Replaces settings and rules with a backup file's; throws with a readable message if it isn't one. */
    fun importBackup(text: String, origin: String = "backup") {
        val b = runCatching { json.decodeFromString<Backup>(text) }.getOrNull()
        require(b != null && b.app == "ZapResponde") { "Esse arquivo não é um backup do ZapResponde." }
        updateConfig(b.config.copy(aiKey = config.aiKey), origin); updateRules(b.rules, origin)
    }

    /** [origin] says who changed it (você, IA, backup, desfazer): every change goes to the monitoring, before → after. */
    @Synchronized
    fun updateConfig(c: Config, origin: String = "você") {
        if (::dir.isInitialized) AiConfig.diff(config, c).forEach { trace("CONFIG", "($origin) $it") }
        // Turning auto-reply on starts a fresh round: "reply once" counters reset.
        if (c.enabled && !config.enabled) {
            state.chats.values.forEach { it.rules.clear() }
            saveState()
        }
        config = c; save("config.json", c)
    }

    @Synchronized
    fun updateRules(r: List<Rule>, origin: String = "você") {
        AiConfig.diff(rules, r).forEach { trace("REGRAS", "($origin) $it") }
        rules = r; save("rules.json", r)
    }

    @Synchronized
    fun updateSheet(s: SheetCache) { sheet = s; save("sheet.json", s) }

    @Synchronized
    fun saveState() = save("state.json", state)

    fun allRules() = rules + if (config.sheetOn && config.sheetRulesOn) sheet.rules else emptyList()

    @Synchronized
    fun log(chat: String, text: String, result: String, trace: List<String> = emptyList()) {
        events = (listOf(LogEntry(System.currentTimeMillis(), chat, text, result, trace)) + events).take(200)
        save("events.json", events)
    }

    /**
     * "Limpar dados": what is checked, for one chat or (null) for all. Settings, rules, the Google sheet and the rows still
     * waiting to go to it are never touched. Returns how much was removed, for the confirmation message.
     */
    @Synchronized
    fun clear(history: Boolean, events: Boolean, chats: Boolean, waiting: Boolean, chat: String?): String {
        fun mine(c: String) = chat == null || c == chat
        val out = mutableListOf<String>()
        if (history) { val n = this.history.count { mine(it.chat) }; this.history = this.history.filterNot { mine(it.chat) }; save("history.json", this.history); out += "$n respostas" }
        if (events) { val n = this.events.count { mine(it.chat) }; this.events = this.events.filterNot { mine(it.chat) }; save("events.json", this.events); out += "$n registros" }
        if (chats) { val keys = state.chats.keys.filter(::mine); keys.forEach { state.chats.remove(it) }; out += "${keys.size} conversas" }
        if (waiting) { val n = state.waiting.count { mine(it.chat) }; state.waiting.removeAll { mine(it.chat) }; out += "$n pedidos esperando carga" }
        if (chats || waiting) saveState()
        trace("APP", "Limpar dados (${chat ?: "todas as conversas"}): ${out.joinToString()}")
        return out.joinToString()
    }

    /** Automatic cleanup: Histórico and Registro older than [keepMs]; the chats' state stays (it would resend welcomes and links). */
    @Synchronized
    fun prune(now: Long, keepMs: Long) {
        history = history.filter { now - it.time < keepMs }; save("history.json", history)
        events = events.filter { now - it.time < keepMs }; save("events.json", events)
        state.lastClean = now; saveState()
        trace("APP", "Limpeza automática: mantidas ${keepMs / 3_600_000L} h de histórico e registro")
    }

    @Synchronized
    fun addHistory(e: HistoryEntry) {
        history = (listOf(e) + history).take(300)
        save("history.json", history)
    }

    @Synchronized
    fun queue(historico: List<String>? = null, pedido: List<String>? = null, lead: List<String>? = null) {
        outbox = Outbox(
            outbox.historico + listOfNotNull(historico),
            outbox.pedidos + listOfNotNull(pedido),
            outbox.leads + listOfNotNull(lead),
        )
        save("outbox.json", outbox)
    }

    /** Removes the rows that were just uploaded, keeping anything queued meanwhile. */
    @Synchronized
    fun sent(historico: Int, pedidos: Int, leads: Int) {
        outbox = Outbox(outbox.historico.drop(historico), outbox.pedidos.drop(pedidos), outbox.leads.drop(leads))
        save("outbox.json", outbox)
    }

    @Synchronized
    fun pauseChat(chat: String, untilMs: Long) {
        state.chats.getOrPut(chat) { ChatState() }.pauseUntil = untilMs
        saveState()
    }

    /** Deep copy for dry runs in the test screen. */
    fun stateCopy(): State = json.decodeFromString(json.encodeToString(state))

    private fun defaultRules() = listOf(
        Rule(
            name = "Saudação", match = Match.CONTAINS, keywords = "oi, olá, ola, bom dia, boa tarde, boa noite",
            replies = listOf("Olá {primeiro_nome}, {saudacao}! Como posso ajudar?"), freq = Freq.INTERVAL, intervalMin = 240,
        ),
        Rule(
            name = "Fora do horário", enabled = false, match = Match.ALL, hours = "18:00-08:00",
            replies = listOf("Estamos fora do horário. Respondemos a partir das 8h."), freq = Freq.INTERVAL, intervalMin = 720,
        ),
    )
}
