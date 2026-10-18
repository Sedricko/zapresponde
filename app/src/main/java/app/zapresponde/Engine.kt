package app.zapresponde

import java.text.Normalizer
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** Pure decision logic: no Android calls, so it runs in unit tests and in the "Testar" screen. */
object Engine {
    private const val LOOP_WINDOW = 120_000L
    private const val LOOP_MAX = 5
    private const val LOOKUP_MAX = 5
    private const val LOOP_PAUSE = 30 * 60_000L

    fun decide(
        msg: Incoming,
        now: LocalDateTime,
        nowMs: Long,
        cfg: Config,
        rules: List<Rule>,
        consulta: List<List<String>>,
        state: State,
        random: Random = Random.Default,
        phoneNames: Set<String> = emptySet(),
    ): Decision {
        val t = mutableListOf<String>()
        return decide(msg, now, nowMs, cfg, rules, consulta, state, random, phoneNames, t).copy(trace = t)
    }

    /** [t] collects each step taken, in order: the "caminho da decisão" shown by the diagnostic and sent to the AI. */
    private fun decide(
        msg: Incoming, now: LocalDateTime, nowMs: Long, cfg: Config, rules: List<Rule>, consulta: List<List<String>>,
        state: State, random: Random, phoneNames: Set<String>, t: MutableList<String>,
    ): Decision {
        if (!cfg.enabled) return skip("Respostas automáticas desligadas")
        t += "✅ Respostas automáticas ligadas"
        // Answering this would send the bot's reply to the contact because *you* wrote; ReplyService treats it as takeover.
        if (msg.fromMe) {
            // You wrote in the chat: you took over, so a pending Acompanhamento is cancelled (our own reply echoed back is not you).
            state.chats[msg.chat]?.takeIf { msg.text !in it.lastTexts && it.followAt > 0 }?.followAt = 0
            return skip("Mensagem enviada por você na conversa")
        }
        t += "✅ Mensagem de outra pessoa (${if (msg.isGroup) "grupo ${msg.chat}" else msg.sender})"
        if (cfg.contactsOn) audience(msg, cfg, phoneNames)?.let { return skip(it) }
        t += if (cfg.contactsOn) "✅ Contato permitido pelo filtro de contatos" else "ℹ️ Filtro de contatos desligado"
        if (cfg.scheduleOn && !inWindow(cfg.scheduleHours, cfg.scheduleDays, now)) return skip("Fora da agenda")
        t += if (cfg.scheduleOn) "✅ Dentro da agenda (${cfg.scheduleHours})" else "ℹ️ Agenda desligada"
        val cs = state.chats.getOrPut(msg.chat) { ChatState() }
        if (cfg.followOn && !msg.isGroup && !cs.followDone) {
            val t0 = normalize(msg.text)
            if (splitList(cfg.followJoined).any { hasWord(t0, normalize(it)) }) {
                cs.followDone = true; cs.followAt = 0
                t += "✅ Acompanhamento: o contato disse que já está no grupo; não vai receber o link"
            } else if (cs.followAt > 0) {
                cs.followAt = nowMs + cfg.followMin * 60_000L
                t += "ℹ️ Acompanhamento: nova mensagem, a contagem de ${cfg.followMin} min recomeçou"
            }
        }
        if (cs.pauseUntil > nowMs) { cs.followAt = 0; return skip("Conversa pausada") }
        // A message equal to something we just sent is our own reply echoed back, in this chat or another WhatsApp on the phone.
        if (state.chats.values.any { c -> c.lastTexts.any { it.trim() == msg.text.trim() } }) return skip("Mensagem é uma resposta nossa")
        t += "✅ Conversa não pausada e mensagem não é eco de resposta nossa"
        if (cfg.loopGuardOn) {
            cs.recentReplies.removeAll { nowMs - it > LOOP_WINDOW }
            if (cs.recentReplies.size >= LOOP_MAX) {
                cs.pauseUntil = nowMs + LOOP_PAUSE
                cs.recentReplies.clear()
                return skip("Proteção contra loop: conversa pausada por 30 min")
            }
            t += "✅ Proteção contra loop: ${cs.recentReplies.size} de $LOOP_MAX respostas nos últimos 2 min"
        }

        val out = mutableListOf<String>()
        var ruleName = ""
        var formRow: List<String>? = null
        val norm = normalize(msg.text)

        val questions = cfg.formQuestions.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (cfg.formOn && questions.isNotEmpty()) {
            if (cs.formStep >= 0) {
                if (norm == normalize(cfg.formCancel)) {
                    cs.formStep = -1; cs.formAnswers.clear()
                    return finish(msg, now, cfg, cs, nowMs, listOf("Pedido cancelado."), "Pedido", null, random)
                }
                cs.formAnswers.add(msg.text)
                cs.formStep++
                if (cs.formStep < questions.size) {
                    return finish(msg, now, cfg, cs, nowMs, listOf(questions[cs.formStep]), "Pedido", null, random)
                }
                formRow = listOf(msg.chat) + cs.formAnswers
                cs.formStep = -1; cs.formAnswers.clear()
                return finish(msg, now, cfg, cs, nowMs, listOf(cfg.formDone), "Pedido", formRow, random)
            }
            if (splitList(cfg.formTrigger).any { norm.contains(normalize(it)) }) {
                cs.formStep = 0
                t += "✅ Pedido pela conversa: palavra de início encontrada"
                return finish(msg, now, cfg, cs, nowMs, listOf(questions[0]), "Pedido", null, random)
            }
            t += "ℹ️ Pedido pela conversa: nenhuma palavra de início (${cfg.formTrigger})"
        }

        // Before the welcome: a FreteBras driver is a new contact, and should get only the freight answer.
        if (!cfg.freteOn) t += "ℹ️ Fretes desligado"
        if (cfg.freteOn) freteRoute(msg.text)?.let { route ->
            val found = freteMessage(route, consulta, now)
            val open = found != null && !closed(found)
            t += "✅ Anúncio FreteBras, rota ${route.label}: " +
                if (open) "carga achada na planilha" else if (found != null) "carga encerrada (FRETE INDISPONÍVEL)" else "rota fora da planilha"
            val reply = found ?: cfg.freteNotFound.ifBlank { null } ?: return skip("Frete fora da planilha")
            return finish(msg, now, cfg, cs, nowMs, listOf(reply), "Fretes", null, random).copy(
                leadRow = listOf(msg.sender, route.label, route.cargo.uppercase(), if (open) "disponível" else "indisponível"),
                alert = if (open) "${msg.sender} quer a carga ${route.label}" else null,
            )
        }
        if (cfg.freteOn) t += "ℹ️ Não é anúncio FreteBras"
        if (cfg.freteOn && !cfg.freteLivreOn) t += "ℹ️ Frete escrito de qualquer jeito desligado"
        if (cfg.freteOn && cfg.freteLivreOn) freteLivre(msg.text, consulta, now).also { f ->
            t += if (f == null) "ℹ️ Frete escrito: não é pedido de frete (nenhuma cidade da planilha, anúncio de carga, ou uma cidade só sem \"para\"/\"carga\")"
            else "✅ Frete escrito: entendi ${f.label}, ${f.messages.count { !closed(it) }} carga(s) ativa(s) e ${f.messages.count(::closed)} encerrada(s) na planilha" +
                if (f.messages.size > 1) "; mandei só a de coleta mais próxima" else ""
        }?.let { f ->
            val reply = f.messages.firstOrNull() ?: cfg.freteNotFound.ifBlank { null } ?: return skip("Frete fora da planilha (${f.label})")
            val ok = f.messages.firstOrNull()?.let { !closed(it) } == true
            return finish(msg, now, cfg, cs, nowMs, listOf(reply), "Fretes (texto livre)", null, random).copy(
                leadRow = listOf(msg.sender, f.label, "", if (ok) "disponível" else "indisponível"),
                alert = if (ok) "${msg.sender} quer a carga ${f.label}" else null,
            )
        }

        if (cfg.welcomeOn) {
            val today = now.toLocalDate().toString()
            val due = if (cfg.welcomeDaily) cs.welcomedDay != today else cs.welcomedDay.isEmpty()
            if (due) {
                cs.welcomedDay = today
                out += cfg.welcomeText
                ruleName = "Boas-vindas"
            }
            t += if (due) "✅ Boas-vindas: primeiro contato${if (cfg.welcomeDaily) " do dia" else ""}" else "ℹ️ Boas-vindas: já enviada antes"
        }

        val why = mutableListOf<String>()
        if (rules.none { it.enabled }) t += "ℹ️ Nenhuma regra ativa"
        for (r in rules) {
            if (!r.enabled) continue
            if (!matches(r, msg.text)) {
                t += "❌ Regra ${r.name}: não casou (${r.match.label}${if (r.match != Match.ALL) ": ${r.keywords}" else ""}${if (r.exclude.isNotBlank()) "; exceto: ${r.exclude}" else ""})"
                continue
            }
            if (!allowed(r, msg)) { why += "${r.name}: contato ou grupo não permitido"; t += "❌ Regra " + why.last(); continue }
            if (!inWindow(r.hours, r.days, now)) { why += "${r.name}: fora do horário da regra"; t += "❌ Regra " + why.last(); continue }
            val st = cs.rules.getOrPut(r.id) { longArrayOf(0, 0) }
            val ok = when (r.freq) {
                Freq.ALWAYS -> true
                Freq.ONCE -> st[0] == 0L
                Freq.INTERVAL -> st[0] == 0L || nowMs - st[1] >= r.intervalMin * 60_000L
                Freq.MAX -> st[0] < r.maxReplies
            }
            if (!ok) { why += "${r.name}: limite de frequência (${r.freq.label})"; t += "❌ Regra " + why.last(); continue }
            val replies = when (r.kind) {
                Kind.TEXT -> pick(r, random)
                Kind.LOOKUP -> listOfNotNull(lookup(r, msg.text, consulta))
            }.filter { it.isNotBlank() }
            if (replies.isEmpty()) { why += "${r.name}: consulta sem resultado"; t += "❌ Regra " + why.last(); continue }
            t += "✅ Regra ${r.name}: casou (${r.match.label}${if (r.match != Match.ALL) ": ${r.keywords}" else ""}) e respondeu"
            st[0]++; st[1] = nowMs
            out += replies
            ruleName = if (ruleName.isEmpty()) r.name else "$ruleName + ${r.name}"
            break
        }
        if (out.isEmpty()) return skip(
            if (why.isEmpty()) "Nenhuma regra serviu" else "Nenhuma regra respondeu · " + why.joinToString("; "),
        )
        return finish(msg, now, cfg, cs, nowMs, out, ruleName, formRow, random)
    }

    /** Chats whose Acompanhamento is due. */
    fun followDue(state: State, nowMs: Long): List<String> = state.chats.filter { it.value.followAt in 1..nowMs }.keys.toList()

    /** Acompanhamento text: {link} is the group link, plus the usual variables. */
    fun followText(cfg: Config, msg: Incoming, now: LocalDateTime, count: Int) =
        fill(cfg.followText.replace("{link}", cfg.followLink.trim()), msg, now, count)

    /** Reason to stay silent under the "Contatos" setting, or null to go on. [phoneNames] are normalized. */
    fun audience(msg: Incoming, cfg: Config, phoneNames: Set<String>): String? {
        if (msg.isGroup) return if (cfg.groupsOn) null else "Grupos desativados"
        val name = normalize(msg.sender)
        val inList = cfg.contactsList.lines().any { normalize(it).let { n -> n.isNotEmpty() && (n == name || n == normalize(msg.chat)) } }
        // Without contacts permission, a sender shown as a name (not a number) counts as saved.
        val saved = if (phoneNames.isEmpty()) msg.sender.any { it.isLetter() } else name in phoneNames
        return when (cfg.contactsMode) {
            Audience.ALL -> null
            Audience.LIST -> if (inList) null else "Fora da lista de contatos"
            Audience.EXCEPT_LIST -> if (inList) "Na lista de exceções" else null
            Audience.EXCEPT_PHONE -> if (saved) "Contato salvo no telefone" else null
        }
    }

    private fun skip(reason: String) = Decision(emptyList(), "", reason = reason)

    private fun finish(
        msg: Incoming, now: LocalDateTime, cfg: Config, cs: ChatState, nowMs: Long,
        raw: List<String>, rule: String, formRow: List<String>?, random: Random,
    ): Decision {
        cs.replyCount++
        var replies = raw.map { fill(it, msg, now, cs.replyCount) }
        if (cfg.headerFooterOn) {
            replies = replies.toMutableList().also { l ->
                if (cfg.header.isNotBlank()) l[0] = fill(cfg.header, msg, now, cs.replyCount) + "\n" + l[0]
                if (cfg.footer.isNotBlank()) l[l.lastIndex] = l.last() + "\n" + fill(cfg.footer, msg, now, cs.replyCount)
            }
        }
        cs.lastBotReply = nowMs
        // Counted from the last message of the conversation: this reply; a new message from the contact restarts it.
        if (cfg.followOn && !msg.isGroup && !cs.followDone) cs.followAt = nowMs + cfg.followMin * 60_000L
        cs.recentReplies.add(nowMs)
        cs.lastTexts.addAll(replies)
        while (cs.lastTexts.size > 10) cs.lastTexts.removeAt(0)
        val delay = if (cfg.delayOn) {
            val lo = minOf(cfg.delayMinSec, cfg.delayMaxSec); val hi = maxOf(cfg.delayMinSec, cfg.delayMaxSec)
            random.nextLong(lo * 1000L, hi * 1000L + 1)
        } else 0L
        return Decision(replies, rule, delay, formRow, "Respondido")
    }

    fun allowed(r: Rule, msg: Incoming): Boolean {
        val name = normalize(if (msg.isGroup) msg.chat else msg.sender)
        fun inList(list: String) = splitList(list).any { normalize(it) == name }
        if (msg.isGroup) return when (r.groups) {
            Groups.NONE -> false
            Groups.ALL -> true
            Groups.LIST -> inList(r.groupList)
            Groups.EXCEPT_LIST -> !inList(r.groupList)
        }
        // shortcut: "saved contact" = notification shows a name, not a number; read the address book if this proves wrong
        val saved = msg.sender.any { it.isLetter() }
        return when (r.who) {
            Who.ALL -> true
            Who.SAVED -> saved
            Who.UNSAVED -> !saved
            Who.LIST -> inList(r.whoList)
            Who.EXCEPT_LIST -> !inList(r.whoList)
            Who.NONE -> false
        }
    }

    fun matches(r: Rule, text: String): Boolean {
        val t = normalize(text)
        if (splitList(r.exclude).any { t.contains(normalize(it)) }) return false
        val keys = splitList(r.keywords).map { normalize(it) }
        return when (r.match) {
            Match.ALL -> true
            Match.CONTAINS -> keys.any { hasWord(t, it) }
            Match.EXACT -> keys.any { t == it }
            Match.STARTS -> keys.any { t.startsWith(it) }
            Match.ENDS -> keys.any { t.endsWith(it) }
            Match.SIMILAR -> keys.any { similarity(t, it) >= r.similarity }
            Match.WILDCARD -> normalize(r.keywords).split("//").map { it.trim() }.filter { it.isNotEmpty() }.any { p ->
                Regex(p.split("*").joinToString(".*") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL).matches(t)
            }
            Match.REGEX -> runCatching { Regex(r.keywords, RegexOption.IGNORE_CASE).containsMatchIn(text) }.getOrDefault(false)
        }
    }

    /** Whole-word (or whole-phrase) match, so "oi" does not fire on "dois". Both sides already normalized. */
    fun hasWord(t: String, k: String) = k.isNotEmpty() && Regex("(^|\\W)" + Regex.escape(k) + "($|\\W)").containsMatchIn(t)

    /**
     * Finds the "Consulta" rows whose search cell appears in the message; the longest match wins, and
     * every row tied with it is answered (e.g. all loads for one city), up to [LOOKUP_MAX].
     * The template can use {busca}, {resultado} and any column by letter, like {C}.
     */
    fun lookup(r: Rule, text: String, rows: List<List<String>>): String? {
        val t = normalize(text)
        val s = col(r.searchCol); val ret = col(r.returnCol)
        val hits = rows.drop(1)
            .filter { it.size > maxOf(s, ret) && it[s].isNotBlank() && hasWord(t, normalize(it[s])) }
        val best = hits.maxOfOrNull { it[s].trim().length } ?: return r.fallback.ifBlank { null }
        return hits.filter { it[s].trim().length == best }.take(LOOKUP_MAX).joinToString("\n\n") { row ->
            row.foldIndexed(r.template.replace("{busca}", row[s]).replace("{resultado}", row[ret])) { i, acc, v ->
                if (i < 26) acc.replace("{${'A' + i}}", v) else acc
            }
        }
    }

    /** [from]/[to] are comparison keys ("pontagrossapr"); [label] is for people ("PONTA GROSSA/PR → RIO DE JANEIRO/RJ"). */
    data class Route(val from: String, val to: String, val cargo: String = "", val label: String = "")

    private fun label(c1: String, u1: String, c2: String, u2: String) =
        "${c1.replace('-', ' ').trim()}/$u1 → ${c2.replace('-', ' ').trim()}/$u2".uppercase()

    private fun place(city: String, uf: String) = normalize(city).replace(Regex("[^a-z0-9]"), "") + normalize(uf)

    /** Origin/destination of a FreteBras message, from its sentence or, failing that, from the freight link. */
    fun freteRoute(text: String): Route? {
        val t = normalize(text)
        Regex("""frete de ([^/\n]+)/([a-z]{2}) para ([^/\n]+)/([a-z]{2})(?: de ([a-z]+))?""").find(t)?.let { m ->
            val g = m.groupValues
            return Route(place(g[1], g[2]), place(g[3], g[4]), g[5], label(g[1], g[2], g[3], g[4]))
        }
        Regex("""fretebras\.com\.br/frete/.*?carga-de-([a-z0-9-]+?)-([a-z]{2})-para-([a-z0-9-]+?)-([a-z]{2})/(?:.*?/carga-([a-z]+))?""").find(t)?.let { m ->
            val g = m.groupValues
            return Route(place(g[1], g[2]), place(g[3], g[4]), g[5], label(g[1], g[2], g[3], g[4]))
        }
        return null
    }

    /** Route of a freight-sheet row: column A "Origem/UF para Destino/UF", or the ORIGEM:/DESTINO: lines of the message. */
    private fun rowRoute(row: List<String>): Route? =
        rowPlaces(row)?.let { (c1, u1, c2, u2) -> Route(place(c1, u1), place(c2, u2), label = label(c1, u1, c2, u2)) }

    /** Origin city, origin UF, destination city, destination UF of a freight-sheet row (normalized). */
    private fun rowPlaces(row: List<String>): List<String>? {
        val a = normalize(row.firstOrNull().orEmpty())
        Regex("""^([^/]+)/([a-z]{2}) para ([^/]+)/([a-z]{2})""").find(a)?.let { return it.groupValues.drop(1) }
        val all = normalize(row.joinToString("\n"))
        // A list of many loads ("Gerar Lista") is not one load: only rows with a single ORIGEM count.
        if (Regex("origem:").findAll(all).count() != 1) return null
        val o = Regex("""origem:\W*([^/\n]+)/([a-z]{2})""").find(all)?.groupValues ?: return null
        val d = Regex("""destino:\W*([^/\n]+)/([a-z]{2})""").find(all)?.groupValues ?: return null
        return listOf(o[1], o[2], d[1], d[2])
    }

    /** A city of the freight sheet: its words and UF; [key] is the same as [place]. */
    private data class City(val words: List<String>, val uf: String) {
        val key get() = words.joinToString("") + uf
        val label get() = words.joinToString(" ").uppercase() + "/" + uf.uppercase()
    }

    private fun words(s: String) = normalize(s).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /**
     * Sheet cities named in the message, in order. Each entry holds every city that fits at that spot
     * (the same name can exist in two states); a UF written right after it narrows the choice.
     */
    private fun citiesIn(text: String, cities: Collection<City>): List<List<City>> {
        val t = words(text)
        val byLength = cities.sortedByDescending { it.words.size }
        val out = mutableListOf<List<City>>()
        var i = 0
        while (i < t.size) {
            val hits = byLength.filter { c -> i + c.words.size <= t.size && c.words.indices.all { k -> t[i + k] == c.words[k] } }
            if (hits.isEmpty()) { i++; continue }
            // The longest name wins: "aparecida de goiania" is not "goiania".
            val n = hits.first().words.size
            val same = hits.filter { it.words.size == n }
            val uf = t.getOrNull(i + n)?.takeIf { it.length == 2 }
            out += same.filter { it.uf == uf }.ifEmpty { same }
            i += n + if (uf != null && same.any { it.uf == uf }) 1 else 0
        }
        return out
    }

    /** Ready message of a sheet row: its longest cell after column A. */
    private fun ready(row: List<String>) = (row.drop(1).maxByOrNull { it.length } ?: row[0]).trim()

    private val COLETA = Regex("""coleta:\W*(?:[a-z]+\W+)?(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?:\W+(\d{1,2}):(\d{2}))?""")

    /** Pickup date of a load ("Coleta: Seg 19/10 08:00"); without a year, the one that puts it closest to [now]. */
    fun loadDate(row: List<String>, now: LocalDateTime): LocalDateTime? {
        val g = COLETA.find(normalize(row.joinToString("\n")))?.groupValues ?: return null
        return runCatching {
            val y = g[3].toIntOrNull()?.let { if (it < 100) it + 2000 else it } ?: now.year
            var d = LocalDateTime.of(y, g[2].toInt(), g[1].toInt(), g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0)
            if (g[3].isEmpty() && d.isBefore(now.minusMonths(6))) d = d.plusYears(1)
            if (g[3].isEmpty() && d.isAfter(now.plusMonths(6))) d = d.minusYears(1)
            d
        }.getOrNull()
    }

    /** A load the sheet script marked as closed or cancelled: its message is the fixed "❌ FRETE INDISPONÍVEL". */
    fun closed(message: String) = normalize(message).contains("frete indisponivel")

    /**
     * The sheet script clears the whole tab and writes it again every minute; a read in between comes back with only the
     * status lines. Then keep the last good copy, so a load that exists is not answered as "indisponível".
     */
    fun keepLoads(old: List<List<String>>, new: List<List<String>>): List<List<String>> {
        fun filled(rows: List<List<String>>) = rows.count { r -> r.any { it.isNotBlank() } }
        return if (filled(new) <= 2 && filled(old) > 2) old else new
    }

    /** Loads picking up from today on come first, soonest first; then past ones, latest first; then rows without a date; closed loads last. */
    fun nearestFirst(rows: List<List<String>>, now: LocalDateTime): List<List<String>> {
        val today = now.toLocalDate().atStartOfDay()
        return rows.sortedWith(compareBy { row ->
            if (closed(ready(row))) return@compareBy Long.MAX_VALUE
            val d = loadDate(row, now) ?: return@compareBy Long.MAX_VALUE - 1
            val min = java.time.Duration.between(today, d).toMinutes()
            if (min >= 0) min else Long.MAX_VALUE / 2 - min
        })
    }

    /** Answer to a freight question written freely ("Goiânia para Anápolis", "goiania-GO anapolis", "tem algo pra Brasília?"). Accents and case are ignored; spelling must be right. */
    data class FreeFrete(val label: String, val messages: List<String>)

    /**
     * Two sheet cities in the message: first is the origin, second the destination. One city after "para"/"pra": loads
     * going there; one city with "carga"/"frete": loads going there, or else leaving it. All their ready messages, nearest pickup first (only the first is sent).
     * Null = not a freight question.
     */
    fun freteLivre(text: String, rows: List<List<String>>, now: LocalDateTime = LocalDateTime.now()): FreeFrete? {
        // A load ad (ours echoed by another WhatsApp, or a list) names two cities but asks nothing; answering it loops.
        if (Regex("""origem:[\s\S]*destino:""").containsMatchIn(normalize(text))) return null
        val loads = rows.mapNotNull { row -> rowPlaces(row)?.let { p -> Triple(row, City(words(p[0]), p[1]), City(words(p[2]), p[3])) } }
        if (loads.isEmpty()) return null
        val found = citiesIn(text, loads.flatMap { listOf(it.second, it.third) }.toSet())
        fun ready(hits: List<Triple<List<String>, City, City>>) = nearestFirst(hits.map { it.first }, now).map(::ready).distinct()
        if (found.size >= 2) {
            val from = found[0].map { it.key }; val to = found[1].map { it.key }
            val hits = loads.filter { it.second.key in from && it.third.key in to }
            val label = (hits.firstOrNull()?.let { it.second.label + " → " + it.third.label }) ?: (found[0][0].label + " → " + found[1][0].label)
            return FreeFrete(label, ready(hits))
        }
        if (found.size == 1) {
            val t = normalize(text)
            val keys = found[0].map { it.key }
            // "pra Brasília" asks for loads going there; "carga" or "frete" with a city also accepts loads leaving it.
            val toCity = Regex("""\b(para|pra|p)\W+""" + found[0][0].words.joinToString("""\W+""")).containsMatchIn(t)
            val asksLoad = Regex("""\b(carga|cargas|frete|fretes)\b""").containsMatchIn(t)
            if (!toCity && !asksLoad) return null
            val hits = loads.filter { it.third.key in keys }.ifEmpty { if (asksLoad) loads.filter { it.second.key in keys } else emptyList() }
            return FreeFrete(found[0][0].label, ready(hits))
        }
        return null
    }

    /**
     * FreteBras "estou disponível para carregar seu frete" → the ready message of the matching row
     * (its longest cell after column A), preferring one with the same cargo type; no match → [notFound] (blank = stay silent).
     * Null when the message is not a FreteBras one.
     */
    fun frete(text: String, rows: List<List<String>>, notFound: String): String? =
        freteRoute(text)?.let { freteMessage(it, rows, LocalDateTime.now()) ?: notFound.ifBlank { null } }

    /** Diagnostic: sends the sheet's first route (column A) through the FreteBras matcher. Null = no route in the sheet. */
    fun freteSelfTest(rows: List<List<String>>): Pair<String, Boolean>? {
        val a = rows.map { it.firstOrNull().orEmpty().trim() }.firstOrNull { Regex("(?i)^[^/]+/\\w\\w para [^/]+/\\w\\w").containsMatchIn(it) } ?: return null
        return a to (frete("estou disponível para carregar seu frete de $a de TESTE", rows, "") != null)
    }

    /** Ready message of the sheet row for this route, or null when the route is not in the sheet. */
    fun freteMessage(r: Route, rows: List<List<String>>, now: LocalDateTime): String? {
        val hits = nearestFirst(rows.filter { rowRoute(it)?.let { x -> x.from == r.from && x.to == r.to } == true }, now)
        val best = hits.firstOrNull { r.cargo.isNotEmpty() && hasWord(normalize(it.joinToString(" ")), r.cargo) } ?: hits.firstOrNull()
        return best?.let(::ready)
    }

    private fun col(letter: String) = (letter.trim().uppercase().firstOrNull() ?: 'A') - 'A'

    private fun pick(r: Rule, random: Random): List<String> {
        val l = r.replies.filter { it.isNotBlank() }
        if (l.isEmpty()) return l
        return when (r.replyMode) {
            ReplyMode.SINGLE -> listOf(l[0])
            ReplyMode.SEQUENCE -> l
            ReplyMode.RANDOM -> listOf(l[random.nextInt(l.size)])
        }
    }

    /** "08:00-18:00" (may cross midnight, several ranges with commas) plus ISO weekdays 1..7. Empty hours = all day. */
    fun inWindow(hours: String, days: Set<Int>, now: LocalDateTime): Boolean {
        if (now.dayOfWeek.value !in days) return false
        if (hours.isBlank()) return true
        val t = now.toLocalTime()
        return hours.split(",").any { range ->
            val p = range.split("-").map { it.trim() }
            if (p.size != 2) return@any false
            val a = parseTime(p[0]) ?: return@any false
            val b = parseTime(p[1]) ?: return@any false
            if (a <= b) t >= a && t < b else t >= a || t < b
        }
    }

    private fun parseTime(s: String): LocalTime? = runCatching {
        val p = s.split(":"); LocalTime.of(p[0].toInt() % 24, p.getOrElse(1) { "0" }.toInt())
    }.getOrNull()

    fun fill(s: String, msg: Incoming, now: LocalDateTime, count: Int): String {
        val br = Locale("pt", "BR")
        val h = now.hour
        // Unsaved contacts come as a phone number; "Olá +55, ..." reads badly, so drop the name instead.
        val first = msg.sender.trim().split(" ").first().takeIf { w -> w.any { it.isLetter() } }.orEmpty()
        return s.replace("{nome}", msg.sender)
            .let { if (first.isEmpty()) it.replace(" {primeiro_nome}", "") else it }
            .replace("{primeiro_nome}", first)
            .replace("{mensagem}", msg.text)
            .replace("{data}", now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .replace("{hora}", now.format(DateTimeFormatter.ofPattern("HH:mm")))
            .replace("{dia_semana}", now.format(DateTimeFormatter.ofPattern("EEEE", br)))
            .replace("{saudacao}", if (h < 12) "bom dia" else if (h < 18) "boa tarde" else "boa noite")
            .replace("{grupo}", if (msg.isGroup) msg.chat else "")
            .replace("{contador}", count.toString())
    }

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun splitList(s: String) = s.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun similarity(a: String, b: String): Int {
        if (a.isEmpty() && b.isEmpty()) return 100
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return 100 - d[a.length][b.length] * 100 / maxOf(a.length, b.length)
    }

    /** QR code shown by the sheet script: {"zapresponde":1,"url":".../exec","key":"..."}. */
    fun parseConnectQr(raw: String): Pair<String, String>? = runCatching {
        val o = kotlinx.serialization.json.Json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
        fun str(k: String) = (o[k] as kotlinx.serialization.json.JsonPrimitive).content
        val url = str("url"); val key = str("key")
        if (!url.startsWith("https://script.google.com/") || !url.endsWith("/exec") || key.isBlank()) null else url to key
    }.getOrNull()

    /** Respostas tab: A keywords, B reply (several separated by "||" are sorted at random), C match, D who, E active. */
    fun rulesFromSheet(rows: List<List<String>>): List<Rule> = rows.drop(1).mapIndexedNotNull { i, row ->
        val keys = row.getOrElse(0) { "" }.trim()
        val reply = row.getOrElse(1) { "" }.trim()
        if (reply.isEmpty()) return@mapIndexedNotNull null
        val m = normalize(row.getOrElse(2) { "" })
        val match = when {
            m.startsWith("exat") -> Match.EXACT
            m.startsWith("comec") -> Match.STARTS
            m.startsWith("termin") -> Match.ENDS
            m.startsWith("curinga") -> Match.WILDCARD
            m.startsWith("regex") -> Match.REGEX
            m.startsWith("parecid") -> Match.SIMILAR
            m.startsWith("tod") || keys.isEmpty() -> Match.ALL
            else -> Match.CONTAINS
        }
        val w = normalize(row.getOrElse(3) { "" })
        val g = normalize(row.getOrElse(5) { "" })
        val active = normalize(row.getOrElse(4) { "sim" }).let { it.isEmpty() || it.startsWith("s") }
        val replies = reply.split("||").map { it.trim() }
        Rule(
            id = "sheet-${i + 2}", name = "Planilha linha ${i + 2}", enabled = active, match = match, keywords = keys,
            replies = replies, replyMode = if (replies.size > 1) ReplyMode.RANDOM else ReplyMode.SINGLE,
            who = when {
                w.startsWith("grupo") -> Who.NONE
                w.startsWith("nao salv") -> Who.UNSAVED
                w.startsWith("salv") -> Who.SAVED
                else -> Who.ALL
            },
            // Column F "Grupos" (sim/não); older sheets without it used Quem = "grupos"/"todos".
            groups = if (g.isNotEmpty()) (if (g.startsWith("s")) Groups.ALL else Groups.NONE)
            else if (w.startsWith("grupo") || w.startsWith("todos")) Groups.ALL else Groups.NONE,
            fromSheet = true,
        )
    }
}
