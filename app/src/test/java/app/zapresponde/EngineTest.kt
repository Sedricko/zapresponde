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
        assertTrue(run(msg("tem algo pra Brasilia?"), listOf(rule), cfg, consulta = sheet).replies[0].startsWith("Achei 2 cargas para BRASILIA/DF"))
        assertEquals(listOf("regra"), run(msg("Goiânia para Anápolis"), listOf(rule), cfg.copy(freteLivreOn = false), consulta = sheet).replies)
        val tr = run(msg("Goiânia para Anápolis"), listOf(rule), cfg, consulta = sheet).trace
        assertTrue(tr.toString(), tr.any { "Frete escrito: entendi GOIANIA/GO → ANAPOLIS/GO, 1 carga" in it })
        val tr2 = run(msg("oi"), listOf(rule.copy(name = "Oi", match = Match.CONTAINS, keywords = "tchau"), rule.copy(name = "Tudo")), cfg, consulta = sheet).trace
        assertTrue(tr2.toString(), tr2.any { it.startsWith("❌ Regra Oi: não casou (Contém: tchau)") } && tr2.last().startsWith("✅ Regra Tudo: casou"))
        assertTrue(Assistant.diagnosisPrompt("REL", cfg.copy(aiKey = "SEGREDO"), listOf(rule), "").let { "REL" in it && "SEGREDO" !in it })
    }
}
