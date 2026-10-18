package app.zapresponde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class EngineTest {
    private val monday10 = LocalDateTime.of(2026, 10, 19, 10, 0)
    private val on = Config(enabled = true, delayOn = false, loopGuardOn = true)
    private fun msg(text: String, sender: String = "Maria Silva", group: String? = null) =
        Incoming(group ?: sender, sender, text, group != null)

    private fun run(m: Incoming, rules: List<Rule>, cfg: Config = on, state: State = State(), now: Long = 1_000_000L, consulta: List<List<String>> = emptyList()) =
        Engine.decide(m, monday10, now, cfg, rules, consulta, state, Random(1))

    @Test fun matchTypes() {
        fun r(m: Match, k: String) = Rule(match = m, keywords = k)
        assertTrue(Engine.matches(r(Match.CONTAINS, "preço, valor"), "Qual o PRECO?"))
        assertFalse(Engine.matches(r(Match.CONTAINS, "oi, ola"), "Tenho dois fretes, carga de Angola"))
        assertTrue(Engine.matches(r(Match.CONTAINS, "bom dia"), "ok, bom dia!"))
        assertFalse(Engine.matches(r(Match.EXACT, "oi"), "oi tudo bem"))
        assertTrue(Engine.matches(r(Match.STARTS, "bom dia"), "Bom dia!"))
        assertTrue(Engine.matches(r(Match.WILDCARD, "oi*tudo bem//ola*"), "Oi, tudo bem"))
        assertTrue(Engine.matches(r(Match.REGEX, "\\d{4}"), "pin 1234"))
        assertTrue(Engine.matches(r(Match.SIMILAR, "obrigado").copy(similarity = 75), "obrigdo"))
        assertFalse(Engine.matches(r(Match.ALL, "").copy(exclude = "spam"), "isso é spam"))
    }

    @Test fun offAndVariables() {
        val rule = Rule(match = Match.ALL, replies = listOf("Olá {primeiro_nome}, {saudacao}"))
        assertTrue(run(msg("oi"), listOf(rule), on.copy(enabled = false)).replies.isEmpty())
        assertEquals(listOf("Olá Maria, bom dia"), run(msg("oi"), listOf(rule)).replies)
        assertEquals(listOf("Olá, bom dia"), run(msg("oi", sender = "+55 11 99999-0000"), listOf(rule)).replies)
    }

    @Test fun contacts() {
        val rule = Rule(match = Match.ALL, replies = listOf("ok"))
        fun ans(m: Incoming, cfg: Config, phone: Set<String> = emptySet()) =
            Engine.decide(m, monday10, 1_000_000L, cfg, listOf(rule), emptyList(), State(), Random(1), phone).replies
        val list = on.copy(contactsOn = true, contactsList = "João (Transportes Poloni)\nMeu")
        val joao = msg("oi", sender = "Joao (Transportes Poloni)")
        assertEquals(listOf("ok"), ans(joao, list.copy(contactsOn = false, contactsMode = Audience.LIST)))
        assertTrue(ans(joao, list.copy(contactsMode = Audience.EXCEPT_LIST)).isEmpty())
        assertEquals(listOf("ok"), ans(msg("oi"), list.copy(contactsMode = Audience.EXCEPT_LIST)))
        assertEquals(listOf("ok"), ans(joao, list.copy(contactsMode = Audience.LIST)))
        assertTrue(ans(msg("oi"), list.copy(contactsMode = Audience.LIST)).isEmpty())
        val phone = list.copy(contactsMode = Audience.EXCEPT_PHONE)
        assertTrue(ans(msg("oi"), phone, setOf("maria silva")).isEmpty())
        assertEquals(listOf("ok"), ans(msg("oi", sender = "Pedro"), phone, setOf("maria silva")))
        assertEquals(listOf("ok"), ans(msg("oi", sender = "+55 11 98888-0000"), phone))
        val grp = msg("oi", group = "Família")
        assertTrue(ans(grp, list.copy(groupsOn = false)).isEmpty())
    }

    @Test fun groupsAndContacts() {
        val rule = Rule(match = Match.ALL, replies = listOf("x"))
        assertTrue(run(msg("oi", group = "Família"), listOf(rule)).replies.isEmpty())
        assertEquals(1, run(msg("oi", group = "Família"), listOf(rule.copy(groups = Groups.ALL))).replies.size)
        assertTrue(run(msg("oi"), listOf(rule.copy(who = Who.UNSAVED))).replies.isEmpty())
        assertEquals(1, run(msg("oi", sender = "+55 11 99999-0000"), listOf(rule.copy(who = Who.UNSAVED))).replies.size)
    }

    @Test fun frequency() {
        val st = State()
        val once = Rule(id = "a", match = Match.ALL, replies = listOf("x"), freq = Freq.ONCE)
        assertEquals(1, run(msg("1"), listOf(once), state = st).replies.size)
        assertTrue(run(msg("2"), listOf(once), state = st).replies.isEmpty())
        val st2 = State()
        val every = Rule(id = "b", match = Match.ALL, replies = listOf("y"), freq = Freq.INTERVAL, intervalMin = 10)
        assertEquals(1, run(msg("1"), listOf(every), state = st2, now = 0).replies.size)
        assertTrue(run(msg("2"), listOf(every), state = st2, now = 5 * 60_000).replies.isEmpty())
        assertEquals(1, run(msg("3"), listOf(every), state = st2, now = 11 * 60_000).replies.size)
    }

    @Test fun ownEchoAndLoopGuard() {
        val st = State()
        val rule = Rule(match = Match.ALL, replies = listOf("resp"))
        assertEquals(listOf("resp"), run(msg("a"), listOf(rule), state = st).replies)
        assertTrue(run(msg("resp"), listOf(rule), state = st).replies.isEmpty())
        repeat(4) { run(msg("m$it"), listOf(rule), state = st, now = 1_000_000L + it) }
        val d = run(msg("again"), listOf(rule), state = st, now = 1_000_010L)
        assertTrue(d.reason.contains("loop"))
        assertTrue(st.chats["Maria Silva"]!!.pauseUntil > 1_000_010L)
    }

    @Test fun windows() {
        assertTrue(Engine.inWindow("08:00-18:00", (1..5).toSet(), monday10))
        assertFalse(Engine.inWindow("08:00-18:00", setOf(6, 7), monday10))
        assertTrue(Engine.inWindow("22:00-08:00", (1..7).toSet(), LocalDateTime.of(2026, 10, 19, 2, 0)))
        assertFalse(Engine.inWindow("22:00-08:00", (1..7).toSet(), monday10))
    }

    @Test fun welcomeThenRule() {
        val st = State()
        val cfg = on.copy(welcomeOn = true, welcomeText = "Bem-vindo")
        val rule = Rule(match = Match.CONTAINS, keywords = "preço", replies = listOf("R$ 10"))
        assertEquals(listOf("Bem-vindo", "R$ 10"), run(msg("preço?"), listOf(rule), cfg, st).replies)
        assertEquals(listOf("R$ 10"), run(msg("e o preço"), listOf(rule), cfg, st, now = 2_000_000).replies)
    }

    @Test fun form() {
        val st = State()
        val cfg = on.copy(formOn = true, formTrigger = "pedido", formQuestions = "Nome?\nData?", formDone = "Anotado")
        assertEquals(listOf("Nome?"), run(msg("quero fazer um pedido"), emptyList(), cfg, st).replies)
        assertEquals(listOf("Data?"), run(msg("Ana"), emptyList(), cfg, st, now = 2_000_000).replies)
        val d = run(msg("sábado"), emptyList(), cfg, st, now = 3_000_000)
        assertEquals(listOf("Anotado"), d.replies)
        assertEquals(listOf("Maria Silva", "Ana", "sábado"), d.formRow)
    }

    @Test fun lookup() {
        val rows = listOf(listOf("Código", "Status"), listOf("123", "enviado"), listOf("1234", "entregue"))
        val r = Rule(kind = Kind.LOOKUP, match = Match.CONTAINS, keywords = "pedido", template = "Pedido {busca}: {resultado}", fallback = "Não achei")
        assertEquals(listOf("Pedido 1234: entregue"), run(msg("status do pedido 1234"), listOf(r), consulta = rows).replies)
        assertEquals(listOf("Pedido 123: enviado"), run(msg("pedido 123?"), listOf(r), consulta = rows).replies)
        assertEquals(listOf("Não achei"), run(msg("pedido 999"), listOf(r), consulta = rows).replies)
        val cargas = listOf(listOf("Destino", "Valor", "Peso"), listOf("Curitiba", "R$ 3.000", "12 t"), listOf("Curitiba", "R$ 2.500", "8 t"), listOf("Campinas", "R$ 900", "2 t"))
        val c = r.copy(keywords = "carga", template = "{busca}: {B}, {C}")
        assertEquals(listOf("Curitiba: R$ 3.000, 12 t\n\nCuritiba: R$ 2.500, 8 t"), run(msg("tem carga pra curitiba?"), listOf(c), consulta = cargas).replies)
    }

    @Test fun frete() {
        val sheet = listOf(
            listOf("ONLINE | ATUALIZAÇÃO: 11:49:26", ""),
            listOf("Ponta Grossa/PR para Rio de Janeiro/RJ", "🚛 *FRETE DISPONÍVEL*\n*ORIGEM:* PONTA GROSSA/PR\n*CARGA:* 31 Ton | Bebida"),
            listOf("", "🚛 *FRETE DISPONÍVEL*\n*ORIGEM:* PATOS DE MINAS/MG\n*DESTINO:* RIO DE JANEIRO/RJ\n*CARGA:* 26 Ton | Alimenticio"),
            listOf("Patos de Minas/MG para Rio de Janeiro/RJ", "🚛 *FRETE DISPONÍVEL*\n*ORIGEM:* PATOS DE MINAS/MG\n*CARGA:* 10 Ton | Bebida"),
        )
        val msgPg = "Olá, sou Marco e estou disponível para carregar seu frete de Ponta Grossa/PR para Rio de Janeiro/RJ de BEBIDA.\n\nA carga ainda está disponível?"
        assertTrue(Engine.frete(msgPg, sheet, "x")!!.contains("31 Ton"))
        val msgPatos = "estou disponível para carregar seu frete de Patos de Minas/MG para Rio de Janeiro/RJ de ALIMENTICIO."
        assertTrue(Engine.frete(msgPatos, sheet, "x")!!.contains("Alimenticio"))
        val soLink = "Frete: https://www.fretebras.com.br/frete/transportes-poloni/carga-de-ponta-grossa-pr-para-rio-de-janeiro-rj/veiculo-carreta/especie-pallet/carga-bebida/1753372597044/"
        assertTrue(Engine.frete(soLink, sheet, "x")!!.contains("31 Ton"))
        val negociar = "Olá, vi seu frete na FreteBras, e tenho interesse em negociar.\nPágina do frete: https://www.fretebras.com.br/frete/transportes-poloni/carga-de-serra-es-para-salvador-ba/veiculo-carreta/especie-outro/carga-alimenticio/1758154495548"
        assertEquals(Engine.Route("serraes", "salvadorba", "alimenticio", "SERRA/ES → SALVADOR/BA"), Engine.freteRoute(negociar))
        assertTrue(Engine.frete(negociar, sheet + listOf(listOf("Serra/ES para Salvador/BA", "carga serra")), "x") == "carga serra")
        val soLinkGo = "https://www.fretebras.com.br/frete/transportes-poloni/carga-de-goiania-go-para-rio-verde-go/veiculo-truck/especie-outro/carga-higiene-e-limpeza/1757316291300/"
        assertEquals(Engine.Route("goianiago", "rioverdego", "higiene", "GOIANIA/GO → RIO VERDE/GO"), Engine.freteRoute(soLinkGo))
        assertEquals("carga go", Engine.frete(soLinkGo, sheet + listOf(listOf("Goiânia/GO para Rio Verde/GO", "carga go")), "x"))
        val lista = listOf("Gerar Lista", "ORIGEM: CRISTALINA/GO\nDESTINO: CARIACICA/ES\n---\nORIGEM: SERRA/ES\nDESTINO: IPIRÁ/BA")
        assertEquals("x", Engine.frete("frete de Cristalina/GO para Cariacica/ES de ALIMENTICIO", sheet + listOf(lista), "x"))
        assertEquals("Ponta Grossa/PR para Rio de Janeiro/RJ" to true, Engine.freteSelfTest(sheet))
        assertEquals(null, Engine.freteSelfTest(listOf(listOf("Lista", "x"))))
        val fechada = "estou disponível para carregar seu frete de Cristalina/GO para Cariacica/ES de ALIMENTICIO."
        assertEquals("x", Engine.frete(fechada, sheet, "x"))
        assertEquals(null, Engine.frete(fechada, sheet, ""))
        assertEquals(null, Engine.frete("oi, bom dia", sheet, "x"))
        val rule = Rule(match = Match.ALL, replies = listOf("regra"))
        assertEquals(listOf("x"), run(msg(fechada), listOf(rule), on.copy(freteOn = true, freteNotFound = "x"), consulta = sheet).replies)
        val d = run(msg(msgPg), listOf(rule), on.copy(freteOn = true), consulta = sheet)
        assertEquals(listOf("Maria Silva", "PONTA GROSSA/PR → RIO DE JANEIRO/RJ", "BEBIDA", "disponível"), d.leadRow)
        assertEquals("Maria Silva quer a carga PONTA GROSSA/PR → RIO DE JANEIRO/RJ", d.alert)
        assertEquals(null, run(msg(fechada), listOf(rule), on.copy(freteOn = true), consulta = sheet).alert)
        assertEquals(listOf("regra"), run(msg(fechada), listOf(rule), consulta = sheet).replies)
    }

    @Test fun sheetRows() {
        val rules = Engine.rulesFromSheet(listOf(
            listOf("Palavras", "Resposta", "Comparação", "Quem", "Ativo"),
            listOf("oi, olá", "Oi! || Olá!", "contém", "todos", "sim"),
            listOf("horário", "8h às 18h", "exata", "", "não"),
            listOf("sem resposta", "", "", "", ""),
            listOf("frete", "Temos!", "contém", "não salvos", "sim", "não"),
        ))
        assertEquals(3, rules.size)
        assertEquals(Who.UNSAVED, rules[2].who)
        assertEquals(Groups.NONE, rules[2].groups)
        assertEquals(ReplyMode.RANDOM, rules[0].replyMode)
        assertEquals(Groups.ALL, rules[0].groups)
        assertEquals(Match.EXACT, rules[1].match)
        assertFalse(rules[1].enabled)
    }

    @Test fun headerFooter() {
        val cfg = on.copy(headerFooterOn = true, header = "[Loja]", footer = "Obrigado")
        val r = Rule(match = Match.ALL, replies = listOf("a", "b"), replyMode = ReplyMode.SEQUENCE)
        assertEquals(listOf("[Loja]\na", "b\nObrigado"), run(msg("x"), listOf(r), cfg).replies)
    }

    @Test fun connectQr() {
        val url = "https://script.google.com/macros/s/abc/exec"
        assertEquals(url to "k1", Engine.parseConnectQr("""{"zapresponde":1,"url":"$url","key":"k1"}"""))
        assertEquals(null, Engine.parseConnectQr("""{"url":"https://evil.com/exec","key":"k1"}"""))
        assertEquals(null, Engine.parseConnectQr("texto qualquer"))
    }

    @Test fun reasonsExplainSilence() {
        val st = State()
        val once = Rule(id = "a", name = "Saudação", match = Match.ALL, replies = listOf("x"), freq = Freq.ONCE)
        run(msg("1"), listOf(once), state = st)
        val d = run(msg("2"), listOf(once), state = st, now = 2_000_000L)
        assertTrue(d.reason.contains("Saudação") && d.reason.contains("frequência"))
        val soGrupo = Rule(name = "R", match = Match.ALL, replies = listOf("x"), who = Who.NONE)
        assertTrue(run(msg("oi"), listOf(soGrupo)).reason.contains("não permitido"))
        assertEquals("Nenhuma regra serviu", run(msg("oi"), emptyList()).reason)
    }

    @Test fun assistantDraft() {
        val ai = """Claro! {"rule": {"name": "Preço", "match": "CONTAINS", "keywords": "preço, valor", "replies": ["A partir de R$ 50"],
            "freq": "ONCE", "hours": "08:00-18:00", "days": [1,2,3,4,5], "inventado": 1},
            "testes": [{"mensagem": "qual o preço?", "responde": true}, {"mensagem": "bom dia", "responde": false}]}"""
        val d = Assistant.parse(ai)
        assertEquals("Preço", d.rule.name)
        assertEquals(Freq.ONCE, d.rule.freq)
        assertEquals(setOf(1, 2, 3, 4, 5), d.rule.days)
        assertTrue(d.problems.isEmpty())
        assertTrue(Assistant.check(d).all { it.second })
        assertTrue(Assistant.problems(d.rule.copy(hours = "8h às 18h", replies = listOf(""))).size == 2)
        assertTrue(runCatching { Assistant.parse("""{"rule": {"match": "QUALQUER"}}""") }.isFailure)
        assertTrue(runCatching { Assistant.parse("não sei") }.isFailure)
        assertTrue(Assistant.prompt("muda o horário", d.rule).contains("\"name\":\"Preço\""))
    }

    @Test fun freteLivre() {
        val sheet = listOf(
            listOf("ONLINE | ATUALIZAÇÃO: 11:49:26", ""),
            listOf("Goiânia/GO para Anápolis/GO", "carga goiania anapolis"),
            listOf("Goiânia/GO para Aparecida de Goiânia/GO", "carga aparecida"),
            listOf("São Paulo/SP para Serra/ES", "carga sp serra"),
            listOf("Anápolis/GO para Brasília/DF", "carga brasilia 1"),
            listOf("Goiânia/GO para Brasília/DF", "carga brasilia 2"),
        )
        for (t in listOf(
            "Goiânia/GO para Anápolis/GO", "Goiânia para Anápolis", "Goiânia-GO para Anápolis-GO",
            "Goiânia - GO para Anápolis - GO", "Goiânia/GO Anápolis/GO", "Goiânia Anápolis", "eu to em goiania quero ir para anapolis",
        )) assertEquals(t, listOf("carga goiania anapolis"), Engine.freteLivre(t, sheet)!!.messages)
        assertEquals(listOf("carga aparecida"), Engine.freteLivre("Goiania/GO para Aparecida de Goiania", sheet)!!.messages)
        assertEquals(listOf("carga sp serra"), Engine.freteLivre("Sao paulo para Serra", sheet)!!.messages)
        assertEquals(listOf("carga brasilia 1", "carga brasilia 2"), Engine.freteLivre("tem algo pra Brasilia?", sheet)!!.messages)
        assertEquals(emptyList<String>(), Engine.freteLivre("Anápolis para Goiânia", sheet)!!.messages)
        // Misspelled city is not recognized: only "para anapolis" counts, so it lists loads going to Anápolis.
        assertEquals("ANAPOLIS/GO", Engine.freteLivre("goinaia para anapolis", sheet)!!.label)
        assertEquals(null, Engine.freteLivre("goinaia anapoliss", sheet))
        assertEquals(null, Engine.freteLivre("vou de Goiania para casa", sheet))
        assertEquals(listOf("carga goiania anapolis", "carga aparecida", "carga brasilia 2"), Engine.freteLivre("tem carga saindo de goiania?", sheet)!!.messages)
        assertEquals(listOf("carga goiania anapolis"), Engine.freteLivre("GOIANIA PARA ANAPOLIS", sheet)!!.messages)
        assertEquals(null, Engine.freteLivre("Boa noite, estou em Goiânia", sheet))
        assertEquals(null, Engine.freteLivre("🚛 FRETE DISPONÍVEL\n🏭 *ORIGEM:* GOIÂNIA/GO\n📍 *DESTINO:* ANÁPOLIS/GO", sheet))
        val cfg = on.copy(freteOn = true, freteLivreOn = true, freteNotFound = "indisponível")
        val rule = Rule(match = Match.ALL, replies = listOf("regra"))
        assertEquals(listOf("carga goiania anapolis"), run(msg("Goiânia para Anápolis"), listOf(rule), cfg, consulta = sheet).replies)
        assertEquals(listOf("indisponível"), run(msg("Anápolis para Goiânia"), listOf(rule), cfg, consulta = sheet).replies)
        // Only one load is sent: the one picking up soonest from today (monday10 = 19/10); past and undated ones go last.
        assertEquals(listOf("carga brasilia 1"), run(msg("tem algo pra Brasilia?"), listOf(rule), cfg, consulta = sheet).replies)
        val dated = listOf(
            listOf("Goiânia/GO para Anápolis/GO", "sem data"),
            listOf("Goiânia/GO para Anápolis/GO", "• Coleta: Sex 16/10 08:00"),
            listOf("Goiânia/GO para Anápolis/GO", "• Coleta: Qua 21/10 08:00"),
            listOf("Goiânia/GO para Anápolis/GO", "• Coleta: Ter 20/10 07:00"),
        )
        assertEquals(listOf("• Coleta: Ter 20/10 07:00"), run(msg("Goiânia para Anápolis"), listOf(rule), cfg, consulta = dated).replies)
        assertEquals("• Coleta: Ter 20/10 07:00", Engine.freteMessage(Engine.freteRoute("frete de Goiânia/GO para Anápolis/GO")!!, dated, monday10))
        assertEquals(LocalDateTime.of(2027, 1, 5, 0, 0), Engine.loadDate(listOf("Coleta: 05/01"), LocalDateTime.of(2026, 12, 20, 9, 0)))
        assertEquals(listOf("regra"), run(msg("Goiânia para Anápolis"), listOf(rule), cfg.copy(freteLivreOn = false), consulta = sheet).replies)
        // Our reply to one chat, seen again as a message from another chat (the other WhatsApp on the phone), is an echo.
        val st = State()
        val sent = run(msg("Goiânia para Anápolis", sender = "Johon"), listOf(rule), cfg, state = st, consulta = sheet).replies[0]
        assertEquals("Mensagem é uma resposta nossa", run(msg(sent, sender = "Você"), listOf(rule), cfg, state = st, consulta = sheet).reason)
        // Closed loads (the sheet script writes "❌ *FRETE INDISPONÍVEL*") go last and are not "disponível" in Leads nor alerted.
        val off = "❌ *FRETE INDISPONÍVEL*\n\nEsta carga já foi fechada ou expirou."
        val mixed = listOf(
            listOf("🟢 ONLINE", ""),
            listOf("Serra/ES para Caratinga/MG", off),
            listOf("Serra/ES para Caratinga/MG", "🚛 *FRETE DISPONÍVEL*\n• Coleta: A combinar"),
            listOf("Mucuri/BA para Serra/ES", off),
        )
        val active = run(msg("Serra para Caratinga"), listOf(rule), cfg, consulta = mixed)
        assertTrue(active.replies[0].contains("FRETE DISPONÍVEL") && active.alert != null && active.leadRow!![3] == "disponível")
        val gone = run(msg("Mucuri para Serra"), listOf(rule), cfg, consulta = mixed)
        assertTrue(gone.replies[0].contains("INDISPONÍVEL") && gone.alert == null && gone.leadRow!![3] == "indisponível")
        assertTrue(gone.trace.any { "0 carga(s) ativa(s) e 1 encerrada(s)" in it })
        val fb = run(msg("estou disponível para carregar seu frete de Mucuri/BA para Serra/ES"), listOf(rule), cfg, consulta = mixed)
        assertTrue(fb.alert == null && fb.leadRow!![3] == "indisponível" && fb.trace.any { "carga encerrada" in it })
        // A read while the script rewrites the tab (only status lines) keeps the last good copy.
        assertEquals(mixed, Engine.keepLoads(mixed, listOf(listOf("🟢 ONLINE", ""), listOf("📂 Resumo", ""))))
        assertEquals(sheet, Engine.keepLoads(mixed, sheet))
        val tr = run(msg("Goiânia para Anápolis"), listOf(rule), cfg, consulta = sheet).trace
        assertTrue(tr.toString(), tr.any { "Frete escrito: entendi GOIANIA/GO → ANAPOLIS/GO, 1 carga" in it })
        val tr2 = run(msg("oi"), listOf(rule.copy(name = "Oi", match = Match.CONTAINS, keywords = "tchau"), rule.copy(name = "Tudo")), cfg, consulta = sheet).trace
        assertTrue(tr2.toString(), tr2.any { it.startsWith("❌ Regra Oi: não casou (Contém: tchau)") } && tr2.last().startsWith("✅ Regra Tudo: casou"))
        val (tests, notes) = Assistant.parseRuleTests("""{"testes": [{"mensagem": "oi tchau", "regra": "Oi"}, {"mensagem": "bom dia", "regra": ""}], "observacoes": ["Tudo pega qualquer mensagem"]}""")
        val rs = listOf(rule.copy(name = "Oi", match = Match.CONTAINS, keywords = "tchau"), rule.copy(name = "Tudo"))
        assertEquals(listOf("Oi", "Tudo"), Assistant.runRuleTests(tests, rs).map { it.second })
        assertEquals(listOf("Tudo pega qualquer mensagem"), notes)
        assertTrue(Assistant.rulesTestPrompt(rs.take(1), rs).contains("Regras a testar: \"Oi\""))
        assertTrue(Assistant.diagnosisPrompt("REL", cfg.copy(aiKey = "SEGREDO"), listOf(rule), "").let { "REL" in it && "SEGREDO" !in it })
    }

    @Test fun acompanhamento() {
        val cfg = on.copy(followOn = true, followMin = 30, followLink = "https://chat.whatsapp.com/X", delayOn = false)
        val rule = Rule(match = Match.ALL, replies = listOf("oi"))
        val st = State()
        run(msg("bom dia", sender = "João Silva"), listOf(rule), cfg, state = st, now = 1_000_000L)
        assertEquals(1_000_000L + 30 * 60_000L, st.chats["João Silva"]!!.followAt)
        assertTrue(Engine.followDue(st, 1_000_000L + 29 * 60_000L).isEmpty())
        assertEquals(listOf("João Silva"), Engine.followDue(st, 1_000_000L + 30 * 60_000L))
        // A new message restarts the count from it.
        run(msg("e aí?", sender = "João Silva"), listOf(rule), cfg, state = st, now = 2_000_000L)
        assertEquals(2_000_000L + 30 * 60_000L, st.chats["João Silva"]!!.followAt)
        // You writing in the chat cancels it; our own reply echoed as yours does not.
        run(Incoming("João Silva", "Eu", "oi", false, fromMe = true), listOf(rule), cfg, state = st, now = 2_100_000L)
        assertEquals(2_000_000L + 30 * 60_000L, st.chats["João Silva"]!!.followAt)
        run(Incoming("João Silva", "Eu", "pode deixar que eu vejo", false, fromMe = true), listOf(rule), cfg, state = st, now = 2_200_000L)
        assertEquals(0L, st.chats["João Silva"]!!.followAt)
        // "Já estou no grupo" stops it for good.
        val st2 = State()
        run(msg("Já estou no grupo, obrigado", sender = "Ana"), listOf(rule), cfg, state = st2)
        assertTrue(st2.chats["Ana"]!!.followDone && st2.chats["Ana"]!!.followAt == 0L)
        // Groups and the switch off never schedule.
        val st3 = State()
        run(msg("oi", sender = "Bia", group = "Turma"), listOf(rule.copy(groups = Groups.ALL)), cfg, state = st3)
        run(msg("oi", sender = "Caio"), listOf(rule), cfg.copy(followOn = false), state = st3)
        assertTrue(Engine.followDue(st3, Long.MAX_VALUE).isEmpty())
        assertEquals("Bom dia, João! Entre: https://chat.whatsapp.com/X",
            Engine.followText(cfg.copy(followText = "{saudacao}, {primeiro_nome}! Entre: {link}"), msg("x", sender = "João Silva"), monday10, 1)
                .replaceFirstChar { it.uppercase() })
    }

    @Test fun outraCargaEAviso() {
        val off = "❌ *FRETE INDISPONÍVEL*"
        val sheet = listOf(
            listOf("🟢 ONLINE", ""),
            listOf("Goiânia/GO para Rio Verde/GO", "carga gyn rio verde • Coleta: Qua 21/10 08:00"),
            listOf("Goiânia/GO para Jataí/GO", "carga gyn jatai • Coleta: Ter 20/10 08:00"),
            listOf("Senador Canedo/GO para Anápolis/GO", "carga canedo anapolis • Coleta: Ter 20/10 09:00"),
            listOf("Goiânia/GO para Anápolis/GO", off),
        )
        val rule = Rule(match = Match.ALL, replies = listOf("regra"))
        val cfg = on.copy(freteOn = true, freteLivreOn = true, altOn = true, waitOn = true)
        // Same origin first, nearest pickup: Jataí (20/10) before Rio Verde (21/10).
        val d = run(msg("Goiânia para Anápolis", sender = "Johon"), listOf(rule), cfg, consulta = sheet)
        assertEquals("GOIANIA/GO → ANAPOLIS/GO não está disponível, mas tenho esta saindo de GOIANIA/GO:\n\ncarga gyn jatai • Coleta: Ter 20/10 08:00", d.replies[0])
        assertEquals("alternativa: GOIANIA/GO → JATAI/GO", d.leadRow!![3])
        assertTrue(d.alert!!.contains("ofereci GOIANIA/GO → JATAI/GO"))
        // Only "same destination": the Senador Canedo load.
        val d2 = run(msg("Goiânia para Anápolis"), listOf(rule), cfg.copy(altOrigin = false), consulta = sheet)
        assertTrue(d2.replies[0].endsWith("carga canedo anapolis • Coleta: Ter 20/10 09:00") && "indo para ANAPOLIS/GO" in d2.replies[0])
        // Off: the closed load's own "indisponível" message.
        assertEquals(off, run(msg("Goiânia para Anápolis"), listOf(rule), cfg.copy(altOn = false), consulta = sheet).replies[0])
        // The request waits; when the load shows up, it is found once, then forgotten.
        val st = State()
        run(msg("Goiânia para Anápolis", sender = "Johon"), listOf(rule), cfg, state = st, now = 1_000_000L, consulta = sheet)
        assertEquals(listOf("GOIANIA/GO → ANAPOLIS/GO"), st.waiting.map { it.label })
        assertTrue(Engine.waitingFound(st, sheet, monday10, 1_000_000L, 24).isEmpty())
        val later = sheet + listOf(listOf("Goiânia/GO para Anápolis/GO", "nova carga gyn anapolis • Coleta: Qui 22/10 08:00"))
        val found = Engine.waitingFound(st, later, monday10, 2_000_000L, 24)
        assertEquals(listOf("Johon" to "nova carga gyn anapolis • Coleta: Qui 22/10 08:00"), found.map { it.first.chat to it.second })
        assertTrue(st.waiting.isEmpty() && Engine.waitingFound(st, later, monday10, 2_000_000L, 24).isEmpty())
        // Expired after the configured hours.
        run(msg("Goiânia para Anápolis", sender = "Johon"), listOf(rule), cfg, state = st, now = 1_000_000L, consulta = sheet)
        assertTrue(Engine.waitingFound(st, later, monday10, 1_000_000L + 25 * 3_600_000L, 24).isEmpty() && st.waiting.isEmpty())
        // "pra Brasília" waits for any load going there.
        val st2 = State()
        run(msg("tem algo pra Brasilia?", sender = "Ana"), listOf(rule), cfg.copy(altOn = false), state = st2, consulta = sheet + listOf(listOf("Brasília/DF para Goiânia/GO", "volta")))
        assertEquals(1, Engine.waitingFound(st2, sheet + listOf(listOf("Anápolis/GO para Brasília/DF", "ida • Coleta: Ter 20/10 08:00"), listOf("Brasília/DF para Goiânia/GO", "volta")), monday10, 1_000_000L, 24).size)
    }

    @Test fun relatorio1810() {
        // Number without a name: "Bom dia, {primeiro_nome}!" must not become "bom dia,!"; the text starts in upper case.
        val cfg = on.copy(followLink = "L", followText = "{saudacao}, {primeiro_nome}! Grupo: {link}")
        assertEquals("Bom dia! Grupo: L", Engine.followText(cfg, msg("x", sender = "+55 51 8544-7212"), monday10, 1))
        assertEquals("Bom dia, Ana! Grupo: L", Engine.followText(cfg, msg("x", sender = "Ana Lima"), monday10, 1))
        // A FreteBras notice listing several routes is not a freight question.
        val sheet = listOf(listOf("x", ""), listOf("Ponta Grossa/PR para Rio de Janeiro/RJ", "a"), listOf("Serra/ES para Caratinga/MG", "b"))
        assertEquals(null, Engine.freteLivre("Fretes não publicados: 1. Ponta Grossa - PR >> Rio de Janeiro - RJ 2. Serra - ES >> Caratinga - MG", sheet))
        assertEquals(listOf("a"), Engine.freteLivre("Ponta Grossa para Rio de Janeiro", sheet)!!.messages)
    }

    @Test fun problemas() {
        val now = 100 * 3_600_000L
        val min = 60_000L
        val ev = listOf(
            LogEntry(now - 1 * min, "Frete Bras - Publicações", "Fretes não publicados", "Respondido (Fretes (texto livre))"),
            LogEntry(now - 2 * min, "Frete Bras - Publicações", "Fretes não publicados", "Respondido (Fretes (texto livre))"),
            LogEntry(now - 3 * min, "Frete Bras - Publicações", "Fretes não publicados", "Respondido (Fretes (texto livre))"),
            LogEntry(now - 4 * min, "Johon", "Que droga", "Nenhuma regra serviu"),
            LogEntry(now - 5 * min, "Ana", "tem carga pra Cuiabá?", "Nenhuma regra serviu", listOf("ℹ️ Frete escrito: não é pedido de frete")),
        )
        val hist = listOf(HistoryEntry(now - 6 * min, "WhatsApp", "Johon", false, "oi", "❌ *FRETE INDISPONÍVEL*", "Fretes", listOf("✅ passo")))
        val cfg = Config(freteOn = true, freteLivreOn = true, sheetOn = true, sheetUrl = "u", followOn = true)
        val p = Problems.find(cfg, ev, hist, State(), emptyList(), "", now, 0, now)
        val titles = p.map { it.title }
        assertTrue(titles.toString(), p.first().level == 0 && p.first().fix == "exclude:Frete Bras - Publicações")
        assertTrue(titles.toString(), titles.any { it.startsWith("Johon respondeu \"Que droga\"") })
        assertTrue(titles.toString(), titles.any { "tem carga pra Cuiabá?" in it })
        assertTrue(titles.toString(), titles.any { it.startsWith("Link do grupo ligado sem link") })
        // Already in the exceptions: no longer a problem.
        val cfg2 = cfg.copy(contactsOn = true, contactsMode = Audience.EXCEPT_LIST, contactsList = "Frete Bras - Publicações", followLink = "x")
        assertTrue(Problems.find(cfg2, ev, hist, State(), emptyList(), "", now, 0, now).none { it.fix?.startsWith("exclude:") == true })
        assertTrue(Problems.summary(ev, hist, now)[0].startsWith("ℹ️ Mensagens recebidas: 5 · respondidas: 3 · sem resposta: 2"))
    }

    @Test fun novidadeNaoEPedido() {
        val sheet = listOf(listOf("x", ""), listOf("Goiânia/GO para Rio Verde/GO", "carga rio verde • Coleta: Ter 20/10 08:00"))
        val cfg = on.copy(freteOn = true, freteLivreOn = true, freteNotFound = "indisponível")
        val rule = Rule(match = Match.ALL, enabled = false)
        // Everaldo, 18/10 13:49: telling news, not asking.
        val d = run(msg("Fechei uma carga c kasia para rio verde ai"), listOf(rule), cfg, consulta = sheet)
        assertTrue(d.replies.isEmpty() && d.trace.any { "\"fechei\"" in it })
        // Even without the blocking word, one city in a long sentence needs a request cue.
        assertEquals(null, Engine.freteLivre("Combinei com a kasia uma carga para rio verde ai", sheet))
        assertEquals(listOf("carga rio verde • Coleta: Ter 20/10 08:00"), Engine.freteLivre("boa tarde, tem alguma carga para rio verde?", sheet)!!.messages)
        assertEquals(listOf("carga rio verde • Coleta: Ter 20/10 08:00"), run(msg("Goiânia para Rio Verde"), listOf(rule), cfg, consulta = sheet).replies)
        assertEquals(null, Engine.freteBlocked("tem carga pra Rio Verde?", cfg.freteBlock))
    }
}
