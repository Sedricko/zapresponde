package app.zapresponde

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/** Assistente de IA: transforma um pedido em português numa [Rule], que o usuário testa e aplica. */
object Assistant {
    private val json = Json { ignoreUnknownKeys = true }

    /** Mensagem de exemplo e se a regra deveria responder a ela. */
    data class Example(val text: String, val shouldReply: Boolean)

    /** Regra sugerida, exemplos para testar e problemas encontrados (vazio = pode aplicar). */
    data class Draft(val rule: Rule, val examples: List<Example>, val problems: List<String>)

    private val SCHEMA = """
        Responda só com JSON: {"rule": {...}, "testes": [{"mensagem": "...", "responde": true|false}]}.
        Campos de "rule" (omita os que ficam no padrão):
        - name: nome curto da regra
        - match: ALL (qualquer mensagem), CONTAINS (contém uma das palavras), EXACT, STARTS, ENDS, SIMILAR, WILDCARD (* e // separa padrões), REGEX
        - keywords: palavras ou frases separadas por vírgula (sem acento não importa; CONTAINS casa palavra inteira)
        - exclude: palavras separadas por vírgula que impedem a resposta
        - replies: lista de respostas; variáveis {primeiro_nome}, {nome}, {saudacao}
        - replyMode: SINGLE (a primeira), SEQUENCE (todas em sequência), RANDOM (uma sorteada)
        - who: ALL, SAVED (só contatos salvos), UNSAVED (só números não salvos), LIST, EXCEPT_LIST, NONE (só grupos); whoList: nomes, um por linha
        - groups: NONE (padrão, não responde grupos), ALL, LIST, EXCEPT_LIST; groupList: nomes, um por linha
        - freq: ALWAYS, ONCE (uma vez por conversa), INTERVAL (uma vez a cada intervalMin minutos), MAX (no máximo maxReplies respostas)
        - hours: faixas "HH:MM-HH:MM" separadas por vírgula, vazio = o dia todo; days: lista de 1 (segunda) a 7 (domingo)
        Não use consulta à planilha. Dê de 3 a 6 testes, com mensagens que devem e que não devem receber resposta.
    """.trimIndent()

    fun prompt(request: String, current: Rule?): String = buildString {
        append("Você configura regras de resposta automática do WhatsApp no app ZapResponde.\n")
        append(SCHEMA)
        if (current != null) {
            append("\n\nRegra atual (ajuste conforme o pedido e devolva a regra inteira):\n")
            append(json.encodeToString(Rule.serializer(), current))
        }
        append("\n\nPedido do usuário: ").append(request.trim())
    }

    /** Lê a resposta da IA e confere a regra. Lança erro legível se não for JSON de regra. */
    fun parse(text: String): Draft {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "A IA não devolveu uma regra. Tente escrever o pedido de outro jeito." }
        val o = runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }
            .getOrElse { throw IllegalArgumentException("A IA devolveu um texto que não deu para ler. Tente de novo.") }
        val raw = o["rule"] as? JsonObject ?: throw IllegalArgumentException("A IA não devolveu uma regra. Tente de novo.")
        val rule = runCatching { json.decodeFromJsonElement(Rule.serializer(), raw) }
            .getOrElse { throw IllegalArgumentException("A regra veio com um valor inválido (${it.message?.take(80)}). Tente de novo.") }
            .copy(id = java.util.UUID.randomUUID().toString(), enabled = true, kind = Kind.TEXT, fromSheet = false)
        val examples = (o["testes"] as? JsonArray).orEmpty().mapNotNull { e ->
            runCatching { Example(e.jsonObject["mensagem"]!!.jsonPrimitive.content, e.jsonObject["responde"]!!.jsonPrimitive.boolean) }.getOrNull()
        }
        return Draft(rule, examples, problems(rule))
    }

    fun problems(r: Rule): List<String> = buildList {
        if (r.replies.none { it.isNotBlank() }) add("A regra não tem resposta.")
        if (r.match != Match.ALL && r.keywords.isBlank()) add("A regra não tem palavras-chave.")
        if (r.match == Match.REGEX && runCatching { Regex(r.keywords) }.isFailure) add("A expressão regular é inválida.")
        if (r.days.isEmpty() || r.days.any { it !in 1..7 }) add("Os dias da semana estão inválidos.")
        if (r.hours.isNotBlank() && !Regex("""^\s*\d{1,2}(:\d{2})?\s*-\s*\d{1,2}(:\d{2})?\s*(,\s*\d{1,2}(:\d{2})?\s*-\s*\d{1,2}(:\d{2})?\s*)*$""").matches(r.hours))
            add("O horário \"${r.hours}\" está fora do formato 08:00-18:00.")
        if (r.freq == Freq.INTERVAL && r.intervalMin < 1) add("O intervalo precisa ser de pelo menos 1 minuto.")
        if (r.freq == Freq.MAX && r.maxReplies < 1) add("O máximo de respostas precisa ser pelo menos 1.")
    }

    /** Confere cada exemplo só pelas palavras (horário, contatos e frequência dependem da hora e de quem manda). */
    fun check(d: Draft): List<Pair<Example, Boolean>> = d.examples.map { it to (Engine.matches(d.rule, it.text) == it.shouldReply) }

    /** Chama o Gemini e devolve o texto da resposta. */
    fun gemini(key: String, model: String, prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent"
        val body = buildJsonObject {
            putJsonArray("contents") { addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", prompt) } } } }
            putJsonObject("generationConfig") { put("responseMimeType", "application/json"); put("temperature", 0.2) }
        }
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 60_000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", key.trim())
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        when {
            code == 400 && "API key" in text -> error("Chave do Gemini inválida. Confira em Ajustes > Assistente de IA.")
            code == 403 -> error("A chave do Gemini não tem permissão (403). Crie outra em aistudio.google.com/apikey.")
            code == 404 -> error("Modelo \"$model\" não encontrado. Use gemini-flash-latest.")
            code == 429 -> error("Limite de uso do Gemini atingido. Espere um pouco e tente de novo.")
            code >= 400 -> error("O Gemini respondeu com erro $code.")
        }
        val o = json.parseToJsonElement(text).jsonObject
        return o["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            ?.takeIf { it.isNotBlank() } ?: error("O Gemini não devolveu resposta. Tente escrever o pedido de outro jeito.")
    }
}
