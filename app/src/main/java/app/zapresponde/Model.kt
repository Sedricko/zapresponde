package app.zapresponde

import kotlinx.serialization.Serializable

enum class Match(val label: String) {
    ALL("Qualquer mensagem"), CONTAINS("Contém"), EXACT("Exata"), STARTS("Começa com"),
    ENDS("Termina com"), WILDCARD("Curinga (* e //)"), REGEX("Regex"), SIMILAR("Parecida")
}

enum class Who(val label: String) {
    ALL("Todos"), SAVED("Só contatos salvos"), UNSAVED("Só números não salvos"),
    LIST("Só a lista"), EXCEPT_LIST("Todos menos a lista"), NONE("Ninguém (só grupos)")
}

enum class Groups(val label: String) {
    NONE("Nenhum grupo"), ALL("Todos os grupos"), LIST("Só a lista"), EXCEPT_LIST("Todos menos a lista")
}

enum class Freq(val label: String) {
    ALWAYS("Sempre"), ONCE("Uma vez só"), INTERVAL("Uma vez a cada X minutos"), MAX("No máximo N respostas")
}

enum class ReplyMode(val label: String) { SINGLE("Uma resposta"), SEQUENCE("Todas em sequência"), RANDOM("Uma sorteada") }

enum class Kind(val label: String) { TEXT("Resposta de texto"), LOOKUP("Consulta na planilha") }

@Serializable
data class Rule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Nova regra",
    val enabled: Boolean = true,
    val kind: Kind = Kind.TEXT,
    val match: Match = Match.CONTAINS,
    val keywords: String = "",
    val exclude: String = "",
    val similarity: Int = 80,
    val replies: List<String> = listOf(""),
    val replyMode: ReplyMode = ReplyMode.SINGLE,
    val who: Who = Who.ALL,
    val whoList: String = "",
    val groups: Groups = Groups.NONE,
    val groupList: String = "",
    val freq: Freq = Freq.ALWAYS,
    val intervalMin: Int = 60,
    val maxReplies: Int = 3,
    val hours: String = "",
    val days: Set<Int> = (1..7).toSet(),
    // LOOKUP: column letters in the "Consulta" tab
    val searchCol: String = "A",
    val returnCol: String = "B",
    val template: String = "{resultado}",
    val fallback: String = "",
    val fromSheet: Boolean = false,
)

@Serializable
data class Config(
    val enabled: Boolean = false,
    val scheduleOn: Boolean = false,
    val scheduleHours: String = "18:00-08:00",
    val scheduleDays: Set<Int> = (1..7).toSet(),
    val delayOn: Boolean = true,
    val delayMinSec: Int = 3,
    val delayMaxSec: Int = 10,
    val headerFooterOn: Boolean = false,
    val header: String = "",
    val footer: String = "",
    val welcomeOn: Boolean = false,
    val welcomeText: String = "Olá {primeiro_nome}, {saudacao}! Obrigado pelo contato.",
    val welcomeDaily: Boolean = false,
    val formOn: Boolean = false,
    val formTrigger: String = "pedido, encomenda",
    val formQuestions: String = "Qual o seu nome?\nQual o produto?\nPara qual data?",
    val formDone: String = "Pedido anotado! Em breve confirmamos.",
    val formCancel: String = "cancelar",
    val takeoverOn: Boolean = true,
    val takeoverMin: Int = 60,
    val loopGuardOn: Boolean = true,
    val sheetOn: Boolean = false,
    val sheetUrl: String = "",
    val sheetKey: String = "",
    val sheetSyncMin: Int = 15,
    val sheetRulesOn: Boolean = true,
    val sheetLogOn: Boolean = true,
)

@Serializable
class ChatState(
    var pauseUntil: Long = 0,
    var welcomedDay: String = "",
    var replyCount: Int = 0,
    var lastBotReply: Long = 0,
    val recentReplies: MutableList<Long> = mutableListOf(),
    val lastTexts: MutableList<String> = mutableListOf(),
    var formStep: Int = -1,
    val formAnswers: MutableList<String> = mutableListOf(),
    // ruleId -> [count, lastMillis]
    val rules: MutableMap<String, LongArray> = mutableMapOf(),
)

@Serializable
class State(val chats: MutableMap<String, ChatState> = mutableMapOf())

data class Incoming(
    val chat: String,
    val sender: String,
    val text: String,
    val isGroup: Boolean,
    val app: String = "WhatsApp",
)

data class Decision(
    val replies: List<String>,
    val rule: String,
    val delayMs: Long = 0,
    val formRow: List<String>? = null,
    val reason: String = "",
)
