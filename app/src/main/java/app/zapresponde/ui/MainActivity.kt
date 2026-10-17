package app.zapresponde.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.zapresponde.Config
import app.zapresponde.Engine
import app.zapresponde.Freq
import app.zapresponde.Groups
import app.zapresponde.Incoming
import app.zapresponde.Kind
import app.zapresponde.Match
import app.zapresponde.ReplyMode
import app.zapresponde.ReplyService
import app.zapresponde.Rule
import app.zapresponde.Sheets
import app.zapresponde.Store
import app.zapresponde.Who
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Sheets.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            MaterialTheme(if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private fun cfg(f: Config.() -> Config) = Store.setConfig(Store.config.f())

@Composable
fun App() {
    var screen by rememberSaveable { mutableStateOf("home") }
    var ruleId by rememberSaveable { mutableStateOf("") }
    BackHandler(screen != "home") { screen = if (screen == "rule") "rules" else "home" }
    val go: (String) -> Unit = { screen = it }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (screen != "home") TextButton({ screen = if (screen == "rule") "rules" else "home" }) { Text("← Voltar") }
        when (screen) {
            "home" -> Home(go)
            "rules" -> RulesScreen { ruleId = it; screen = "rule" }
            "rule" -> RuleEdit(ruleId) { screen = "rules" }
            "sheet" -> SheetScreen()
            "welcome" -> WelcomeScreen()
            "delay" -> DelayScreen()
            "schedule" -> ScheduleScreen()
            "header" -> HeaderScreen()
            "form" -> FormScreen()
            "takeover" -> TakeoverScreen()
            "loop" -> LoopScreen()
            "test" -> TestScreen()
            "history" -> HistoryScreen()
            "help" -> HelpScreen()
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun Home(go: (String) -> Unit) {
    val c = Store.config
    val ctx = LocalContext.current
    val access = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    Text("ZapResponde", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            SwitchRow(
                if (c.enabled) "Respostas automáticas LIGADAS" else "Respostas automáticas desligadas", c.enabled,
            ) { on -> cfg { copy(enabled = on) } }
            if (!access) {
                Text("Falta liberar o acesso às notificações para o app conseguir responder.", color = MaterialTheme.colorScheme.error)
                Button({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Liberar acesso") }
            }
        }
    }
    val n = Store.rules.count { it.enabled }
    FeatureCard("Regras", "$n regras ativas no app" + if (c.sheetOn) " + ${Store.sheet.rules.count { it.enabled }} da planilha" else "", null) { go("rules") }
    FeatureCard("Google Sheets", "Respostas, consulta, pedidos e histórico na planilha", c.sheetOn, { on -> cfg { copy(sheetOn = on) }; Sheets.schedule(ctx) }) { go("sheet") }
    FeatureCard("Boas-vindas", "Mensagem no primeiro contato", c.welcomeOn, { on -> cfg { copy(welcomeOn = on) } }) { go("welcome") }
    FeatureCard("Pedido pela conversa", "Faz perguntas e grava na aba Pedidos", c.formOn, { on -> cfg { copy(formOn = on) } }) { go("form") }
    FeatureCard("Atraso humano", "Espera entre ${c.delayMinSec}s e ${c.delayMaxSec}s antes de responder", c.delayOn, { on -> cfg { copy(delayOn = on) } }) { go("delay") }
    FeatureCard("Agenda", "Só responde em certos horários: ${c.scheduleHours}", c.scheduleOn, { on -> cfg { copy(scheduleOn = on) } }) { go("schedule") }
    FeatureCard("Cabeçalho e rodapé", "Texto fixo antes e depois das respostas", c.headerFooterOn, { on -> cfg { copy(headerFooterOn = on) } }) { go("header") }
    FeatureCard("Pausa quando você assume", "Para de responder por ${c.takeoverMin} min se você abrir a conversa", c.takeoverOn, { on -> cfg { copy(takeoverOn = on) } }) { go("takeover") }
    FeatureCard("Proteção contra loop", "Pausa a conversa se outro robô ficar respondendo", c.loopGuardOn, { on -> cfg { copy(loopGuardOn = on) } }) { go("loop") }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedButton({ go("test") }, Modifier.weight(1f)) { Text("Testar") }
        OutlinedButton({ go("history") }, Modifier.weight(1f).padding(horizontal = 4.dp)) { Text("Histórico") }
        OutlinedButton({ go("help") }, Modifier.weight(1f)) { Text("Não funciona?") }
    }
}

@Composable
fun RulesScreen(edit: (String) -> Unit) {
    Text("Regras", style = MaterialTheme.typography.headlineSmall)
    Text("Testadas de cima para baixo. A primeira que servir responde.", style = MaterialTheme.typography.bodySmall)
    val rules = Store.rules
    rules.forEachIndexed { i, r ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                SwitchRow(r.name, r.enabled, "${r.match.label}: ${r.keywords.take(40)}") { on ->
                    Store.setRules(rules.map { if (it.id == r.id) it.copy(enabled = on) else it })
                }
                Row {
                    TextButton({ edit(r.id) }) { Text("Configurar") }
                    TextButton({ if (i > 0) Store.setRules(rules.toMutableList().apply { add(i - 1, removeAt(i)) }) }) { Text("↑") }
                    TextButton({ if (i < rules.lastIndex) Store.setRules(rules.toMutableList().apply { add(i + 1, removeAt(i)) }) }) { Text("↓") }
                }
            }
        }
    }
    Button({ val r = Rule(); Store.setRules(rules + r); edit(r.id) }, Modifier.fillMaxWidth()) { Text("+ Nova regra") }
    if (Store.config.sheetOn && Store.sheet.rules.isNotEmpty()) {
        Section("Da planilha (edite na aba Respostas)")
        Store.sheet.rules.forEach { Text("• ${it.keywords.ifBlank { "(qualquer)" }} → ${it.replies.first().take(50)}") }
    }
}

@Composable
fun RuleEdit(id: String, done: () -> Unit) {
    val rules = Store.rules
    val r = rules.firstOrNull { it.id == id } ?: return
    fun set(f: Rule.() -> Rule) = Store.setRules(rules.map { if (it.id == id) it.f() else it })
    Field("Nome da regra", r.name) { set { copy(name = it) } }
    SwitchRow("Regra ativa", r.enabled) { set { copy(enabled = it) } }
    Choice("Tipo", r.kind, Kind.entries, { it.label }) { set { copy(kind = it) } }

    Section("Quando responder")
    Choice("Comparação", r.match, Match.entries, { it.label }) { set { copy(match = it) } }
    if (r.match != Match.ALL) Field(
        "Palavras-chave", r.keywords, 2,
        when (r.match) {
            Match.WILDCARD -> "* = qualquer coisa, // = ou. Ex.: oi*tudo bem//ola*"
            Match.REGEX -> "Expressão regular"
            else -> "Separe por vírgula. Maiúsculas e acentos são ignorados."
        },
    ) { set { copy(keywords = it) } }
    if (r.match == Match.SIMILAR) NumberField("Semelhança mínima (%)", r.similarity) { set { copy(similarity = it.coerceIn(1, 100)) } }
    Field("Não responder se tiver", r.exclude, help = "Palavras separadas por vírgula") { set { copy(exclude = it) } }

    Section("Resposta")
    if (r.kind == Kind.TEXT) {
        Choice("Modo", r.replyMode, ReplyMode.entries, { it.label }) { set { copy(replyMode = it) } }
        r.replies.forEachIndexed { i, text ->
            Field("Resposta ${i + 1}", text, 3) { v -> set { copy(replies = replies.toMutableList().also { it[i] = v }) } }
        }
        Row {
            TextButton({ set { copy(replies = replies + "") } }) { Text("+ Resposta") }
            if (r.replies.size > 1) TextButton({ set { copy(replies = replies.dropLast(1)) } }) { Text("− Remover última") }
        }
        Text(VARS, style = MaterialTheme.typography.bodySmall)
    } else {
        Text("Procura na aba Consulta uma linha cujo valor da coluna de busca aparece na mensagem.", style = MaterialTheme.typography.bodySmall)
        Field("Coluna de busca", r.searchCol) { set { copy(searchCol = it.take(1).uppercase()) } }
        Field("Coluna da resposta", r.returnCol) { set { copy(returnCol = it.take(1).uppercase()) } }
        Field("Modelo", r.template, 2, "Use {busca} e {resultado}") { set { copy(template = it) } }
        Field("Se não achar", r.fallback, 2) { set { copy(fallback = it) } }
    }

    Section("Para quem")
    Choice("Contatos", r.who, Who.entries, { it.label }) { set { copy(who = it) } }
    if (r.who == Who.LIST || r.who == Who.EXCEPT_LIST) Field("Lista de contatos", r.whoList, 2, "Nomes como aparecem no WhatsApp, separados por vírgula") { set { copy(whoList = it) } }
    Choice("Grupos", r.groups, Groups.entries, { it.label }) { set { copy(groups = it) } }
    if (r.groups == Groups.LIST || r.groups == Groups.EXCEPT_LIST) Field("Lista de grupos", r.groupList, 2) { set { copy(groupList = it) } }

    Section("Frequência por conversa")
    Choice("Responder", r.freq, Freq.entries, { it.label }) { set { copy(freq = it) } }
    if (r.freq == Freq.INTERVAL) NumberField("Minutos entre respostas", r.intervalMin) { set { copy(intervalMin = it) } }
    if (r.freq == Freq.MAX) NumberField("Máximo de respostas", r.maxReplies) { set { copy(maxReplies = it) } }

    Section("Horários")
    Field("Horários", r.hours, help = "Vazio = o dia todo. Ex.: 08:00-12:00, 14:00-18:00") { set { copy(hours = it) } }
    DaysPicker(r.days) { set { copy(days = it) } }

    Spacer(Modifier.height(16.dp))
    OutlinedButton({ Store.setRules(rules.filter { it.id != id }); done() }) { Text("Apagar regra") }
}

const val VARS = "Variáveis: {nome} {primeiro_nome} {mensagem} {data} {hora} {dia_semana} {saudacao} {grupo} {contador}"

@Composable
fun SheetScreen() {
    val c = Store.config
    val ctx = LocalContext.current
    Text("Google Sheets", style = MaterialTheme.typography.headlineSmall)
    SwitchRow("Usar planilha", c.sheetOn) { on -> cfg { copy(sheetOn = on) }; Sheets.schedule(ctx) }
    Text(
        "1. Na planilha, abra Extensões > Apps Script.\n2. Cole o código do arquivo apps-script/Codigo.gs e troque a CHAVE.\n" +
            "3. Implantar > Nova implantação > App da Web, executar como você, acesso: qualquer pessoa.\n4. Cole aqui o link e a chave.",
        style = MaterialTheme.typography.bodySmall,
    )
    Field("Link do Apps Script", c.sheetUrl, help = "Termina em /exec") { cfg { copy(sheetUrl = it.trim()) } }
    Field("Chave", c.sheetKey) { cfg { copy(sheetKey = it) } }
    NumberField("Sincronizar a cada (minutos, mínimo 15)", c.sheetSyncMin) { cfg { copy(sheetSyncMin = it) } }
    SwitchRow("Respostas da aba Respostas", c.sheetRulesOn, "A: palavras, B: resposta, C: comparação, D: quem, E: ativo") { cfg { copy(sheetRulesOn = it) } }
    SwitchRow("Gravar histórico na aba Historico", c.sheetLogOn) { cfg { copy(sheetLogOn = it) } }
    Text("A aba Consulta é usada pelas regras do tipo \"Consulta na planilha\". A aba Pedidos, pela função Pedido pela conversa.", style = MaterialTheme.typography.bodySmall)
    Button({ Sheets.schedule(ctx); Sheets.syncNow(ctx) }, Modifier.padding(top = 8.dp)) { Text("Sincronizar agora") }
    val s = Store.sheet
    if (s.syncedAt > 0) Text("Última sincronização: ${fmt(s.syncedAt)} · ${s.rules.size} respostas · ${maxOf(0, s.consulta.size - 1)} linhas de consulta")
    if (s.error.isNotEmpty()) Text("Erro: ${s.error}", color = MaterialTheme.colorScheme.error)
    if (Store.outbox.historico.isNotEmpty() || Store.outbox.pedidos.isNotEmpty())
        Text("Aguardando envio: ${Store.outbox.historico.size} linhas de histórico, ${Store.outbox.pedidos.size} pedidos")
}

@Composable
fun WelcomeScreen() {
    val c = Store.config
    Text("Boas-vindas", style = MaterialTheme.typography.headlineSmall)
    SwitchRow("Ativar boas-vindas", c.welcomeOn) { cfg { copy(welcomeOn = it) } }
    Field("Mensagem", c.welcomeText, 3) { cfg { copy(welcomeText = it) } }
    SwitchRow("Enviar uma vez por dia", c.welcomeDaily, "Desligado: só na primeira vez que a pessoa fala com você") { cfg { copy(welcomeDaily = it) } }
    Text("Se uma regra também servir, a resposta dela vai logo depois. $VARS", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun FormScreen() {
    val c = Store.config
    Text("Pedido pela conversa", style = MaterialTheme.typography.headlineSmall)
    SwitchRow("Ativar pedido", c.formOn) { cfg { copy(formOn = it) } }
    Field("Palavras que iniciam o pedido", c.formTrigger) { cfg { copy(formTrigger = it) } }
    Field("Perguntas (uma por linha)", c.formQuestions, 5) { cfg { copy(formQuestions = it) } }
    Field("Mensagem final", c.formDone, 2) { cfg { copy(formDone = it) } }
    Field("Palavra para cancelar", c.formCancel) { cfg { copy(formCancel = it) } }
    Text("As respostas vão para a aba Pedidos da planilha (Google Sheets precisa estar ligado).", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun DelayScreen() {
    val c = Store.config
    Text("Atraso humano", style = MaterialTheme.typography.headlineSmall)
    SwitchRow("Ativar atraso", c.delayOn) { cfg { copy(delayOn = it) } }
    NumberField("Mínimo (segundos)", c.delayMinSec) { cfg { copy(delayMinSec = it) } }
    NumberField("Máximo (segundos)", c.delayMaxSec) { cfg { copy(delayMaxSec = it) } }
    Text("Se a pessoa mandar outra mensagem durante a espera, a resposta pendente é trocada pela nova.", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun ScheduleScreen() {
    val c = Store.config
    Text("Agenda", style = MaterialTheme.typography.headlineSmall)
    SwitchRow("Ativar agenda", c.scheduleOn, "Fora desses horários o app não responde") { cfg { copy(scheduleOn = it) } }
    Field("Horários", c.scheduleHours, help = "Ex.: 18:00-08:00 (atravessa a meia-noite) ou 08:00-12:00, 14:00-18:00") { cfg { copy(scheduleHours = it) } }
    DaysPicker(c.scheduleDays) { cfg { copy(scheduleDays = it) } }
}

@Composable
fun HeaderScreen() {
    val c = Store.config
    Text("Cabeçalho e rodapé", style = MaterialTheme.typography.headlineSmall)
    SwitchRow("Ativar cabeçalho e rodapé", c.headerFooterOn) { cfg { copy(headerFooterOn = it) } }
    Field("Cabeçalho", c.header, 2) { cfg { copy(header = it) } }
    Field("Rodapé", c.footer, 2) { cfg { copy(footer = it) } }
}

@Composable
fun TakeoverScreen() {
    val c = Store.config
    Text("Pausa quando você assume", style = MaterialTheme.typography.headlineSmall)
    SwitchRow("Ativar pausa", c.takeoverOn) { cfg { copy(takeoverOn = it) } }
    NumberField("Minutos de pausa", c.takeoverMin) { cfg { copy(takeoverMin = it) } }
    Text("Quando você toca na notificação ou abre a conversa no WhatsApp, o robô para de responder naquela conversa.", style = MaterialTheme.typography.bodySmall)
    var tick by remember { mutableStateOf(0) }
    val paused = Store.state.chats.filter { tick >= 0 }.filter { it.value.pauseUntil > System.currentTimeMillis() }
    if (paused.isNotEmpty()) {
        Section("Conversas pausadas agora")
        paused.forEach { (chat, st) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$chat até ${fmt(st.pauseUntil)}", Modifier.weight(1f))
                TextButton({ Store.pauseChat(chat, 0); tick++ }) { Text("Retomar") }
            }
        }
    }
}

@Composable
fun LoopScreen() {
    val c = Store.config
    Text("Proteção contra loop", style = MaterialTheme.typography.headlineSmall)
    SwitchRow("Ativar proteção", c.loopGuardOn) { cfg { copy(loopGuardOn = it) } }
    Text("Se o app responder 5 vezes à mesma conversa em 2 minutos, ela fica pausada por 30 minutos.", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun TestScreen() {
    var name by remember { mutableStateOf("Maria Silva") }
    var text by remember { mutableStateOf("Oi, bom dia") }
    var group by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Text("Testar", style = MaterialTheme.typography.headlineSmall)
    Text("Simula uma mensagem sem enviar nada. Ignora o botão liga/desliga.", style = MaterialTheme.typography.bodySmall)
    Field("Nome de quem manda", name) { name = it }
    Field("Grupo (vazio = conversa individual)", group) { group = it }
    Field("Mensagem", text, 2) { text = it }
    Button({
        val msg = Incoming(group.ifBlank { name }, name, text, group.isNotBlank())
        val d = Engine.decide(
            msg, LocalDateTime.now(), System.currentTimeMillis(), Store.config.copy(enabled = true),
            Store.allRules(), Store.sheet.consulta, Store.stateCopy(),
        )
        result = if (d.replies.isEmpty()) "Sem resposta: ${d.reason}"
        else "Regra: ${d.rule}\nAtraso: ${d.delayMs / 1000}s\n\n" + d.replies.joinToString("\n\n")
    }) { Text("Testar") }
    if (result.isNotEmpty()) Card(Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(result, Modifier.padding(12.dp)) }
}

@Composable
fun HistoryScreen() {
    Text("Histórico", style = MaterialTheme.typography.headlineSmall)
    val h = Store.history
    val today = h.count { it.time > System.currentTimeMillis() - 86_400_000 }
    Text("${h.size} respostas guardadas · $today nas últimas 24h")
    h.take(100).forEach { e ->
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("${fmt(e.time)} · ${e.chat} · ${e.rule}", style = MaterialTheme.typography.bodySmall)
        Text("← ${e.text}")
        Text("→ ${e.reply}")
    }
}

@Composable
fun HelpScreen() {
    val ctx = LocalContext.current
    val access = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    val battery = (ctx.getSystemService(PowerManager::class.java)).isIgnoringBatteryOptimizations(ctx.packageName)
    val notifs = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    Text("Não está funcionando?", style = MaterialTheme.typography.headlineSmall)
    Check("Acesso às notificações", access) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    Check("Sem economia de bateria para o app", battery) {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
    }
    Check("Notificações do ZapResponde", notifs) {
        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
    }
    Check("Respostas automáticas ligadas", Store.config.enabled) { cfg { copy(enabled = true) } }
    OutlinedButton({
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.whatsapp")))
    }, Modifier.padding(top = 8.dp)) { Text("Abrir notificações do WhatsApp") }
    OutlinedButton({
        // Re-binding the listener fixes the common "stopped replying" case after updates.
        val cn = ComponentName(ctx, ReplyService::class.java)
        android.service.notification.NotificationListenerService.requestRebind(cn)
    }) { Text("Reiniciar o serviço de respostas") }
    Text(
        "• O app só responde quando aparece notificação. Com a conversa aberta ou silenciada, não responde.\n" +
            "• Xiaomi, Samsung e Motorola: libere \"início automático\" e tire o app da economia de bateria.\n" +
            "• Android 15+: se aparecer aviso de notificação sensível, permita o acesso nas configurações de notificação.\n" +
            "• Se nada resolver, desligue e ligue o acesso às notificações ou reinicie o celular.",
        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun Check(label: String, ok: Boolean, fix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text((if (ok) "✓ " else "✗ ") + label, Modifier.weight(1f), color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        if (!ok) TextButton(fix) { Text("Corrigir") }
    }
}

private fun fmt(ms: Long) = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
