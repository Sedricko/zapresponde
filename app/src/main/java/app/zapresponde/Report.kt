package app.zapresponde

import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

/* The diagnostic report as text (no screen code): it is shown, shared, saved and read by the AI. */

val AUDIENCE = mapOf(
    Audience.ALL to "Todos",
    Audience.LIST to "Só minha lista de contatos",
    Audience.EXCEPT_LIST to "Todos, exceto minha lista",
    Audience.EXCEPT_PHONE to "Exceto meus contatos do telefone",
)

/** "Funcionamento interno": errors, rule and setting conflicts (Problems.internal) plus the service's health and versions. */
internal fun internalLines(ctx: android.content.Context): List<String> {
    val now = System.currentTimeMillis()
    val out = synchronized(Store) { Problems.internal(Store.config, Store.allRules(), Store.events, Store.history, now) }.toMutableList()
    val day = Store.events.filter { now - it.time < 24 * 3_600_000L }
    fun version(pkg: String) = runCatching { ctx.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull() ?: "não instalado"
    val disconnects = day.count { it.result.startsWith("Android desconectou") }
    val lost = day.count { it.result.contains("perdeu o botão Responder") }
    val box = Store.outbox
    val queued = box.historico.size + box.pedidos.size + box.leads.size
    out += "Saúde do serviço (24 h)"
    out += "  ${if (disconnects == 0) "✅" else "⚠️"} O Android desligou o serviço $disconnects vez(es)"
    out += "  ${if (lost == 0) "✅" else "⚠️"} Envios perdidos por reinício do app (botão Responder esquecido): $lost"
    out += "  ${if (queued == 0) "✅" else "⚠️"} Fila para a planilha: $queued linha(s) esperando" +
        if (queued > 0) " (Historico ${box.historico.size}, Pedidos ${box.pedidos.size}, Leads ${box.leads.size})" else ""
    out += if (Store.sheet.syncedAt == 0L) "  ⚠️ A planilha nunca foi lida" else
        "  ${if (Store.sheet.error.isEmpty()) "✅" else "⚠️"} Última leitura da planilha: ${fmt(Store.sheet.syncedAt)}" +
            if (Store.sheet.error.isNotEmpty()) " · último erro: ${Store.sheet.error.take(120)}" else ""
    out += "  ℹ️ Guardado no celular: ${Store.history.size} respostas, ${Store.events.size} registros, ${Store.state.chats.size} conversas, ${Store.state.waiting.size} pedidos esperando"
    out += "  ℹ️ Na memória agora: ${ReplyService.instance?.approvalsPending() ?: 0} aprovação(ões) pendente(s)"
    val week = Store.traceLog.filter { now - it.time < 24 * 3_600_000L }
    val decisions = week.filter { it.cat == "DECISAO" }.mapNotNull { Regex(""" em (\d+) ms""").find(it.msg)?.groupValues?.get(1)?.toLongOrNull() }
    out += "Monitoramento (24 h)"
    out += "  ℹ️ " + week.groupingBy { it.cat }.eachCount().entries.sortedBy { it.key }.joinToString { "${it.key} ${it.value}" }.ifEmpty { "nada registrado" }
    if (decisions.isNotEmpty()) out += "  ${if (decisions.max() > 500) "⚠️" else "✅"} Decisões: ${decisions.size}, média ${decisions.average().toLong()} ms, mais lenta ${decisions.max()} ms"
    val sends = week.filter { it.cat == "ENVIO" }
    if (sends.any { "RECUSADA" in it.msg }) out += "  ⚠️ Envios recusados pelo WhatsApp: ${sends.count { "RECUSADA" in it.msg }}"
    val sheetFails = week.count { it.cat == "PLANILHA" && "FALHOU" in it.msg }
    out += "  ${if (sheetFails == 0) "✅" else "⚠️"} Planilha: ${week.count { it.cat == "PLANILHA" && "ok em" in it.msg }} leitura(s) ok, $sheetFails falha(s)"
    val ai = week.filter { it.cat == "IA" && "Gemini" in it.msg }
    if (ai.isNotEmpty()) out += "  ℹ️ IA: ${ai.count { ": ok em" in it.msg }} chamada(s) ok, ${ai.count { ": ok em" !in it.msg }} com erro ou nova tentativa"
    val changes = week.filter { it.cat == "CONFIG" || it.cat == "REGRAS" }
    if (changes.isNotEmpty()) out += "  ℹ️ Mudanças: ${changes.size} (" + changes.groupingBy { it.msg.substringAfter("(").substringBefore(")") }.eachCount().entries.joinToString { "${it.key} ${it.value}" } + ")"
    out += "Versões"
    out += "  ℹ️ App ${version(ctx.packageName)} · script da planilha ${Sheets.scriptVersion.ifEmpty { "?" }} · Android ${android.os.Build.VERSION.RELEASE}"
    out += "  ℹ️ WhatsApp ${version("com.whatsapp")} · WhatsApp Business ${version("com.whatsapp.w4b")}"
    return out
}

internal fun currentProblems() = synchronized(Store) {
    Problems.find(
        Store.config, Store.events, Store.history, Store.state, Store.sheet.consulta, Store.sheet.error, Store.sheet.syncedAt,
        ReplyService.instance?.approvalsPending() ?: 0, System.currentTimeMillis(),
    )
}

internal val TRACE_TIME = SimpleDateFormat("dd/MM HH:mm:ss.SSS", Locale("pt", "BR"))

/** The internal monitoring as text, newest first: all lines, or the [n] most recent. */
internal fun traceText(n: Int?): String = (if (n == null) Store.traceLog else Store.traceLog.take(n))
    .joinToString("\n") { "${synchronized(TRACE_TIME) { TRACE_TIME.format(Date(it.time)) }} [${it.cat}] ${it.msg}" }

/** Plain-text report: every setting that can stop a reply, the sheet and freight self-tests, and the last messages seen. */
internal fun diagnose(ctx: android.content.Context): String {
    val c = Store.config
    val now = System.currentTimeMillis()
    val l = mutableListOf("RELATÓRIO ZAPRESPONDE · ${fmt(now)}", "App versão " + ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName, "")
    fun ok(b: Boolean, yes: String, no: String) { l += if (b) "✅ $yes" else "❌ $no" }
    fun warn(b: Boolean, text: String) { if (b) l += "⚠️ $text" }

    l += "— Celular"
    ok(NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName), "Acesso às notificações liberado", "Acesso às notificações DESLIGADO: o app não vê nenhuma mensagem")
    ok(ReplyService.instance != null, "Serviço de respostas rodando", "Serviço de respostas parado: use \"Reiniciar o serviço de respostas\" ou reinicie o celular")
    ok(Health.connected(ctx), "Serviço de notificações conectado",
        "Serviço de notificações DESCONECTADO: use \"Reiniciar o serviço de respostas\" ou reinicie o celular")
    ok(ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName), "Sem economia de bateria", "Economia de bateria ativa: o Android pode parar o app")
    ok(NotificationManagerCompat.from(ctx).areNotificationsEnabled(), "Notificações do ZapResponde liberadas", "Notificações do ZapResponde bloqueadas: alertas e aprovação não aparecem")
    l += Health.report(ctx)
    val last = Store.events.firstOrNull()
    if (last == null) l += "⚠️ Nenhuma mensagem do WhatsApp chegou ao app ainda"
    else warn(now - last.time > 6 * 3_600_000L, "Última mensagem vista há mais de 6 h (${fmt(last.time)}). Com o WhatsApp Web aberto, o celular não mostra notificações e o app não vê as mensagens.")

    l += ""; l += "— Configurações"
    ok(c.enabled, "Respostas automáticas ligadas", "Respostas automáticas DESLIGADAS (botão da tela Início)")
    warn(c.scheduleOn && !Engine.inWindow(c.scheduleHours, c.scheduleDays, LocalDateTime.now()), "Agenda ligada e agora está fora do horário (${c.scheduleHours})")
    warn(c.contactsOn && c.contactsMode != Audience.ALL, "Filtro de contatos ligado: ${AUDIENCE[c.contactsMode]}")
    warn(c.contactsOn && !c.groupsOn, "Respostas em grupos desligadas")
    warn(c.approvalOn, "Aprovar antes de enviar ligado: nada sai sem você tocar em Enviar")
    val paused = synchronized(Store) { Store.state.chats.filter { it.value.pauseUntil > now }.toMap() }
    warn(paused.isNotEmpty(), "Conversas pausadas: " + paused.entries.joinToString { "${it.key} (até ${fmt(it.value.pauseUntil)})" })
    val follows = synchronized(Store) { Store.state.chats.filter { it.value.followAt > 0 }.toMap() }
    if (follows.isNotEmpty()) l += "ℹ️ Acompanhamentos marcados: " + follows.entries.joinToString { "${it.key} (às ${fmt(it.value.followAt)})" }
    warn(c.followOn && c.followLink.isBlank(), "Acompanhamento ligado sem link do grupo: nada será enviado")
    val waits = synchronized(Store) { Store.state.waiting.toList() }
    if (waits.isNotEmpty()) l += "ℹ️ Pedidos esperando carga: " + waits.joinToString { "${it.chat}: ${it.label} (desde ${fmt(it.time)})" }
    l += "ℹ️ Regras ativas: ${Store.allRules().count { it.enabled }} (app ${Store.rules.count { it.enabled }}, planilha ${Store.sheet.rules.count { it.enabled }})"
    fun on(b: Boolean) = if (b) "ligado" else "desligado"
    l += "ℹ️ Recursos: boas-vindas ${on(c.welcomeOn)}, pedido ${on(c.formOn)}, fretes ${on(c.freteOn)}, leads ${on(c.leadsOn)}, " +
        "alerta ${on(c.alertOn)}, pausa ao assumir ${on(c.takeoverOn)} (${c.takeoverMin} min, ao ler: ${on(c.takeoverOnRead)}), atraso ${on(c.delayOn)}, frete escrito ${on(c.freteLivreOn)}, acompanhamento ${on(c.followOn)} (${c.followMin} min), outra carga ${on(c.altOn)}, avisar carga ${on(c.waitOn)} (${c.waitMode.label}), diagnóstico pela IA ${on(c.aiDiagOn)}"
    if (Store.sheet.error.isNotEmpty()) l += "❌ Último erro de sincronização em segundo plano: ${Store.sheet.error}"

    l += ""; l += "— Planilha"
    if (!c.sheetOn || c.sheetUrl.isBlank()) l += "⚠️ Planilha não conectada"
    else runCatching { Sheets.refresh(c) }.fold(
        {
            l += "✅ Planilha conectada · script ${Sheets.scriptVersion}"
            l += "ℹ️ ${Store.sheet.rules.size} respostas na aba Respostas · ${maxOf(0, Store.sheet.consulta.size - 1)} linhas de consulta/cargas"
        },
        { l += "❌ Planilha não respondeu: ${it.message}" },
    )
    val box = Store.outbox
    warn(box.historico.size + box.pedidos.size + box.leads.size > 0, "Aguardando envio para a planilha: ${box.historico.size} histórico, ${box.pedidos.size} pedidos, ${box.leads.size} leads")

    l += ""; l += "— Fretes"
    if (!c.freteOn) l += "⚠️ Respostas de frete DESLIGADO: pedidos de frete não são respondidos"
    else when (val t = Engine.freteSelfTest(Store.sheet.consulta)) {
        null -> l += "❌ Nenhuma rota \"Origem/UF para Destino/UF\" na coluna A da planilha de cargas"
        else -> ok(t.second, "Teste com a rota \"${t.first}\": carga encontrada", "Teste com a rota \"${t.first}\": carga NÃO encontrada")
    }

    l += ""; l += "— Últimas mensagens recebidas"
    if (Store.events.isEmpty()) l += "(nenhuma)"
    Store.events.take(15).forEachIndexed { i, e ->
        l += "${fmt(e.time)} · ${e.chat}: \"${e.text.take(50).replace('\n', ' ')}\" → ${e.result}"
        // The 5 newest, and any message that went wrong, also show the path of the decision.
        val wrong = !e.result.startsWith("Respondido") && !e.result.startsWith("Mensagem é uma resposta") && !e.result.startsWith("Serviço")
        if (i < 5 || wrong) e.trace.forEach { l += "      $it" }
    }
    val causa = when {
        !NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) ->
            "O acesso às notificações está desligado. Se o botão estiver cinza: Android > Apps > ZapResponde > ⋮ > Permitir configurações restritas."
        !Health.connected(ctx) -> "O Android desconectou o serviço de notificações (bateria ou início automático)."
        !c.enabled -> "As respostas automáticas estão desligadas."
        Health.get(ctx, "notifAt") == 0L -> "Nenhuma notificação do WhatsApp chegou ao app."
        now - Health.get(ctx, "notifAt") > 6 * 3_600_000L ->
            "Faz horas sem notificação do WhatsApp. Verifique WhatsApp Web aberto, conversas silenciadas ou notificações do WhatsApp desligadas."
        else -> null
    }
    // Problems first: "healthy" only when there is nothing to fix, not just when the phone side is fine.
    val probs = currentProblems()
    val top = mutableListOf(
        when {
            causa != null -> "➡️ CAUSA MAIS PROVÁVEL: $causa"
            probs.any { it.level <= 1 } -> "➡️ ${probs.count { it.level <= 1 }} problema(s) para resolver; veja abaixo."
            else -> "➡️ Tudo certo: nenhum problema nas últimas 24 h."
        },
        "",
    )
    if (probs.isNotEmpty()) {
        top += "— Problemas encontrados"
        probs.forEach { pr -> top += "${pr.icon} ${pr.title}"; pr.detail.lines().forEach { top += "      $it" } }
        top += ""
    }
    top += "— Últimas 24 h"
    top += Problems.summary(Store.events, Store.history, now)
    top += ""
    top += "— Funcionamento interno"
    top += internalLines(ctx)
    top += ""
    l.addAll(2, top)
    return l.joinToString("\n")
}

internal fun fmt(ms: Long) = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
