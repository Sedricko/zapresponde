package app.zapresponde

import kotlinx.serialization.builtins.ListSerializer
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

    /** Mensagem de teste e o nome da regra que deveria responder ("" = nenhuma). */
    data class RuleTest(val text: String, val expected: String)

    /** Pede à IA mensagens de teste para as regras [selected], sabendo de todas as [active] (a primeira que casa responde). Só as regras vão ao Gemini. */
    fun rulesTestPrompt(selected: List<Rule>, active: List<Rule>): String = buildString {
        append("""
            Você testa regras de resposta automática do WhatsApp no app ZapResponde.
            As regras ativas estão abaixo na ordem em que o app as confere: a PRIMEIRA que casar responde, as outras não.
            Comparações (sem acento e sem maiúscula): CONTAINS casa palavra inteira; EXACT a mensagem inteira; STARTS/ENDS começo/fim;
            SIMILAR parecida pelo percentual "similarity"; WILDCARD com * e // separando padrões; REGEX; ALL qualquer mensagem; "exclude" impede.
            Para cada regra a testar, crie de 3 a 5 mensagens reais de clientes que DEVEM ser respondidas por ela (com variações, erros comuns, acento ou não)
            e 1 ou 2 parecidas que NÃO devem ser respondidas por ela. Diga qual regra deveria responder cada mensagem, pelo nome exato, ou "" para nenhuma.
            Em "observacoes", aponte problemas que você vê só lendo as regras: palavras que pegam mensagens demais, regras que se atropelam pela ordem,
            regra que nunca vai responder, palavras faltando. Português simples, sem markdown.
            Responda só com JSON: {"testes": [{"mensagem": "...", "regra": "nome ou vazio"}], "observacoes": ["..."]}
        """.trimIndent())
        append("\n\nRegras a testar: ").append(selected.joinToString { "\"${it.name}\"" })
        append("\n\nTodas as regras ativas, em ordem:\n").append(json.encodeToString(ListSerializer(Rule.serializer()), active))
    }

    /** Lê os testes e as observações devolvidos pela IA. */
    fun parseRuleTests(text: String): Pair<List<RuleTest>, List<String>> {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        val o = runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }
            .getOrElse { throw IllegalArgumentException("A IA devolveu um texto que não deu para ler. Tente de novo.") }
        val tests = (o["testes"] as? JsonArray).orEmpty().mapNotNull { e ->
            runCatching { RuleTest(e.jsonObject["mensagem"]!!.jsonPrimitive.content, e.jsonObject["regra"]?.jsonPrimitive?.content.orEmpty()) }.getOrNull()
        }
        require(tests.isNotEmpty()) { "A IA não mandou testes. Tente de novo." }
        val notes = (o["observacoes"] as? JsonArray).orEmpty().mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
        return tests to notes
    }

    /** Nome da regra que de fato responde cada teste pelas palavras ("" = nenhuma); horário, contatos e frequência ficam de fora. */
    fun runRuleTests(tests: List<RuleTest>, active: List<Rule>): List<Pair<RuleTest, String>> =
        tests.map { t -> t to (active.firstOrNull { it.enabled && Engine.matches(it, t.text) }?.name.orEmpty()) }

    /** Diagnóstico pela IA: o relatório (com o caminho de cada decisão), as regras e os ajustes, sem chaves nem link da planilha. */
    fun diagnosisPrompt(report: String, cfg: Config, rules: List<Rule>, question: String): String = buildString {
        append("""
            Você analisa o app ZapResponde, que responde o WhatsApp sozinho lendo as notificações do Android.
            Ordem de decisão para cada mensagem: robô ligado; mensagem não é sua; filtro de contatos; agenda; conversa pausada;
            eco de resposta nossa; proteção contra loop; pedido pela conversa; Fretes FreteBras (frase "frete de X/UF para Y/UF" ou link);
            frete escrito de qualquer jeito (cidades da planilha de cargas: 1ª origem, 2ª destino); boas-vindas; regras (a primeira que servir).
            "Você" e "Meu" são contatos normais (outro número do dono). O caminho de cada decisão vem no relatório, linha a linha.
            Responda em português simples, para quem não é programador, em texto puro (sem markdown):
            1) para cada mensagem recente que pareça errada (respondeu o que não devia, não respondeu o que devia, respondeu várias vezes), diga o que aconteceu e por quê;
            2) se a decisão do app foi correta ou não;
            3) o que mudar, apontando o botão ou a regra exata em Ajustes. Se parecer defeito do app, diga "possível defeito do app" e descreva.
            Não invente: use só o que está abaixo.
        """.trimIndent())
        if (question.isNotBlank()) append("\n\nPergunta do dono: ").append(question.trim())
        append("\n\nAjustes:\n").append(json.encodeToString(Config.serializer(), cfg.copy(aiKey = "", sheetUrl = "", sheetKey = "")))
        append("\n\nRegras:\n").append(json.encodeToString(ListSerializer(Rule.serializer()), rules))
        append("\n\nRelatório:\n").append(report)
    }

    /** Modelos tentados depois do escolhido, quando ele está sobrecarregado, sem cota ou não existe. */
    private val FALLBACK = listOf("gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite")

    private class Retry(msg: String) : Exception(msg)

    /** Chama o Gemini e devolve o texto da resposta, tentando de novo e trocando de modelo quando o Google está ocupado. */
    fun gemini(key: String, model: String, prompt: String, jsonOut: Boolean = true): String {
        var last = "O Gemini não respondeu."
        for (m in (listOf(model.trim()) + FALLBACK).filter { it.isNotEmpty() }.distinct()) {
            repeat(2) { attempt ->
                try {
                    return call(key, m, prompt, jsonOut)
                } catch (e: Retry) {
                    last = e.message.orEmpty()
                    if (attempt == 0 && "sobrecarregado" in last) Thread.sleep(2_000) else return@repeat
                }
            }
        }
        error(last)
    }

    private fun call(key: String, model: String, prompt: String, jsonOut: Boolean): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val body = buildJsonObject {
            putJsonArray("contents") { addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", prompt) } } } }
            putJsonObject("generationConfig") { if (jsonOut) put("responseMimeType", "application/json"); put("temperature", 0.2) }
        }
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 120_000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", key.trim())
        }
        val code = try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            c.responseCode
        } catch (e: java.net.SocketTimeoutException) {
            throw Retry("O Gemini demorou demais para responder. Tente de novo em alguns minutos.")
        }
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        when {
            code == 400 && "API key" in text -> error("Chave do Gemini inválida. Confira em Ajustes > Assistente de IA.")
            code == 403 -> error("A chave do Gemini não tem permissão (403). Crie outra em aistudio.google.com/apikey.")
            code == 404 -> throw Retry("Nenhum modelo do Gemini foi encontrado para essa chave.")
            code == 429 -> throw Retry("Limite de uso grátis do Gemini atingido. Espere alguns minutos e tente de novo.")
            code >= 500 -> throw Retry("O Gemini está sobrecarregado agora (erro $code). Tente de novo em alguns minutos.")
            code >= 400 -> error("O Gemini respondeu com erro $code.")
        }
        val o = json.parseToJsonElement(text).jsonObject
        return o["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            ?.takeIf { it.isNotBlank() } ?: error("O Gemini não devolveu resposta. Tente escrever o pedido de outro jeito.")
    }
}
