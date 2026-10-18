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
    val takeoverOnRead: Boolean = false,
    val loopGuardOn: Boolean = true,
    val contactsOn: Boolean = false,
    val contactsMode: Audience = Audience.EXCEPT_LIST,
    /** One name per line, as the phone/WhatsApp shows it. */
    val contactsList: String = "",
    val groupsOn: Boolean = true,
    val freteOn: Boolean = false,
    /** Fretes escritos livremente ("Goiânia para Anápolis"), reconhecendo as cidades da planilha de cargas. */
    val freteLivreOn: Boolean = false,
    /** Words of a driver telling news, not asking ("Fechei uma carga para Rio Verde"): the written request is ignored. */
    val freteBlock: String = "fechei, fechou, fechamos, fechado, carreguei, carregando, descarreguei, descarregando, entreguei, entregando, peguei, cheguei, chegando, to indo, tô indo, estou indo, indo pra, indo para, voltando, já tenho, ja tenho",
    val leadsOn: Boolean = false,
    val alertOn: Boolean = false,
    val backupAutoOn: Boolean = false,
    val approvalOn: Boolean = false,
    /** One name per line; empty = every chat waits for approval. */
    val approvalList: String = "",
    /** Sent instead of the same load again to the same chat within an hour; blank = stay silent. */
    val freteRepeatText: String = "✅ Sim, essa carga ainda está disponível. Os detalhes estão na mensagem acima.",
    val freteNotFound: String = "❌ *FRETE INDISPONÍVEL*\n\nEsta carga já foi fechada ou expirou. Confira novas oportunidades no grupo oficial:\n🔗 (cole aqui o link do grupo)",
    val sheetOn: Boolean = false,
    val sheetUrl: String = "",
    val sheetKey: String = "",
    val sheetSyncMin: Int = 15,
    val sheetRulesOn: Boolean = true,
    val sheetLogOn: Boolean = true,
    val aiOn: Boolean = false,
    /** Chave da API do Gemini; fica fora do backup. */
    val aiKey: String = "",
    val aiModel: String = "gemini-flash-latest",
    /** Oferecer outra carga quando a pedida não existe ou fechou: mesma origem, mesmo destino, mesmo estado. */
    val altOn: Boolean = false,
    val altOrigin: Boolean = true,
    val altDest: Boolean = true,
    val altState: Boolean = false,
    val altText: String = "{pedida} não está disponível, mas tenho esta {motivo}:",
    /** Avisar quando a carga pedida aparecer na planilha. */
    val waitOn: Boolean = false,
    val waitMode: WaitMode = WaitMode.ASK,
    val waitHours: Int = 24,
    /** With requests waiting, the service reads the freight sheet this often (the background sync can't go below 15 min). */
    val waitReadMin: Int = 5,
    /** Acompanhamento: depois de uma conversa respondida pelo app, manda uma vez o link do grupo. */
    val followOn: Boolean = false,
    val followMin: Int = 30,
    val followText: String = "{saudacao}, {primeiro_nome}! Para acompanhar as novas cargas, entre no nosso grupo:\n🔗 {link}\n\nSe você já está no grupo, desconsidere 👍",
    val followLink: String = "",
    /** Frases que mostram que o contato já está no grupo; separadas por vírgula. */
    val followJoined: String = "já estou no grupo, já to no grupo, já sou do grupo, já participo, já faço parte, já estou nele, já entrei",
    /** Avisa na hora (vibra) quando o diagnóstico acha um problema grave, como respostas repetidas a um robô. */
    val problemAlertOn: Boolean = false,
    /** Diagnóstico pela IA: manda o relatório (com textos das mensagens) ao Gemini para explicar as decisões. */
    val aiDiagOn: Boolean = false,
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
    /** Acompanhamento: when to send it (0 = nothing pending) and whether this contact already got it or is in the group. */
    var followAt: Long = 0,
    var followDone: Boolean = false,
    /** Last message handled in this chat, to drop the same notification seen twice. */
    var lastInText: String = "",
    var lastInAt: Long = 0,
)

/** A freight request that found no active load: [from]/[to] are city keys (empty = any), [label] is for people. */
@Serializable
data class Waiting(val chat: String, val from: List<String>, val to: List<String>, val label: String, val time: Long)

@Serializable
class State(
    val chats: MutableMap<String, ChatState> = mutableMapOf(),
    var lastBackup: Long = 0,
    /** "Avisar quando a carga aparecer": requests waiting for a load. */
    val waiting: MutableList<Waiting> = mutableListOf(),
)

/** What to do when a load someone asked for shows up. */
enum class WaitMode(val label: String) { ASK("Me perguntar antes"), AUTO("Enviar sozinho"), NOTIFY("Só me avisar") }

/** Who the app answers, on top of each rule's own filter. */
@Serializable
enum class Audience { ALL, LIST, EXCEPT_LIST, EXCEPT_PHONE }

data class Incoming(
    val chat: String,
    val sender: String,
    val text: String,
    val isGroup: Boolean,
    val app: String = "WhatsApp",
    /** You wrote this inside someone else's chat (reply from the notification or a linked device). */
    val fromMe: Boolean = false,
)

data class Decision(
    val replies: List<String>,
    val rule: String,
    val delayMs: Long = 0,
    val formRow: List<String>? = null,
    val reason: String = "",
    /** Motorista, rota, carga, status: one row for the Leads tab. */
    val leadRow: List<String>? = null,
    /** Text of the phone alert for an available load. */
    val alert: String? = null,
    /** Every step of the decision, for the diagnostic and the AI review. */
    val trace: List<String> = emptyList(),
)

@Serializable
data class HistoryEntry(
    val time: Long, val app: String, val chat: String, val isGroup: Boolean,
    val text: String, val reply: String, val rule: String,
    /** Path of the decision, shown when you tap the reply in Histórico. */
    val trace: List<String> = emptyList(),
    /** Kind (Carga enviada, Indisponível, ...) and asked route, saved once so the screens don't recompute them. */
    val kind: String = "",
    val route: String = "",
)

/** One WhatsApp message the service saw, and what it decided: the "Registro" of the diagnostic. */
@Serializable
data class LogEntry(val time: Long, val chat: String, val text: String, val result: String, val trace: List<String> = emptyList())
