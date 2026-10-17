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
data class HistoryEntry(
    val time: Long, val app: String, val chat: String, val isGroup: Boolean,
    val text: String, val reply: String, val rule: String,
)

@Serializable
data class SheetCache(
    val rules: List<Rule> = emptyList(),
    val consulta: List<List<String>> = emptyList(),
    val syncedAt: Long = 0,
    val error: String = "",
)

/** Rows waiting to be appended to a tab, kept on disk so nothing is lost without internet. */
@Serializable
data class Outbox(val historico: List<List<String>> = emptyList(), val pedidos: List<List<String>> = emptyList())

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
        state = load("state.json") ?: State()
        outbox = load("outbox.json") ?: Outbox()
    }

    private inline fun <reified T> load(name: String): T? = runCatching {
        json.decodeFromString<T>(File(dir, name).readText())
    }.getOrNull()

    private inline fun <reified T> save(name: String, value: T) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(json.encodeToString(value))
        tmp.renameTo(File(dir, name))
    }

    @Synchronized
    fun setConfig(c: Config) {
        // Turning auto-reply on starts a fresh round: "reply once" counters reset.
        if (c.enabled && !config.enabled) {
            state.chats.values.forEach { it.rules.clear() }
            saveState()
        }
        config = c; save("config.json", c)
    }

    @Synchronized
    fun setRules(r: List<Rule>) { rules = r; save("rules.json", r) }

    @Synchronized
    fun setSheet(s: SheetCache) { sheet = s; save("sheet.json", s) }

    @Synchronized
    fun saveState() = save("state.json", state)

    fun allRules() = rules + if (config.sheetOn && config.sheetRulesOn) sheet.rules else emptyList()

    @Synchronized
    fun addHistory(e: HistoryEntry) {
        history = (listOf(e) + history).take(500)
        save("history.json", history)
    }

    @Synchronized
    fun queue(historico: List<String>? = null, pedido: List<String>? = null) {
        outbox = Outbox(
            outbox.historico + listOfNotNull(historico),
            outbox.pedidos + listOfNotNull(pedido),
        )
        save("outbox.json", outbox)
    }

    /** Removes the rows that were just uploaded, keeping anything queued meanwhile. */
    @Synchronized
    fun sent(historico: Int, pedidos: Int) {
        outbox = Outbox(outbox.historico.drop(historico), outbox.pedidos.drop(pedidos))
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
