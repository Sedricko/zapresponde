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
    ): Decision {
        if (!cfg.enabled) return skip("Respostas automáticas desligadas")
        if (cfg.scheduleOn && !inWindow(cfg.scheduleHours, cfg.scheduleDays, now)) return skip("Fora da agenda")
        val cs = state.chats.getOrPut(msg.chat) { ChatState() }
        if (cs.pauseUntil > nowMs) return skip("Conversa pausada")
        // A message equal to something we just sent is our own reply echoed back in the notification.
        if (msg.text in cs.lastTexts) return skip("Mensagem é uma resposta nossa")
        if (cfg.loopGuardOn) {
            cs.recentReplies.removeAll { nowMs - it > LOOP_WINDOW }
            if (cs.recentReplies.size >= LOOP_MAX) {
                cs.pauseUntil = nowMs + LOOP_PAUSE
                cs.recentReplies.clear()
                return skip("Proteção contra loop: conversa pausada por 30 min")
            }
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
                return finish(msg, now, cfg, cs, nowMs, listOf(questions[0]), "Pedido", null, random)
            }
        }

        if (cfg.welcomeOn) {
            val today = now.toLocalDate().toString()
            val due = if (cfg.welcomeDaily) cs.welcomedDay != today else cs.welcomedDay.isEmpty()
            if (due) {
                cs.welcomedDay = today
                out += cfg.welcomeText
                ruleName = "Boas-vindas"
            }
        }

        for (r in rules) {
            if (!r.enabled || !allowed(r, msg) || !inWindow(r.hours, r.days, now)) continue
            if (!matches(r, msg.text)) continue
            val st = cs.rules.getOrPut(r.id) { longArrayOf(0, 0) }
            val ok = when (r.freq) {
                Freq.ALWAYS -> true
                Freq.ONCE -> st[0] == 0L
                Freq.INTERVAL -> st[0] == 0L || nowMs - st[1] >= r.intervalMin * 60_000L
                Freq.MAX -> st[0] < r.maxReplies
            }
            if (!ok) continue
            val replies = when (r.kind) {
                Kind.TEXT -> pick(r, random)
                Kind.LOOKUP -> listOfNotNull(lookup(r, msg.text, consulta))
            }.filter { it.isNotBlank() }
            if (replies.isEmpty()) continue
            st[0]++; st[1] = nowMs
            out += replies
            ruleName = if (ruleName.isEmpty()) r.name else "$ruleName + ${r.name}"
            break
        }
        if (out.isEmpty()) return skip("Nenhuma regra serviu")
        return finish(msg, now, cfg, cs, nowMs, out, ruleName, formRow, random)
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
            Match.CONTAINS -> keys.any { t.contains(it) }
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

    /** Finds the "Consulta" row whose search cell appears in the message; longest match wins. */
    fun lookup(r: Rule, text: String, rows: List<List<String>>): String? {
        val t = normalize(text)
        val s = col(r.searchCol); val ret = col(r.returnCol)
        val hit = rows.drop(1)
            .filter { it.size > maxOf(s, ret) && it[s].isNotBlank() }
            .filter { Regex("(^|\\W)" + Regex.escape(normalize(it[s])) + "($|\\W)").containsMatchIn(t) }
            .maxByOrNull { it[s].length }
        return if (hit == null) r.fallback.ifBlank { null }
        else r.template.replace("{busca}", hit[s]).replace("{resultado}", hit[ret])
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
        return s.replace("{nome}", msg.sender)
            .replace("{primeiro_nome}", msg.sender.trim().split(" ").first())
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
        val active = normalize(row.getOrElse(4) { "sim" }).let { it.isEmpty() || it.startsWith("s") }
        val replies = reply.split("||").map { it.trim() }
        Rule(
            id = "sheet-${i + 2}", name = "Planilha linha ${i + 2}", enabled = active, match = match, keywords = keys,
            replies = replies, replyMode = if (replies.size > 1) ReplyMode.RANDOM else ReplyMode.SINGLE,
            who = if (w.startsWith("grupo")) Who.NONE else Who.ALL,
            groups = if (w.startsWith("grupo") || w.startsWith("todos")) Groups.ALL else Groups.NONE,
            fromSheet = true,
        )
    }
}
