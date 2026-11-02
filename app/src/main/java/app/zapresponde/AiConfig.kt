package app.zapresponde

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The AI assistant that changes the app: a request in Portuguese becomes a list of proposed changes (settings and the
 * app's own rules), each shown as before → after and applied only when the user taps Aplicar. Pure: tested on the JVM.
 */
object AiConfig {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Never changed by the AI: secrets, the sheet connection and the main on/off switch. */
    val FORBIDDEN = setOf("enabled", "aiKey", "aiModel", "sheetUrl", "sheetKey")

    /** What each setting is, in the words of the Ajustes screens (also the guide the AI reads). */
    val LABELS = linkedMapOf(
        "freteOn" to "Respostas de frete (anúncio do FreteBras e link)",
        "freteLivreOn" to "Pedido escrito de qualquer jeito",
        "freteBlock" to "Frete: não tratar como pedido se tiver (palavras separadas por vírgula)",
        "freteRepeatText" to "Frete: texto quando pedem a mesma carga de novo em até 1 h (vazio = não responde)",
        "freteNotFound" to "Frete: texto quando a carga não está na planilha",
        "altOn" to "Oferecer outra carga", "altOrigin" to "Outra carga: saindo da mesma cidade",
        "altDest" to "Outra carga: indo para a mesma cidade", "altState" to "Outra carga: saindo do mesmo estado",
        "altText" to "Outra carga: texto antes da carga ({pedida}, {motivo})",
        "waitOn" to "Avisar quando a carga aparecer", "waitMode" to "Avisar carga: ASK = me perguntar, AUTO = enviar sozinho, NOTIFY = só avisar",
        "waitHours" to "Avisar carga: esquecer o pedido depois de (horas, 1 a 72)",
        "waitReadMin" to "Avisar carga: ler a planilha a cada (min, 1 a 60)",
        "followOn" to "Link do grupo depois da conversa", "followMin" to "Link do grupo: esperar (min, 1 a 1440)",
        "followText" to "Link do grupo: mensagem ({link} = link do grupo)", "followLink" to "Link do grupo: o link",
        "followJoined" to "Link do grupo: frases de quem já está no grupo",
        "leadsOn" to "Gravar na aba Leads", "alertOn" to "Alerta de carga pedida",
        "welcomeOn" to "Boas-vindas", "welcomeText" to "Boas-vindas: texto", "welcomeDaily" to "Boas-vindas: uma vez por dia",
        "formOn" to "Pedido pela conversa", "formTrigger" to "Pedido: palavras que começam", "formQuestions" to "Pedido: perguntas (uma por linha)",
        "formDone" to "Pedido: texto ao terminar", "formCancel" to "Pedido: palavra para cancelar",
        "headerFooterOn" to "Cabeçalho e rodapé", "header" to "Cabeçalho", "footer" to "Rodapé",
        "contactsOn" to "Filtro de contatos (vale para o app todo)",
        "contactsMode" to "Contatos: ALL = todos, LIST = só a lista, EXCEPT_LIST = todos menos a lista, EXCEPT_PHONE = só quem não está salvo",
        "contactsList" to "Contatos: a lista (um nome por linha, como aparece no WhatsApp)", "groupsOn" to "Responder em grupos",
        "scheduleOn" to "Agenda", "scheduleHours" to "Agenda: horários (ex.: 08:00-18:00)", "scheduleDays" to "Agenda: dias (1 = segunda ... 7 = domingo)",
        "approvalOn" to "Aprovar antes de enviar", "approvalList" to "Aprovar: só estas conversas (vazio = todas)",
        "takeoverOn" to "Pausa quando você assume", "takeoverMin" to "Pausa: minutos",
        "takeoverOnUnanswered" to "Pausa: quando você ler o que o app não respondeu", "takeoverOnRead" to "Pausa: sempre que a conversa for lida",
        "delayOn" to "Atraso humano", "delayMinSec" to "Atraso: mínimo (s)", "delayMaxSec" to "Atraso: máximo (s)",
        "loopGuardOn" to "Proteção contra loop",
        "sheetOn" to "Usar a planilha", "sheetSyncMin" to "Planilha: sincronizar a cada (min, mínimo 15)",
        "sheetRulesOn" to "Planilha: usar as regras da aba Respostas", "sheetLogOn" to "Planilha: gravar o histórico",
        "backupAutoOn" to "Backup diário na planilha", "aiOn" to "Assistente de IA", "aiDiagOn" to "Diagnóstico: análise pela IA junto",
        "problemAlertOn" to "Avisar na hora se algo grave acontecer",
        "cleanAutoOn" to "Limpar dados sozinho todo dia", "cleanKeepHours" to "Limpar: manter (horas: 24, 168, 360 ou 720)",
    )

    /** One proposed change. [key] is a setting, or "+regra" / "-regra" for the app's rules. */
    data class Change(
        val key: String, val label: String, val before: String, val after: String,
        val value: JsonElement? = null, val rule: Rule? = null, val problems: List<String> = emptyList(),
    )

    /** What the AI proposed, plus anything it asked that could not be done and why. */
    data class Proposal(val explanation: String, val changes: List<Change>, val ignored: List<String>)

    fun prompt(request: String, cfg: Config, rules: List<Rule>, context: String = ""): String = buildString {
        append("""
            Você configura o app ZapResponde (respostas automáticas do WhatsApp de uma transportadora) a pedido do dono.
            Proponha só as mudanças necessárias para o pedido. Responda só com JSON:
            {"explicacao": "1 a 3 frases em português simples", "mudancas": [{"campo": "nome", "valor": novo valor}],
             "regras": [regra nova ou editada, inteira], "remover_regras": ["nome exato"]}
            "campo" é um dos ajustes abaixo; "valor" tem o mesmo tipo do valor atual (true/false, número, texto, lista).
            Para listas de texto (como a lista de contatos), devolva o texto inteiro já com a mudança.
            Uma regra com o mesmo "name" de uma existente a substitui; regra nova tem "name" novo.
        """.trimIndent())
        append("\n\nCampos de regra:\n").append(Assistant.RULE_FIELDS)
        append("\n\nAjustes que você pode mudar (campo: o que é):\n")
        LABELS.forEach { (k, v) -> append("- $k: $v\n") }
        append("\nValores atuais:\n").append(json.encodeToString(Config.serializer(), cfg).let { stripForbidden(it) })
        append("\n\nRegras do app, em ordem (a primeira que casar responde):\n")
        append(json.encodeToString(ListSerializer(Rule.serializer()), rules.filter { !it.fromSheet }))
        if (context.isNotBlank()) append("\n\nContexto (diagnóstico do app):\n").append(context.take(12_000))
        append("\n\nPedido do dono: ").append(request.trim())
    }

    private fun stripForbidden(cfgJson: String): String =
        JsonObject(json.parseToJsonElement(cfgJson).jsonObject.filterKeys { it !in FORBIDDEN }).toString()

    /** Reads the AI's answer and checks every change against the current settings; invalid ones go to [Proposal.ignored]. */
    fun parse(text: String, cfg: Config, rules: List<Rule>): Proposal {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "A IA não devolveu mudanças. Tente escrever o pedido de outro jeito." }
        val o = runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }
            .getOrElse { throw IllegalArgumentException("A IA devolveu um texto que não deu para ler. Tente de novo.") }
        val current = json.parseToJsonElement(json.encodeToString(Config.serializer(), cfg)).jsonObject
        val changes = mutableListOf<Change>(); val ignored = mutableListOf<String>()

        (o["mudancas"] as? JsonArray).orEmpty().forEach { e ->
            val m = e as? JsonObject ?: return@forEach
            val key = (m["campo"] as? JsonPrimitive)?.content.orEmpty()
            val value = m["valor"] ?: JsonNull
            when {
                key in FORBIDDEN -> ignored += "\"$key\" não pode ser mudado pela IA"
                key !in current -> ignored += "\"$key\" não existe"
                !sameKind(current.getValue(key), value) -> ignored += "\"${LABELS[key] ?: key}\" com valor de tipo errado"
                current.getValue(key) == value -> Unit // already like that
                runCatching { json.decodeFromJsonElement(Config.serializer(), JsonObject(current + (key to value))) }.isFailure ->
                    ignored += "\"${LABELS[key] ?: key}\" com valor inválido"
                else -> changes += Change(key, LABELS[key] ?: key, show(current.getValue(key)), show(value), value = value)
            }
        }
        (o["regras"] as? JsonArray).orEmpty().forEach { e ->
            val r = runCatching { json.decodeFromJsonElement(Rule.serializer(), e) }.getOrNull()
            if (r == null || r.name.isBlank()) { ignored += "uma regra veio incompleta"; return@forEach }
            val old = rules.firstOrNull { !it.fromSheet && it.name.equals(r.name, true) }
            val rule = r.copy(id = old?.id ?: java.util.UUID.randomUUID().toString(), kind = Kind.TEXT, fromSheet = false)
            changes += Change("+regra", if (old != null) "Editar a regra \"${r.name}\"" else "Nova regra \"${r.name}\"",
                old?.let(::describe) ?: "—", describe(rule), rule = rule, problems = Assistant.problems(rule))
        }
        (o["remover_regras"] as? JsonArray).orEmpty().forEach { e ->
            val name = (e as? JsonPrimitive)?.content.orEmpty()
            val old = rules.firstOrNull { !it.fromSheet && it.name.equals(name, true) }
            if (old == null) ignored += "não há regra do app chamada \"$name\""
            else changes += Change("-regra", "Apagar a regra \"${old.name}\"", describe(old), "—", rule = old)
        }
        val explanation = (o["explicacao"] as? JsonPrimitive)?.content.orEmpty()
        return Proposal(explanation, changes, ignored)
    }

    /** New settings and rules with the chosen changes; rules keep their order, new ones go to the end. */
    fun apply(cfg: Config, rules: List<Rule>, chosen: List<Change>): Pair<Config, List<Rule>> {
        var obj = json.parseToJsonElement(json.encodeToString(Config.serializer(), cfg)).jsonObject
        chosen.filter { it.value != null && it.key !in FORBIDDEN }.forEach { obj = JsonObject(obj + (it.key to it.value!!)) }
        var out = rules
        chosen.filter { it.key == "+regra" }.mapNotNull { it.rule }.forEach { r ->
            out = if (out.any { it.id == r.id }) out.map { if (it.id == r.id) r else it } else out + r
        }
        chosen.filter { it.key == "-regra" }.mapNotNull { it.rule }.forEach { r -> out = out.filter { it.id != r.id } }
        return json.decodeFromJsonElement(Config.serializer(), obj) to out
    }

    private fun sameKind(a: JsonElement, b: JsonElement) = when (a) {
        is JsonArray -> b is JsonArray
        is JsonObject -> b is JsonObject
        is JsonPrimitive -> b is JsonPrimitive && (a.isString == b.isString) && ((a.booleanOrNull != null) == (b.booleanOrNull != null))
        else -> false
    }

    private fun show(v: JsonElement): String = when {
        v is JsonPrimitive && v.booleanOrNull != null -> if (v.booleanOrNull == true) "ligado" else "desligado"
        v is JsonPrimitive -> v.content.ifEmpty { "(vazio)" }.let { if (it.length > 120) it.take(120) + "…" else it }
        v is JsonArray -> v.joinToString { (it as? JsonPrimitive)?.content ?: it.toString() }
        else -> v.toString()
    }

    private fun describe(r: Rule) = listOfNotNull(
        r.match.label + if (r.match != Match.ALL) ": ${r.keywords}" else "",
        r.replies.firstOrNull { it.isNotBlank() }?.let { "responde \"${it.take(60)}\"" },
        if (!r.enabled) "desligada" else null,
    ).joinToString(" · ")
}
