package app.zapresponde

/**
 * Something wrong the diagnostic points out: [level] 0 = grave (❌), 1 = atenção (⚠️), 2 = informação (ℹ️).
 * [fix] is what the [Corrigir] button does: "exclude:<chat>" puts the chat in the contact exceptions, "open:<screen>" opens a screen.
 */
data class Problem(val level: Int, val title: String, val detail: String, val fix: String? = null) {
    val icon get() = when (level) { 0 -> "❌"; 1 -> "⚠️"; else -> "ℹ️" }
}

/** Pure checks over the app's own records (no Android), so they run in tests, in the diagnostic and in the automatic alert. */
object Problems {
    private const val MIN = 60_000L
    private val COMPLAINT = listOf("droga", "errado", "errada", "nao e isso", "nao entendi", "nao pedi", "que isso", "para de", "pare de", "chato", "spam", "robo")
    private val BOT_NAME = listOf("publicac", "noreply", "no-reply", "notificac", "sistema", "bot", "automatic")

    /** Kind of a sent reply, for the colored label, the counters and the problems. */
    fun replyKind(rule: String, reply: String) = when {
        rule == "Acompanhamento" -> "Link do grupo"
        rule == "Fretes (outra carga)" -> "Outra carga"
        rule.startsWith("Fretes") || rule == "Carga que apareceu" ->
            if (Engine.closed(reply) || reply.contains("INDISPON")) "Indisponível" else "Carga enviada"
        else -> "Regra"
    }

    private fun excluded(cfg: Config, chat: String) =
        cfg.contactsOn && cfg.contactsMode == Audience.EXCEPT_LIST && cfg.contactsList.lines().any { Engine.normalize(it) == Engine.normalize(chat) }

    fun find(
        cfg: Config, events: List<LogEntry>, history: List<HistoryEntry>, state: State, sheet: List<List<String>>,
        sheetError: String, sheetSyncedAt: Long, approvalsPending: Int, now: Long,
    ): List<Problem> {
        val out = mutableListOf<Problem>()
        val day = events.filter { now - it.time < 24 * 60 * MIN && it.chat != "(sistema)" }
        val replied = day.filter { it.result.startsWith("Respondido") || it.result.startsWith("📨") }

        // Many replies to one chat in a few minutes: almost always another robot or a notice channel.
        replied.groupBy { it.chat }.forEach { (chat, l) ->
            val times = l.map { it.time }.sorted()
            val burst = times.indices.any { i -> times.count { it in times[i]..(times[i] + 10 * MIN) } >= 3 }
            if (!burst || excluded(cfg, chat)) return@forEach
            // A robot repeats itself; a person (or you testing) writes different things. Only the first is grave.
            val texts = day.filter { it.chat == chat }.map { Engine.normalize(it.text) }.filter { it.isNotEmpty() }
            val robotish = texts.size - texts.distinct().size >= 2 || BOT_NAME.any { Engine.normalize(chat).contains(it) }
            out += if (robotish) Problem(
                0, "$chat recebeu ${l.size} respostas em pouco tempo e repete as mesmas mensagens",
                "Mensagens iguais repetidas e muitas respostas costumam ser outro robô ou um canal de avisos. Colocar nas exceções faz o app parar de responder ali.",
                "exclude:$chat",
            ) else Problem(
                1, "$chat recebeu ${l.size} respostas em pouco tempo",
                "Se foi um teste seu ou uma conversa longa, está tudo bem. Se for um robô, coloque nas exceções.", "exclude:$chat",
            )
        }
        day.filter { it.result.startsWith("Proteção contra loop") }.map { it.chat }.distinct().forEach { chat ->
            if (!excluded(cfg, chat)) out += Problem(0, "Loop com $chat", "A proteção contra loop pausou essa conversa: dois robôs estavam respondendo um ao outro.", "exclude:$chat")
        }
        replied.map { it.chat }.distinct().filter { c -> BOT_NAME.any { Engine.normalize(c).contains(it) } && !excluded(cfg, c) }.forEach { chat ->
            if (out.none { it.fix == "exclude:$chat" }) out += Problem(1, "$chat parece uma conta automática", "O nome sugere um canal de avisos, e o app respondeu essa conversa.", "exclude:$chat")
        }

        // A complaint right after our reply: the reply was probably wrong.
        day.forEach { e ->
            val t = Engine.normalize(e.text)
            if (!COMPLAINT.any { Engine.hasWord(t, it) } && t != "?") return@forEach
            val last = history.firstOrNull { it.chat == e.chat && it.time <= e.time && e.time - it.time < 15 * MIN } ?: return@forEach
            out += Problem(
                1, "${e.chat} respondeu \"${e.text.take(40)}\" logo depois do app",
                "Resposta possivelmente errada (${last.rule}): \"${last.reply.take(80).replace('\n', ' ')}\"" +
                    if (last.trace.isNotEmpty()) "\nCaminho da decisão:\n" + last.trace.joinToString("\n") { "   $it" } else "",
            )
        }

        // Looks like a freight request but nothing answered it.
        val cityWords = Regex("""\b(para|pra|carga|cargas|frete|fretes)\b""")
        // Only messages naming a city of the sheet: "Descarga é para que dia?" is a question about a load, not a request.
        day.filter {
            it.result.startsWith("Nenhuma regra") && cityWords.containsMatchIn(Engine.normalize(it.text)) &&
                !Regex("origem:|lista de cargas").containsMatchIn(Engine.normalize(it.text)) && Engine.mentionsSheetCity(it.text, sheet)
        }.take(5).forEach { e ->
            val why = e.trace.lastOrNull { "Frete" in it || "frete" in it }?.trim().orEmpty()
            out += Problem(
                1, "Possível pedido de frete sem resposta: \"${e.text.take(50)}\" (${e.chat})",
                why.ifEmpty { "Nenhuma função de frete reconheceu a mensagem." } +
                    if (!cfg.freteLivreOn) "\n\"Pedido escrito de qualquer jeito\" está desligado." else "",
                if (!cfg.freteLivreOn || !cfg.freteOn) "open:frete" else null,
            )
        }

        // The app's own errors: a crash is grave, an error it survived is worth a look when it repeats.
        day.filter { it.chat == "(erro interno)" }.groupBy { it.text }.forEach { (where, l) ->
            val crash = where == "O app fechou por erro"
            out += Problem(
                if (crash) 0 else 1, if (crash) "O app fechou por erro ${l.size} vez(es)" else "Erro interno em $where: ${l.size} vez(es)",
                "Último: ${l.first().result}" + if (l.first().trace.isNotEmpty()) "\n" + l.first().trace.take(3).joinToString("\n") else "",
            )
        }

        // Things that did not go out.
        listOf(
            "Acompanhamento não enviado" to "Links do grupo não enviados",
            "Falhou ao enviar" to "Respostas que o WhatsApp recusou",
            "Planilha não atualizou" to "Leituras da planilha que falharam",
        ).forEach { (key, title) ->
            val l = day.filter { it.result.startsWith(key) }
            if (l.isNotEmpty()) out += Problem(1, "$title: ${l.size}", l.take(3).joinToString("\n") { "${it.chat}: ${it.result}" })
        }
        val noButton = day.count { it.result.startsWith("Sem botão Responder") }
        if (noButton >= 3) out += Problem(1, "$noButton mensagens chegaram sem o botão Responder",
            "O app vê a mensagem mas não consegue responder. Costuma ser conversa silenciada ou notificação resumida do WhatsApp.")

        // Settings that contradict each other.
        if (!cfg.freteOn && (cfg.freteLivreOn || cfg.altOn || cfg.waitOn))
            out += Problem(1, "Funções de frete ligadas com Respostas de frete desligado", "Pedido escrito, outra carga e avisar carga só funcionam com Respostas de frete ligado.", "open:frete")
        if (cfg.freteOn && (!cfg.sheetOn || cfg.sheetUrl.isBlank()))
            out += Problem(0, "Respostas de frete sem planilha conectada", "Sem a planilha de cargas o app não tem o que responder.", "open:frete")
        if (cfg.followOn && cfg.followLink.isBlank()) out += Problem(1, "Link do grupo ligado sem link", "Nada será enviado até você colar o link do grupo.", "open:follow")
        if ((cfg.aiDiagOn || cfg.aiOn) && cfg.aiKey.isBlank()) out += Problem(2, "IA ligada sem chave do Gemini", "Cole a chave em Assistente de IA.", "open:ai")
        if (!cfg.takeoverOn) out += Problem(
            1, "Pausa quando você assume está desligada",
            "O app continua respondendo mesmo quando você está conversando, inclusive pelo WhatsApp Web.", "open:takeover",
        )
        if (approvalsPending > 0) out += Problem(1, "$approvalsPending resposta(s) esperando você tocar em Enviar", "Veja as notificações do ZapResponde.")

        // The freight sheet.
        if (cfg.sheetOn) {
            if (sheetError.isNotEmpty()) out += Problem(1, "Erro na sincronização da planilha", sheetError)
            if (sheetSyncedAt > 0 && now - sheetSyncedAt > 2 * 60 * MIN) out += Problem(1, "Planilha sem atualizar há ${(now - sheetSyncedAt) / (60 * MIN)} h", "Toque em Sincronizar na tela Início ou confira a internet.")
            if (cfg.freteOn) {
                val routes = sheet.filter { Regex("(?i)^[^/]+/\\w\\w para [^/]+/\\w\\w").containsMatchIn(it.firstOrNull().orEmpty()) }
                val active = routes.filter { r -> !Engine.closed(r.drop(1).maxByOrNull { it.length }.orEmpty()) }
                if (routes.isNotEmpty() && active.isEmpty()) out += Problem(1, "Nenhuma carga ativa na planilha", "Todas as rotas estão como FRETE INDISPONÍVEL: todo pedido recebe indisponível.")
                val undated = active.count { Engine.loadDate(it, java.time.LocalDateTime.now()) == null }
                if (undated > 0) out += Problem(2, "$undated carga(s) ativa(s) sem data de coleta", "Elas vão por último quando há mais de uma carga na rota (\"A combinar\").")
            }
        }
        state.waiting.filter { now - it.time > cfg.waitHours * 30 * MIN }.take(3).forEach {
            out += Problem(2, "${it.chat} espera ${it.label} há ${(now - it.time) / (60 * MIN)} h", "O pedido é esquecido em ${cfg.waitHours} h se a carga não aparecer.")
        }
        return out.sortedBy { it.level }
    }

    /**
     * "Funcionamento interno": the app's own errors (7 days), conflicts between rules and between settings. Always shown,
     * with ✅ lines when a part is fine, so an empty section never hides that it was checked.
     */
    fun internal(cfg: Config, rules: List<Rule>, events: List<LogEntry>, history: List<HistoryEntry>, now: Long): List<String> {
        val out = mutableListOf<String>()
        val week = events.filter { it.chat == "(erro interno)" && now - it.time < 7 * 24 * 60 * MIN }

        out += "Erros internos (7 dias)"
        val crashes = week.filter { it.text == "O app fechou por erro" }
        if (week.isEmpty()) out += "  ✅ Nenhum erro interno e nenhum travamento"
        if (crashes.isNotEmpty()) out += "  ❌ O app fechou por erro ${crashes.size} vez(es); último: ${crashes.first().result}"
        week.filter { it.text != "O app fechou por erro" }.groupBy { it.text }.forEach { (where, l) ->
            out += "  ⚠️ $where: ${l.size} erro(s); último: ${l.first().result}"
        }

        out += "Conflitos entre regras"
        val active = rules.filter { it.enabled }
        val ruleIssues = mutableListOf<String>()
        active.forEach { r -> Assistant.problems(r).forEach { ruleIssues += "  ❌ ${r.name}: $it" } }
        // A rule that takes every message, at any time, for anyone, hides every rule under it.
        active.forEachIndexed { i, r ->
            val catchAll = r.match == Match.ALL && r.exclude.isBlank() && r.hours.isBlank() && r.days.size == 7 && r.who == Who.ALL && r.freq == Freq.ALWAYS
            if (catchAll) active.drop(i + 1).forEach { below -> ruleIssues += "  ❌ ${below.name} nunca responde: \"${r.name}\" (qualquer mensagem) está acima dela" }
        }
        // The same keyword in two rules: only the first one answers it.
        val seen = mutableMapOf<String, String>()
        active.filter { it.match == Match.CONTAINS || it.match == Match.EXACT }.forEach { r ->
            Engine.splitList(r.keywords).map { Engine.normalize(it) }.distinct().forEach { k ->
                val first = seen.putIfAbsent(k, r.name)
                if (first != null && first != r.name) ruleIssues += "  ⚠️ \"$k\" está em \"$first\" e em \"${r.name}\": só \"$first\" responde"
            }
        }
        val fired = history.filter { now - it.time < 7 * 24 * 60 * MIN }.map { it.rule }
        active.filter { r -> fired.none { it == r.name || it.contains(r.name) } }.forEach { ruleIssues += "  ℹ️ ${it.name} não respondeu nada nos últimos 7 dias" }
        out += ruleIssues.ifEmpty { listOf("  ✅ Nenhum conflito entre as ${active.size} regras ativas") }

        out += "Conflitos de configuração"
        val cfgIssues = mutableListOf<String>()
        if (cfg.contactsOn && cfg.contactsMode == Audience.LIST && cfg.contactsList.isBlank())
            cfgIssues += "  ❌ Contatos em \"Só a minha lista\" com a lista vazia: o app não responde ninguém"
        if (cfg.scheduleOn && (cfg.scheduleDays.isEmpty() || !Regex("""^\s*\d{1,2}(:\d{2})?\s*-\s*\d{1,2}(:\d{2})?\s*(,\s*\d{1,2}(:\d{2})?\s*-\s*\d{1,2}(:\d{2})?\s*)*$""").matches(cfg.scheduleHours)))
            cfgIssues += "  ❌ Agenda ligada com horário \"${cfg.scheduleHours}\" ou dias inválidos: pode nunca abrir"
        if (cfg.contactsOn && !cfg.groupsOn && active.any { it.groups != Groups.NONE })
            cfgIssues += "  ⚠️ Há regras para grupos, mas os grupos estão desligados em Contatos"
        if (cfg.approvalOn && cfg.waitOn && cfg.waitMode == WaitMode.AUTO)
            cfgIssues += "  ℹ️ \"Avisar quando a carga aparecer\" está em Enviar sozinho, mas \"Aprovar antes de enviar\" está ligado (as respostas comuns pedem aprovação; a carga que apareceu não)"
        if (cfg.followOn && cfg.followMin > 360) cfgIssues += "  ⚠️ Link do grupo depois de ${cfg.followMin} min: tempos longos se perdem se o celular reiniciar"
        if (cfg.headerFooterOn && cfg.header.isBlank() && cfg.footer.isBlank()) cfgIssues += "  ⚠️ Cabeçalho e rodapé ligado, mas os dois estão vazios"
        if (cfg.formOn && cfg.formQuestions.isBlank()) cfgIssues += "  ⚠️ Pedido pela conversa ligado sem perguntas"
        if (cfg.delayOn && maxOf(cfg.delayMinSec, cfg.delayMaxSec) > 120) cfgIssues += "  ⚠️ Atraso humano de até ${maxOf(cfg.delayMinSec, cfg.delayMaxSec)} s: uma mensagem nova nesse meio-tempo cancela a resposta"
        if (cfg.sheetOn && cfg.sheetUrl.isBlank()) cfgIssues += "  ❌ Planilha ligada sem link"
        out += cfgIssues.ifEmpty { listOf("  ✅ Nenhum conflito de configuração") }
        return out
    }

    /** Last 24 h in numbers: received, answered, ignored by reason, and the kinds of replies. */
    fun summary(events: List<LogEntry>, history: List<HistoryEntry>, now: Long): List<String> {
        val day = events.filter { now - it.time < 24 * 60 * MIN && it.chat != "(sistema)" && it.chat != "(erro interno)" && it.text.isNotEmpty() }
        val answered = day.count { it.result.startsWith("Respondido") }
        val ignored = day.filter { !it.result.startsWith("Respondido") && !it.result.startsWith("Aguardando") }
        val kinds = history.filter { now - it.time < 24 * 60 * MIN }.groupingBy { it.kind.ifEmpty { replyKind(it.rule, it.reply) } }.eachCount()
        return listOf(
            "ℹ️ Mensagens recebidas: ${day.size} · respondidas: $answered · sem resposta: ${ignored.size}",
        ) + ignored.groupingBy { it.result.substringBefore(" · ").substringBefore(" (").take(50) }.eachCount()
            .entries.sortedByDescending { it.value }.take(5).map { "      ${it.value}× ${it.key}" } +
            (if (kinds.isEmpty()) emptyList() else listOf("ℹ️ Respostas: " + kinds.entries.joinToString { "${it.key} ${it.value}" }))
    }
}
