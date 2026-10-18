package app.zapresponde.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.zapresponde.Assistant
import app.zapresponde.Audience
import app.zapresponde.Config
import app.zapresponde.Contacts
import app.zapresponde.Engine
import app.zapresponde.Health
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
        setContent { ZapTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

// Bumped on every return to the app so permission checks refresh after visiting system settings.
private var resumeTick by mutableStateOf(0)

private fun cfg(f: Config.() -> Config) = Store.updateConfig(Store.config.f())

private data class Tab(val key: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Início", Icons.Default.Home),
    Tab("rules", "Regras", Icons.Default.Build),
    Tab("sheet", "Planilha", Icons.AutoMirrored.Filled.List),
    Tab("history", "Histórico", Icons.Default.Notifications),
    Tab("more", "Ajustes", Icons.Default.Settings),
)

private val TITLES = mapOf(
    "welcome" to "Boas-vindas", "form" to "Pedido pela conversa", "delay" to "Atraso humano",
    "schedule" to "Agenda", "header" to "Cabeçalho e rodapé", "takeover" to "Pausa quando você assume",
    "loop" to "Proteção contra loop", "contacts" to "Contatos", "backup" to "Backup", "approval" to "Aprovar antes de enviar", "frete" to "Respostas de frete", "follow" to "Link do grupo depois da conversa", "alt" to "Oferecer outra carga", "wait" to "Avisar quando a carga aparecer", "ai" to "Assistente de IA", "test" to "Testar respostas", "help" to "Diagnóstico",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var tab by rememberSaveable { mutableStateOf("home") }
    // Detail screen on top of the tabs: a feature key or "rule:<id>".
    var sub by rememberSaveable { mutableStateOf("") }
    var newRule by remember { mutableStateOf(false) }
    BackHandler(sub.isNotEmpty() || tab != "home") { if (sub.isNotEmpty()) sub = "" else tab = "home" }
    val open: (String) -> Unit = { sub = it }
    val bar = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)

    if (sub.isNotEmpty()) {
        val ruleId = sub.removePrefix("rule:").takeIf { sub.startsWith("rule:") }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (ruleId != null) "Editar regra" else TITLES[sub].orEmpty()) }, colors = bar,
                    navigationIcon = { IconButton({ sub = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                )
            },
        ) { pad ->
            Page(pad) {
                if (ruleId != null) RuleEdit(ruleId) { sub = "" }
                else when (sub) {
                    "welcome" -> WelcomeScreen()
                    "form" -> FormScreen()
                    "delay" -> DelayScreen()
                    "schedule" -> ScheduleScreen()
                    "header" -> HeaderScreen()
                    "takeover" -> TakeoverScreen()
                    "loop" -> LoopScreen()
                    "contacts" -> ContactsScreen()
                    "backup" -> BackupScreen()
                    "approval" -> ApprovalScreen()
                    "frete" -> FreteScreen()
                    "follow" -> FollowScreen()
                    "alt" -> AltScreen()
                    "wait" -> WaitScreen()
                    "test" -> TestScreen()
                    "ai" -> AssistantScreen()
                    "help" -> HelpScreen()
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (tab == "home") "ZapResponde" else TABS.first { it.key == tab }.label,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = bar,
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEach { t ->
                    NavigationBarItem(tab == t.key, { tab = t.key }, { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
        },
        floatingActionButton = {
            if (tab == "rules") ExtendedFloatingActionButton(
                onClick = { newRule = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nova regra") },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        Page(pad) {
            when (tab) {
                "home" -> Home(open, { tab = it }, { newRule = true })
                "rules" -> RulesTab(open)
                "sheet" -> SheetScreen()
                "history" -> HistoryScreen()
                "more" -> MoreTab(open)
            }
        }
    }
    if (newRule) NewRuleDialog({ newRule = false }) { r ->
        newRule = false
        Store.updateRules(Store.rules + r)
        tab = "rules"; sub = "rule:${r.id}"
    }
}

@Composable
private fun Page(pad: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        content()
        Spacer(Modifier.height(96.dp))
    }
}

// ---------- Início ----------

@Composable
fun Home(open: (String) -> Unit, goTab: (String) -> Unit, newRule: () -> Unit) {
    val c = Store.config
    val ctx = LocalContext.current
    val access = resumeTick >= 0 && NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    val connected = resumeTick >= 0 && Health.connected(ctx)
    val degraded = c.enabled && access && !connected
    val bg by animateColorAsState(if (c.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "bg")
    val fg = if (c.enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Card(
        Modifier.fillMaxWidth().padding(top = 4.dp), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (degraded) "Ligado, sem conexão" else if (c.enabled) "Respondendo" else "Pausado", style = MaterialTheme.typography.headlineSmall, color = fg, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        degraded -> "O serviço de notificações está desconectado. Abra Ajustes > Diagnóstico."
                        c.enabled -> "As mensagens do WhatsApp estão sendo respondidas"
                        else -> "Toque no botão para começar a responder"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f),
                )
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f))
                    .border(3.dp, fg.copy(alpha = 0.6f), CircleShape).clickable { cfg { copy(enabled = !enabled) } },
                contentAlignment = Alignment.Center,
            ) { Text(if (c.enabled) "ON" else "OFF", color = fg, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) }
        }
    }

    if (!access) Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Libere o acesso às notificações para o app conseguir responder.")
                Text(
                    "Se o botão \"Liberar\" ficar cinza: Configurações do Android > Apps > ZapResponde > ⋮ > Permitir configurações restritas.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Liberar") }
        }
    }

    val now = System.currentTimeMillis()
    val h = Store.history
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("Hoje", h.count { now - it.time < 86_400_000 }.toString(), Modifier.weight(1f))
        Stat("Total", h.size.toString(), Modifier.weight(1f))
        Stat("Regras ativas", Store.allRules().count { it.enabled }.toString(), Modifier.weight(1f))
    }

    Panel("Atalhos") {
        FeatureRow(Icons.Default.Add, "Criar regra", "Responder automaticamente a uma palavra", null) { newRule() }
        Divider()
        FeatureRow(Icons.Default.PlayArrow, "Testar respostas", "Simule uma mensagem e veja o que seria enviado", null) { open("test") }
        if (c.sheetOn) {
            Divider()
            FeatureRow(Icons.Default.Refresh, "Sincronizar planilha", syncText(), null) { Sheets.syncNow(ctx) }
        }
    }

    Panel("Últimas respostas") {
        if (h.isEmpty()) Hint("Nenhuma resposta enviada ainda.")
        h.take(3).forEachIndexed { i, e ->
            if (i > 0) Divider()
            HistoryItem(e)
        }
        if (h.size > 3) TextButton({ goTab("history") }) { Text("Ver todas") }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Regras ----------

@Composable
fun RulesTab(open: (String) -> Unit) {
    Hint("Testadas de cima para baixo. A primeira que servir responde.")
    val rules = Store.rules
    if (rules.isEmpty()) Panel { Hint("Nenhuma regra ainda. Toque em \"Nova regra\".") }
    rules.forEach { r ->
        Card(
            Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { open("rule:${r.id}") },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(
                    if (r.kind == Kind.LOOKUP) Icons.Default.Search else Icons.AutoMirrored.Filled.Send,
                    if (r.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (r.match == Match.ALL) "Qualquer mensagem" else "${r.match.label}: ${r.keywords}",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "→ " + if (r.kind == Kind.LOOKUP) "consulta na planilha" else r.replies.firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(r.enabled, { on -> Store.updateRules(rules.map { if (it.id == r.id) it.copy(enabled = on) else it }) })
            }
        }
    }
    if (Store.config.sheetOn && Store.sheet.rules.isNotEmpty()) Panel("Da planilha · edite na aba Respostas") {
        Store.sheet.rules.forEachIndexed { i, r ->
            if (i > 0) Divider()
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(r.keywords.ifBlank { "Qualquer mensagem" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("→ " + r.replies.first(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NewRuleDialog(dismiss: () -> Unit, create: (Rule) -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss, confirmButton = {}, dismissButton = { TextButton(dismiss) { Text("Cancelar") } },
        title = { Text("Que tipo de regra?") },
        text = {
            Column {
                FeatureRow(Icons.AutoMirrored.Filled.Send, "Responder a palavras", "Ex.: quando falarem \"preço\", enviar a tabela", null) {
                    create(Rule(name = "Nova resposta"))
                }
                FeatureRow(Icons.Default.Person, "Responder todo mundo", "Ex.: \"Estou ausente, já retorno\"", null) {
                    create(Rule(name = "Ausente", match = Match.ALL, freq = Freq.INTERVAL, intervalMin = 240, replies = listOf("Olá {primeiro_nome}! No momento não posso responder, retorno em breve.")))
                }
                FeatureRow(Icons.Default.Search, "Consultar a planilha", "Ex.: status do pedido pelo código", null) {
                    create(Rule(name = "Consulta", kind = Kind.LOOKUP, keywords = "pedido", template = "Pedido {busca}: {resultado}", fallback = "Não encontrei esse código."))
                }
            }
        },
    )
}

@Composable
fun RuleEdit(id: String, done: () -> Unit) {
    val rules = Store.rules
    val idx = rules.indexOfFirst { it.id == id }
    if (idx < 0) return
    val r = rules[idx]
    fun set(f: Rule.() -> Rule) = Store.updateRules(rules.map { if (it.id == id) it.f() else it })

    Panel {
        Field("Nome da regra", r.name) { set { copy(name = it) } }
        SwitchRow("Regra ativa", r.enabled) { set { copy(enabled = it) } }
    }

    Panel("Quando chegar") {
        Choice("Tipo de mensagem", r.match, Match.entries, { it.label }) { set { copy(match = it) } }
        if (r.match != Match.ALL) Field(
            "Palavras-chave", r.keywords, 2,
            when (r.match) {
                Match.WILDCARD -> "* = qualquer coisa, // = ou. Ex.: oi*tudo bem//ola*"
                Match.REGEX -> "Expressão regular"
                else -> "Separe por vírgula. Maiúsculas e acentos não importam."
            },
        ) { set { copy(keywords = it) } }
    }

    if (r.kind == Kind.TEXT) Panel("Responder com") {
        r.replies.forEachIndexed { i, text ->
            Field(if (r.replies.size > 1) "Resposta ${i + 1}" else "Resposta", text, 3) { v ->
                set { copy(replies = replies.toMutableList().also { it[i] = v }) }
            }
        }
        Row {
            TextButton({ set { copy(replies = replies + "", replyMode = if (replyMode == ReplyMode.SINGLE) ReplyMode.RANDOM else replyMode) } }) { Text("+ Outra resposta") }
            if (r.replies.size > 1) TextButton({ set { copy(replies = replies.dropLast(1)) } }) { Text("Remover última") }
        }
        if (r.replies.size > 1) Choice("Com várias respostas", r.replyMode, ReplyMode.entries, { it.label }) { set { copy(replyMode = it) } }
        Hint(VARS)
    } else Panel("Consulta na planilha") {
        Hint("Procura na aba Consulta uma linha cujo valor da coluna de busca aparece na mensagem.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("Coluna de busca", r.searchCol) { set { copy(searchCol = it.take(1).uppercase()) } } }
            Box(Modifier.weight(1f)) { Field("Coluna da resposta", r.returnCol) { set { copy(returnCol = it.take(1).uppercase()) } } }
        }
        Field("Modelo da resposta", r.template, 2, "Use {busca}, {resultado} ou a letra de qualquer coluna, como {C}. Se várias linhas baterem, responde até 5.") { set { copy(template = it) } }
        Field("Se não encontrar", r.fallback, 2) { set { copy(fallback = it) } }
    }

    Panel {
        Advanced {
            Choice("Tipo de regra", r.kind, Kind.entries, { it.label }) { set { copy(kind = it) } }
            if (r.match == Match.SIMILAR) NumberField("Semelhança mínima", r.similarity, "%") { set { copy(similarity = it.coerceIn(1, 100)) } }
            Field("Não responder se tiver", r.exclude, help = "Palavras separadas por vírgula") { set { copy(exclude = it) } }
            Divider()
            Choice("Contatos", r.who, Who.entries, { it.label }) { set { copy(who = it) } }
            if (r.who == Who.LIST || r.who == Who.EXCEPT_LIST) Field("Lista de contatos", r.whoList, 2, "Nomes como aparecem no WhatsApp, separados por vírgula") { set { copy(whoList = it) } }
            Choice("Grupos", r.groups, Groups.entries, { it.label }) { set { copy(groups = it) } }
            if (r.groups == Groups.LIST || r.groups == Groups.EXCEPT_LIST) Field("Lista de grupos", r.groupList, 2) { set { copy(groupList = it) } }
            Divider()
            Choice("Frequência por conversa", r.freq, Freq.entries, { it.label }) { set { copy(freq = it) } }
            if (r.freq == Freq.INTERVAL) NumberField("Intervalo", r.intervalMin, "min") { set { copy(intervalMin = it) } }
            if (r.freq == Freq.MAX) NumberField("Máximo de respostas", r.maxReplies) { set { copy(maxReplies = it) } }
            Divider()
            Field("Horários", r.hours, help = "Vazio = o dia todo. Ex.: 08:00-12:00, 14:00-18:00") { set { copy(hours = it) } }
            DaysPicker(r.days) { set { copy(days = it) } }
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ if (idx > 0) Store.updateRules(rules.toMutableList().apply { add(idx - 1, removeAt(idx)) }) }, enabled = idx > 0) {
            Icon(Icons.Default.KeyboardArrowUp, null); Text("Subir")
        }
        OutlinedButton({ if (idx < rules.lastIndex) Store.updateRules(rules.toMutableList().apply { add(idx + 1, removeAt(idx)) }) }, enabled = idx < rules.lastIndex) {
            Icon(Icons.Default.KeyboardArrowDown, null); Text("Descer")
        }
        Spacer(Modifier.weight(1f))
        TextButton({ Store.updateRules(rules.filter { it.id != id }); done() }) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            Text("Apagar", color = MaterialTheme.colorScheme.error)
        }
    }
}

const val VARS = "Variáveis: {nome} {primeiro_nome} {mensagem} {data} {hora} {dia_semana} {saudacao} {grupo} {contador}"

// ---------- Planilha ----------

private fun syncText(): String {
    val s = Store.sheet
    return when {
        s.error.isNotEmpty() -> "Erro: ${s.error}"
        s.syncedAt > 0 -> "Sincronizada em ${fmt(s.syncedAt)}"
        else -> "Ainda não sincronizada"
    }
}

@Composable
fun SheetScreen() {
    val c = Store.config
    val ctx = LocalContext.current
    val s = Store.sheet
    val ok = c.sheetOn && s.syncedAt > 0 && s.error.isEmpty()
    Panel {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(
                if (ok) Icons.Default.CheckCircle else Icons.Default.Info,
                if (s.error.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, 48,
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(if (ok) "Planilha conectada" else "Google Sheets", style = MaterialTheme.typography.titleMedium)
                Text(syncText(), style = MaterialTheme.typography.bodySmall, color = if (s.error.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(c.sheetOn, { on -> cfg { copy(sheetOn = on) }; Sheets.schedule(ctx) })
        }
        if (ok) Hint("${s.rules.size} respostas · ${maxOf(0, s.consulta.size - 1)} linhas de consulta")
        val box = Store.outbox
        if (box.historico.isNotEmpty() || box.pedidos.isNotEmpty()) Hint("Aguardando envio: ${box.historico.size} no histórico, ${box.pedidos.size} pedidos")
        FilledTonalButton({ Sheets.schedule(ctx); Sheets.syncNow(ctx) }, Modifier.padding(bottom = 8.dp), enabled = c.sheetOn) {
            Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Sincronizar agora")
        }
    }
    val scope = rememberCoroutineScope()
    var testMsg by remember { mutableStateOf("") }
    fun test() {
        testMsg = "Testando conexão..."
        scope.launch {
            testMsg = withContext(Dispatchers.IO) {
                runCatching {
                    val (resp, cons) = Sheets.read(Store.config)
                    "Conectado! ${Engine.rulesFromSheet(resp).size} respostas e ${maxOf(0, cons.size - 1)} linhas de consulta."
                }.getOrElse { "Não conectou: ${it.message}" }
            }
            if (testMsg.startsWith("Conectado")) Sheets.syncNow(ctx)
        }
    }
    Panel("Conexão") {
        Button({
            val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            GmsBarcodeScanning.getClient(ctx, opts).startScan()
                .addOnSuccessListener { b ->
                    val p = Engine.parseConnectQr(b.rawValue.orEmpty())
                    if (p == null) testMsg = "Esse QR code não é do ZapResponde."
                    else {
                        cfg { copy(sheetUrl = p.first, sheetKey = p.second, sheetOn = true) }
                        Sheets.schedule(ctx); test()
                    }
                }
                .addOnFailureListener { testMsg = "Não abriu o leitor: ${it.message}" }
        }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Ler QR code da planilha")
        }
        OutlinedButton({ test() }, Modifier.fillMaxWidth(), enabled = c.sheetUrl.isNotBlank()) { Text("Testar conexão") }
        if (testMsg.isNotEmpty()) Text(
            testMsg, Modifier.padding(vertical = 8.dp),
            color = if (testMsg.startsWith("Não") || testMsg.startsWith("Esse")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        NumberField("Sincronizar a cada (mín. 15)", c.sheetSyncMin, "min") {
            cfg { copy(sheetSyncMin = it.coerceAtLeast(15)) }; Sheets.schedule(ctx)
        }
        Advanced("Digitar link e chave") {
            Field("Link do Apps Script", c.sheetUrl, help = "Termina em /exec") { cfg { copy(sheetUrl = it.trim()) } }
            Field("Chave", c.sheetKey) { cfg { copy(sheetKey = it.trim()) } }
        }
    }
    Panel("O que usar da planilha") {
        SwitchRow("Respostas", c.sheetRulesOn, "Aba Respostas: A palavras, B resposta, C comparação, D quem, E ativo") { cfg { copy(sheetRulesOn = it) } }
        Divider()
        SwitchRow("Gravar histórico", c.sheetLogOn, "Cada resposta vira uma linha na aba Historico") { cfg { copy(sheetLogOn = it) } }
        Hint("A aba Consulta é usada pelas regras de consulta, e a aba Pedidos pela função Pedido pela conversa.")
    }
    Panel {
        Advanced("Como conectar (uma vez só)") {
            Hint(
                "1. Na planilha, abra Extensões > Apps Script, cole o código do ZapResponde e salve.\n" +
                    "2. Clique em Implantar > Nova implantação > App da Web. Executar como: você. Acesso: qualquer pessoa.\n" +
                    "3. Recarregue a planilha e use o menu ZapResponde > Conectar celular.\n" +
                    "4. Aqui no app, toque em Ler QR code.",
            )
        }
    }
}

// ---------- Histórico ----------

@Composable
private fun HistoryItem(e: app.zapresponde.HistoryEntry) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.chat, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmt(e.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(e.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text(e.reply, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        Text(e.rule, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun HistoryScreen() {
    val h = Store.history
    Hint("${h.size} respostas guardadas · ${h.count { System.currentTimeMillis() - it.time < 86_400_000 }} nas últimas 24h")
    Panel {
        if (h.isEmpty()) Hint("Nenhuma resposta enviada ainda.")
        h.take(100).forEachIndexed { i, e ->
            if (i > 0) Divider()
            HistoryItem(e)
        }
    }
}

// ---------- Ajustes ----------

@Composable
fun MoreTab(open: (String) -> Unit) {
    val c = Store.config
    fun on(b: Boolean) = if (b) "ligado" else "desligado"
    // Every freight feature works only with "Respostas de frete" on; say so instead of failing silently.
    val needsFrete = if (c.freteOn) "" else " · precisa de Respostas de frete ligado"
    Panel("Fretes") {
        FeatureRow(Icons.Default.Search, "Respostas de frete", "Anúncio do FreteBras ou link: manda a carga da planilha", c.freteOn, { on -> cfg { copy(freteOn = on) } }) { open("frete") }
        Divider()
        FeatureRow(Icons.Default.Edit, "Pedido escrito de qualquer jeito", "\"Goiânia para Anápolis\", \"tem carga pra Brasília?\"$needsFrete", c.freteLivreOn, { on -> cfg { copy(freteLivreOn = on) } }) { open("frete") }
        Divider()
        FeatureRow(Icons.Default.Refresh, "Oferecer outra carga", "Sem a carga pedida, oferece uma parecida$needsFrete", c.altOn, { on -> cfg { copy(altOn = on) } }) { open("alt") }
        Divider()
        FeatureRow(Icons.Default.Notifications, "Avisar quando a carga aparecer", "${c.waitMode.label}$needsFrete", c.waitOn, { on -> cfg { copy(waitOn = on) } }) { open("wait") }
        Divider()
        FeatureRow(Icons.Default.Share, "Link do grupo depois da conversa", "Acompanhamento: ${c.followMin} min depois da última mensagem", c.followOn, { on -> cfg { copy(followOn = on) } }) { open("follow") }
        Divider()
        FeatureRow(Icons.AutoMirrored.Filled.List, "Leads e alerta de carga", "Aba Leads ${on(c.leadsOn)} · alerta ${on(c.alertOn)}", null) { open("frete") }
    }
    Panel("Respostas") {
        FeatureRow(Icons.Default.Face, "Boas-vindas", "Mensagem no primeiro contato", c.welcomeOn, { on -> cfg { copy(welcomeOn = on) } }) { open("welcome") }
        Divider()
        FeatureRow(Icons.Default.ShoppingCart, "Pedido pela conversa", "Faz perguntas e grava na aba Pedidos", c.formOn, { on -> cfg { copy(formOn = on) } }) { open("form") }
        Divider()
        FeatureRow(Icons.Default.Edit, "Cabeçalho e rodapé", "Texto fixo antes e depois das respostas", c.headerFooterOn, { on -> cfg { copy(headerFooterOn = on) } }) { open("header") }
    }
    Panel("Quem e quando") {
        FeatureRow(Icons.Default.Person, "Contatos", "Vale para o app todo: " + AUDIENCE[c.contactsMode].orEmpty() + if (c.groupsOn) " · grupos sim" else " · grupos não", c.contactsOn, { on -> cfg { copy(contactsOn = on) } }) { open("contacts") }
        Divider()
        FeatureRow(Icons.Default.DateRange, "Agenda", "Só responde em ${c.scheduleHours}", c.scheduleOn, { on -> cfg { copy(scheduleOn = on) } }) { open("schedule") }
    }
    Panel("Segurança") {
        FeatureRow(Icons.Default.CheckCircle, "Aprovar antes de enviar", "Você confirma cada resposta pela notificação", c.approvalOn, { on -> cfg { copy(approvalOn = on) } }) { open("approval") }
        Divider()
        FeatureRow(Icons.Default.Person, "Pausa quando você assume", "Para por ${c.takeoverMin} min se você tocar na notificação ou escrever na conversa", c.takeoverOn, { on -> cfg { copy(takeoverOn = on) } }) { open("takeover") }
        Divider()
        FeatureRow(Icons.Default.Refresh, "Atraso humano", "Espera de ${c.delayMinSec} a ${c.delayMaxSec} segundos antes de responder", c.delayOn, { on -> cfg { copy(delayOn = on) } }) { open("delay") }
        Divider()
        FeatureRow(Icons.Default.Warning, "Proteção contra loop", "Pausa se outro robô ficar respondendo", c.loopGuardOn, { on -> cfg { copy(loopGuardOn = on) } }) { open("loop") }
    }
    Panel("Ferramentas") {
        FeatureRow(Icons.Default.PlayArrow, "Testar respostas", "Simule uma mensagem sem enviar nada", null) { open("test") }
        Divider()
        FeatureRow(Icons.Default.Info, "Diagnóstico", "Por que respondeu ou não · IA ${on(c.aiDiagOn)}", null) { open("help") }
        Divider()
        FeatureRow(Icons.Default.Face, "Assistente de IA", "Cria regras e testa as regras com o Gemini", c.aiOn, { on -> cfg { copy(aiOn = on) } }) { open("ai") }
        Divider()
        FeatureRow(Icons.Default.Refresh, "Backup", "Exportar e importar configurações e regras", null) { open("backup") }
    }
    val ctx = LocalContext.current
    Hint("ZapResponde versão " + ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName)
}

@Composable
fun AltScreen() {
    val c = Store.config
    Panel {
        SwitchRow("Oferecer outra carga", c.altOn) { cfg { copy(altOn = it) } }
    }
    Panel("Onde procurar (nesta ordem)") {
        SwitchRow("Saindo da mesma cidade", c.altOrigin, "Pediu Goiânia → Anápolis: oferece outra saindo de Goiânia") { cfg { copy(altOrigin = it) } }
        SwitchRow("Indo para a mesma cidade", c.altDest, "Oferece outra indo para Anápolis") { cfg { copy(altDest = it) } }
        SwitchRow("Saindo do mesmo estado", c.altState, "Oferece outra saindo de GO") { cfg { copy(altState = it) } }
    }
    Panel("Mensagem") {
        Field("Texto antes da carga", c.altText, lines = 2, help = "{pedida} = rota pedida · {motivo} = \"saindo de GOIANIA/GO\". Vazio = só a carga.") { cfg { copy(altText = it) } }
        Hint("Vai uma carga só, a de coleta mais próxima, com a mensagem completa da planilha. Sem nenhuma parecida, vai a mensagem de carga indisponível. Na aba Leads fica \"alternativa\" com a rota oferecida.")
    }
}

@Composable
fun WaitScreen() {
    val c = Store.config
    Panel {
        SwitchRow("Avisar quando a carga aparecer", c.waitOn) { cfg { copy(waitOn = it) } }
    }
    Panel("Quando a carga aparecer") {
        WaitMode.entries.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { cfg { copy(waitMode = m) } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(c.waitMode == m, { cfg { copy(waitMode = m) } })
                Column {
                    Text(m.label)
                    Text(
                        when (m) {
                            WaitMode.ASK -> "Notificação com Enviar e Descartar; nada sai sem o seu toque"
                            WaitMode.AUTO -> "Manda a carga ao motorista e avisa você"
                            WaitMode.NOTIFY -> "Só avisa você; o app não manda nada"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        NumberField("Esquecer o pedido depois de", c.waitHours, "h") { cfg { copy(waitHours = it.coerceIn(1, 72)) } }
    }
    Panel {
        Hint("Quando um pedido de frete fica sem carga, o app anota o contato e a rota. Cada vez que lê a planilha, confere se a carga apareceu e avisa uma vez só. Se você assumiu a conversa, \"Enviar sozinho\" vira \"Me perguntar antes\". Os avisos vibram.")
        Hint("Limite do Android: o envio usa o botão Responder da última notificação da conversa. Se o celular reiniciar, o app só avisa e você responde à mão.")
    }
}

@Composable
fun WelcomeScreen() {
    val c = Store.config
    Panel { SwitchRow("Ativar boas-vindas", c.welcomeOn) { cfg { copy(welcomeOn = it) } } }
    Panel("Mensagem") {
        Field("Texto", c.welcomeText, 3) { cfg { copy(welcomeText = it) } }
        Hint(VARS)
    }
    Panel("Quando enviar") {
        SwitchRow("Uma vez por dia", c.welcomeDaily, "Desligado: só na primeira vez que a pessoa fala com você") { cfg { copy(welcomeDaily = it) } }
        Hint("Se uma regra também servir, a resposta dela vai logo depois.")
    }
}

@Composable
fun FormScreen() {
    val c = Store.config
    Panel { SwitchRow("Ativar pedido pela conversa", c.formOn) { cfg { copy(formOn = it) } } }
    Panel("Como funciona") {
        Field("Palavras que iniciam o pedido", c.formTrigger, help = "Separe por vírgula") { cfg { copy(formTrigger = it) } }
        Field("Perguntas, uma por linha", c.formQuestions, 5) { cfg { copy(formQuestions = it) } }
        Field("Mensagem final", c.formDone, 2) { cfg { copy(formDone = it) } }
        Field("Palavra para cancelar", c.formCancel) { cfg { copy(formCancel = it) } }
        Hint("As respostas vão para a aba Pedidos (a planilha precisa estar ligada).")
    }
}

@Composable
fun DelayScreen() {
    val c = Store.config
    Panel { SwitchRow("Ativar atraso humano", c.delayOn) { cfg { copy(delayOn = it) } } }
    Panel("Tempo de espera") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { NumberField("Mínimo", c.delayMinSec, "s") { cfg { copy(delayMinSec = it) } } }
            Box(Modifier.weight(1f)) { NumberField("Máximo", c.delayMaxSec, "s") { cfg { copy(delayMaxSec = it) } } }
        }
        Hint("O app sorteia um tempo entre os dois. Se a pessoa mandar outra mensagem durante a espera, a resposta é trocada pela nova.")
    }
}

@Composable
fun ScheduleScreen() {
    val c = Store.config
    Panel { SwitchRow("Ativar agenda", c.scheduleOn, "Fora desses horários o app não responde") { cfg { copy(scheduleOn = it) } } }
    Panel("Horários") {
        Field("Horários", c.scheduleHours, help = "Ex.: 18:00-08:00 ou 08:00-12:00, 14:00-18:00") { cfg { copy(scheduleHours = it) } }
        DaysPicker(c.scheduleDays) { cfg { copy(scheduleDays = it) } }
    }
}

@Composable
fun HeaderScreen() {
    val c = Store.config
    Panel { SwitchRow("Ativar cabeçalho e rodapé", c.headerFooterOn) { cfg { copy(headerFooterOn = it) } } }
    Panel("Textos") {
        Field("Cabeçalho", c.header, 2) { cfg { copy(header = it) } }
        Field("Rodapé", c.footer, 2) { cfg { copy(footer = it) } }
    }
}

@Composable
fun TakeoverScreen() {
    val c = Store.config
    Panel {
        SwitchRow("Ativar pausa", c.takeoverOn) { cfg { copy(takeoverOn = it) } }
        SwitchRow("Pausar também quando a conversa for lida", c.takeoverOnRead, "Inclui ler pelo WhatsApp Web. Deixe desligado se você usa o Web.") { cfg { copy(takeoverOnRead = it) } }
    }
    Panel("Duração") {
        NumberField("Tempo de pausa", c.takeoverMin, "min") { cfg { copy(takeoverMin = it) } }
        Hint("Quando você toca na notificação ou escreve na conversa, o robô para de responder naquela conversa.")
    }
    var tick by remember { mutableStateOf(0) }
    val paused = Store.state.chats.filter { tick >= 0 && it.value.pauseUntil > System.currentTimeMillis() }
    if (paused.isNotEmpty()) Panel("Pausadas agora") {
        paused.forEach { (chat, st) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$chat · até ${fmt(st.pauseUntil)}", Modifier.weight(1f))
                TextButton({ Store.pauseChat(chat, 0); tick++ }) { Text("Retomar") }
            }
        }
    }
}

@Composable
fun LoopScreen() {
    val c = Store.config
    Panel { SwitchRow("Ativar proteção contra loop", c.loopGuardOn) { cfg { copy(loopGuardOn = it) } } }
    Panel { Hint("Se o app responder 5 vezes à mesma conversa em 2 minutos, ela fica pausada por 30 minutos.") }
}

val AUDIENCE = mapOf(
    Audience.ALL to "Todos",
    Audience.LIST to "Só minha lista de contatos",
    Audience.EXCEPT_LIST to "Todos, exceto minha lista",
    Audience.EXCEPT_PHONE to "Exceto meus contatos do telefone",
)

@Composable
fun ContactsScreen() {
    val c = Store.config
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) picking = true }
    val list = c.contactsList.lines().map { it.trim() }.filter { it.isNotEmpty() }
    fun save(l: List<String>) = cfg { copy(contactsList = l.distinct().joinToString("\n")) }

    Panel { SwitchRow("Ativar filtro de contatos", c.contactsOn) { cfg { copy(contactsOn = it) } } }
    Panel("Resposta automática para") {
        AUDIENCE.forEach { (mode, label) ->
            Row(Modifier.fillMaxWidth().clickable { cfg { copy(contactsMode = mode) } }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(c.contactsMode == mode, { cfg { copy(contactsMode = mode) } })
                Text(label)
            }
        }
        Divider()
        SwitchRow("Responder em grupos", c.groupsOn) { cfg { copy(groupsOn = it) } }
        if (c.contactsMode == Audience.EXCEPT_PHONE && !Contacts.allowed(ctx)) Hint("Sem acesso aos contatos, o app considera salvo quem aparece com nome em vez de número.")
    }
    Panel("Lista de contatos (${list.size})") {
        Button({ if (Contacts.allowed(ctx)) picking = true else ask.launch(Manifest.permission.READ_CONTACTS) }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Escolher dos contatos do telefone")
        }
        var typed by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Field("Ou digite um nome", typed) { typed = it } }
            IconButton({ if (typed.isNotBlank()) { save(list + typed.trim()); typed = "" } }) { Icon(Icons.Default.Add, "Adicionar") }
        }
        list.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f))
                IconButton({ save(list - name) }) { Icon(Icons.Default.Close, "Remover") }
            }
        }
        if (list.isEmpty()) Hint("Nenhum contato na lista.")
    }
    if (picking) ContactPicker(list, { picking = false }) { save(it); picking = false }
}

/** Full-screen list of the phone's contacts with search and checkboxes. */
@Composable
private fun ContactPicker(selected: List<String>, dismiss: () -> Unit, done: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { Contacts.all(ctx) } }
    var query by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) {
                Text("Escolher contatos (${chosen.size})", style = MaterialTheme.typography.titleLarge)
                Field("Buscar", query) { query = it }
                val q = Engine.normalize(query)
                val shown = all.orEmpty().filter { q.isEmpty() || Engine.normalize(it).contains(q) }
                if (all == null) Hint("Carregando contatos...")
                LazyColumn(Modifier.weight(1f)) {
                    items(shown) { name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (name in chosen) chosen.remove(name) else chosen.add(name) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(name in chosen, { if (it) chosen.add(name) else chosen.remove(name) })
                            Text(name)
                        }
                    }
                }
                Row {
                    TextButton({ chosen.removeAll(shown) }) { Text("Desmarcar visíveis") }
                    TextButton({ chosen.addAll(shown.filter { it !in chosen }) }) { Text("Marcar visíveis") }
                    Spacer(Modifier.weight(1f))
                    Button({ done(chosen.toList()) }) { Text("Concluir") }
                }
            }
        }
    }
}

@Composable
fun BackupScreen() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) msg = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Store.exportBackup().toByteArray()) }
            "Backup salvo."
        }.getOrElse { "Não consegui salvar: ${it.message}" }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) msg = runCatching {
            Store.importBackup(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
            Sheets.schedule(ctx)
            "Backup restaurado: configurações e ${Store.rules.size} regras."
        }.getOrElse { it.message ?: "Não consegui ler o arquivo." }
    }
    Panel {
        Button({ export.launch("zapresponde-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json") }, Modifier.fillMaxWidth()) {
            Text("Exportar backup")
        }
        OutlinedButton({ import.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, Modifier.fillMaxWidth()) {
            Text("Importar backup")
        }
        if (msg.isNotEmpty()) Hint(msg)
    }
    val c = Store.config
    val scope = rememberCoroutineScope()
    Panel("Backup automático") {
        SwitchRow("Backup diário na planilha", c.backupAutoOn, "Uma vez por dia, na aba Backup da planilha conectada") { cfg { copy(backupAutoOn = it) } }
        OutlinedButton({
            msg = "Buscando o backup na planilha..."
            scope.launch {
                msg = withContext(Dispatchers.IO) {
                    runCatching {
                        val text = Sheets.readBackup(Store.config) ?: error("A planilha ainda não tem backup.")
                        Store.importBackup(text)
                        "Backup da planilha restaurado: configurações e ${Store.rules.size} regras."
                    }.getOrElse { it.message ?: "Não consegui restaurar." }
                }
                Sheets.schedule(ctx)
            }
        }, Modifier.fillMaxWidth(), enabled = c.sheetUrl.isNotBlank()) { Text("Restaurar o backup da planilha") }
    }
    Panel {
        Hint("O arquivo leva todas as configurações (inclusive o link e a chave da planilha) e as regras do app. Guarde no Google Drive ou mande para você mesmo. Importar substitui as configurações e regras atuais. O histórico não vai no backup.")
    }
}

@Composable
fun ApprovalScreen() {
    val c = Store.config
    Panel {
        SwitchRow("Aprovar antes de enviar", c.approvalOn) { cfg { copy(approvalOn = it) } }
        Field("Conversas que precisam de aprovação", c.approvalList, lines = 3, help = "Um nome por linha. Vazio = todas as conversas.") { cfg { copy(approvalList = it) } }
    }
    Panel { Hint("O robô prepara a resposta e mostra uma notificação com \"Enviar\" e \"Descartar\". Nada é enviado sem o seu toque.") }
}

@Composable
fun FollowScreen() {
    val c = Store.config
    Panel {
        SwitchRow("Link do grupo depois da conversa", c.followOn) { cfg { copy(followOn = it) } }
        NumberField("Esperar depois da última mensagem", c.followMin, "min") { cfg { copy(followMin = it.coerceIn(1, 1440)) } }
        Field("Link do grupo", c.followLink, help = "Dados do grupo > Convidar via link > Copiar link. Entra no lugar de {link}.") { cfg { copy(followLink = it.trim()) } }
        Field("Mensagem", c.followText, lines = 5, help = "{link} = link do grupo. Também valem {primeiro_nome}, {nome} e {saudacao}.") { cfg { copy(followText = it) } }
        Advanced {
            Field("Frases de quem já está no grupo", c.followJoined, lines = 3, help = "Separadas por vírgula. Se o contato escrever uma delas, não recebe o link.") { cfg { copy(followJoined = it) } }
        }
    }
    Panel {
        Hint("Depois de uma conversa que o app respondeu, ele espera esse tempo contado da última mensagem e manda a mensagem uma vez só por contato. Nova mensagem do contato recomeça a contagem; se você escrever na conversa ou ela estiver pausada, cancela. Segue o filtro de Contatos do app; grupos nunca recebem.")
        Hint("Limite do Android: o envio usa o botão Responder da última notificação da conversa. Funciona bem com minutos ou poucas horas; se o celular reiniciar ou o app for fechado, os acompanhamentos pendentes não são enviados (aparece no Diagnóstico). Máximo: 1440 min (24 h).")
    }
}

@Composable
fun FreteScreen() {
    val c = Store.config
    Panel {
        SwitchRow("Respostas de frete", c.freteOn) { cfg { copy(freteOn = it) } }
        SwitchRow(
            "Pedido escrito de qualquer jeito", c.freteLivreOn,
            "\"Goiânia para Anápolis\", \"goiania-GO anapolis\", \"tem carga pra Brasília?\": a 1ª cidade é a origem e a 2ª o destino",
        ) { cfg { copy(freteLivreOn = it) } }
        Divider()
        SwitchRow("Gravar na aba Leads", c.leadsOn, "Data, motorista, rota, carga e se estava disponível") { cfg { copy(leadsOn = it) } }
        SwitchRow("Alerta de carga pedida", c.alertOn, "Notificação no celular quando a carga pedida está disponível; tocar abre a conversa") { cfg { copy(alertOn = it) } }
        Divider()
        Field("Quando a carga não estiver na planilha", c.freteNotFound, lines = 5, help = "Deixe vazio para não responder.") { cfg { copy(freteNotFound = it) } }
    }
    Panel {
        Hint("Quando chega \"estou disponível para carregar seu frete de Origem/UF para Destino/UF\" (ou o link do FreteBras), o app procura a rota na planilha ligada em ZapResponde > Ligar planilha de cargas. A coluna A tem a rota (\"Ponta Grossa/PR para Rio de Janeiro/RJ\") e a mensagem pronta da linha é enviada. Com mais de uma carga na rota, manda só uma: a do mesmo tipo de carga e com a coleta mais próxima (linha \"Coleta: dd/mm\" da mensagem). A planilha precisa estar ligada em Planilha > Google Sheets; sem isso, o app usa a última cópia das cargas, que não atualiza.")
    }
}

@Composable
fun AssistantScreen() {
    val c = Store.config
    val scope = rememberCoroutineScope()
    var request by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<Assistant.Draft?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun ask() {
        busy = true; status = "Pedindo ao Gemini..."
        val current = draft?.rule
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { Assistant.parse(Assistant.gemini(c.aiKey, c.aiModel, Assistant.prompt(request, current))) }
            }.fold({ draft = it; status = ""; request = "" }, { status = it.message ?: "Falhou: tente de novo." })
            busy = false
        }
    }
    Panel {
        SwitchRow("Ativar assistente de IA", c.aiOn) { cfg { copy(aiOn = it) } }
        Field("Chave da API do Gemini", c.aiKey, help = "Crie grátis em aistudio.google.com/apikey e cole aqui") { cfg { copy(aiKey = it.trim()) } }
        Advanced { Field("Modelo", c.aiModel, help = "Padrão: gemini-flash-latest") { cfg { copy(aiModel = it.trim()) } } }
    }
    Panel(if (draft == null) "O que a regra deve fazer?" else "Quer ajustar alguma coisa?") {
        Field(
            if (draft == null) "Pedido" else "Ajuste", request, 3,
            help = if (draft == null) "Ex.: quando perguntarem preço, responder \"a partir de R$ 50\", de segunda a sexta das 8h às 18h, uma vez por conversa"
            else "Ex.: muda o horário para 7h às 17h",
        ) { request = it }
        Button({ ask() }, Modifier.fillMaxWidth().padding(vertical = 8.dp), enabled = c.aiOn && c.aiKey.isNotBlank() && request.isNotBlank() && !busy) {
            Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text(if (draft == null) "Criar regra" else "Ajustar regra")
        }
        if (status.isNotEmpty()) Text(status, color = if (busy || status.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        if (!c.aiOn || c.aiKey.isBlank()) Hint("Ligue o assistente e cole a chave acima. Só o seu pedido vai para o Gemini; mensagens de clientes não são enviadas.")
    }
    draft?.let { d ->
        val r = d.rule
        Panel("Regra sugerida") {
            Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    r.match.label + if (r.match != Match.ALL) ": ${r.keywords}" else "",
                    r.exclude.takeIf { it.isNotBlank() }?.let { "Exceto: $it" },
                    r.freq.label + when (r.freq) { Freq.INTERVAL -> " (${r.intervalMin} min)"; Freq.MAX -> " (${r.maxReplies})"; else -> "" },
                    "Horário: " + r.hours.ifBlank { "o dia todo" } + if (r.days.size < 7) " · dias ${r.days.sorted().joinToString()}" else "",
                    "Quem: ${r.who.label} · grupos: ${r.groups.label}",
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp),
            )
            r.replies.filter { it.isNotBlank() }.forEach {
                Box(
                    Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
                ) { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
        val results = Assistant.check(d)
        Panel("Teste automático") {
            if (results.isEmpty()) Hint("A IA não mandou exemplos. Use Testar respostas depois de aplicar.")
            results.forEach { (e, ok) ->
                Text("${if (ok) "✅" else "❌"} \"${e.text}\" → ${if (e.shouldReply) "deve responder" else "não deve responder"}${if (ok) "" else " (não bateu)"}")
            }
            d.problems.forEach { Text("❌ $it", color = MaterialTheme.colorScheme.error) }
            Hint("O teste confere as palavras. Horário, contatos e frequência valem quando a mensagem real chegar.")
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ draft = null; status = "" }, Modifier.weight(1f)) { Text("Descartar") }
            Button({
                Store.updateRules(Store.rules + r); draft = null; status = "✅ Regra \"${r.name}\" aplicada. Ela está na aba Regras."
            }, Modifier.weight(1f), enabled = d.problems.isEmpty()) { Text("Aplicar") }
        }
        if (d.problems.isEmpty() && !results.all { it.second }) Hint("Algum teste não bateu: o melhor é pedir um ajuste antes de aplicar.")
    }
    RuleTestPanel()
}

/** A IA cria mensagens para as regras escolhidas; o app confere qual regra responde cada uma e mostra o que não bateu. */
@Composable
private fun RuleTestPanel() {
    val c = Store.config
    val scope = rememberCoroutineScope()
    val active = Store.allRules().filter { it.enabled }
    var picked by remember { mutableStateOf(active.map { it.id }.toSet()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<List<Pair<Assistant.RuleTest, String>>, List<String>>?>(null) }
    if (active.isEmpty()) { Panel("Testar regras com a IA") { Hint("Nenhuma regra ativa para testar.") }; return }
    Panel("Testar regras com a IA") {
        Hint("Escolha as regras. A IA inventa mensagens de clientes e o app confere qual regra responderia cada uma, na ordem real. Só as regras vão ao Gemini.")
        active.forEach { r ->
            Row(Modifier.fillMaxWidth().clickable { picked = if (r.id in picked) picked - r.id else picked + r.id }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(r.id in picked, { picked = if (it) picked + r.id else picked - r.id })
                Text(r.name)
            }
        }
        Button({
            busy = true; status = "Pedindo testes ao Gemini..."; result = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val (tests, notes) = Assistant.parseRuleTests(
                            Assistant.gemini(c.aiKey, c.aiModel, Assistant.rulesTestPrompt(active.filter { it.id in picked }, active)),
                        )
                        Assistant.runRuleTests(tests, active) to notes
                    }
                }.fold({ result = it; status = "" }, { status = it.message ?: "Falhou: tente de novo." })
                busy = false
            }
        }, Modifier.fillMaxWidth().padding(vertical = 8.dp), enabled = c.aiOn && c.aiKey.isNotBlank() && picked.isNotEmpty() && !busy) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Testar ${picked.size} regra(s)")
        }
        if (status.isNotEmpty()) Text(status, color = if (busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        result?.let { (tests, notes) ->
            val ok = tests.count { (t, got) -> t.expected.trim().equals(got, true) }
            Text("$ok de ${tests.size} testes bateram", fontWeight = FontWeight.Bold)
            tests.forEach { (t, got) ->
                val pass = t.expected.trim().equals(got, true)
                Text(
                    "${if (pass) "✅" else "❌"} \"${t.text}\" → " + got.ifEmpty { "nenhuma regra" } +
                        if (pass) "" else " (esperado: ${t.expected.ifEmpty { "nenhuma regra" }})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (notes.isNotEmpty()) {
                Text("Observações da IA", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            Hint("O teste confere as palavras e a ordem das regras. Horário, contatos e frequência valem quando a mensagem real chegar.")
        }
    }
}

@Composable
fun TestScreen() {
    var name by remember { mutableStateOf("Maria Silva") }
    var text by remember { mutableStateOf("Oi, bom dia") }
    var group by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    val ctx = LocalContext.current
    Panel("Mensagem simulada") {
        Field("Nome de quem manda", name) { name = it }
        Field("Grupo", group, help = "Vazio = conversa individual") { group = it }
        Field("Mensagem", text, 2) { text = it }
        Button({
            val msg = Incoming(group.ifBlank { name }, name, text, group.isNotBlank())
            val d = Engine.decide(
                msg, LocalDateTime.now(), System.currentTimeMillis(), Store.config.copy(enabled = true),
                Store.allRules(), Store.sheet.consulta, Store.stateCopy(), phoneNames = Contacts.names(ctx),
            )
            result = if (d.replies.isEmpty()) "Sem resposta: ${d.reason}" to emptyList()
            else "${d.rule} · espera de ${d.delayMs / 1000}s" to d.replies
        }, Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Testar")
        }
        Hint("Não envia nada e ignora o botão ON/OFF.")
    }
    result?.let { (title, replies) ->
        Panel("Resultado") {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            replies.forEach {
                Box(
                    Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp),
                ) { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    }
}

@Composable
fun HelpScreen() {
    val ctx = LocalContext.current
    val access = resumeTick >= 0 && NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    val battery = resumeTick >= 0 && ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    val notifs = resumeTick >= 0 && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    Panel("Verificações") {
        Check("Acesso às notificações", access) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        Divider()
        Check("Sem economia de bateria", battery) {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
        }
        Divider()
        Check("Notificações do ZapResponde", notifs) {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        Divider()
        Check("Respostas automáticas ligadas", Store.config.enabled) { cfg { copy(enabled = true) } }
    }
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf("") }
    Panel("Relatório") {
        Button({
            report = "Testando..."
            scope.launch { report = withContext(Dispatchers.IO) { diagnose(ctx) } }
        }, Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Rodar diagnóstico completo") }
        if (report.isNotEmpty()) {
            Text(report, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton({
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Enviar relatório"))
            }, Modifier.fillMaxWidth()) { Text("Compartilhar relatório") }
        }
    }
    val c = Store.config
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Panel("Diagnóstico pela IA") {
        SwitchRow("Ativar diagnóstico pela IA", c.aiDiagOn, "O relatório, com o texto das últimas mensagens, vai para o Gemini") { cfg { copy(aiDiagOn = it) } }
        if (c.aiDiagOn) {
            Field("Pergunta (opcional)", question, 2, help = "Ex.: por que respondeu várias vezes para o Meu?") { question = it }
            Button({
                busy = true; answer = "Rodando o diagnóstico e perguntando ao Gemini..."
                scope.launch {
                    answer = runCatching {
                        withContext(Dispatchers.IO) {
                            report = diagnose(ctx)
                            Assistant.gemini(c.aiKey, c.aiModel, Assistant.diagnosisPrompt(report, c, Store.allRules(), question), jsonOut = false)
                        }
                    }.getOrElse { "❌ " + (it.message ?: "Falhou: tente de novo.") }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = c.aiKey.isNotBlank() && !busy) { Icon(Icons.Default.Face, null); Spacer(Modifier.width(6.dp)); Text("Pedir análise à IA") }
            if (c.aiKey.isBlank()) Hint("Cole a chave do Gemini em Ajustes > Assistente de IA.")
            if (answer.isNotEmpty()) {
                Text(answer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                if (!busy) OutlinedButton({
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, answer + "\n\n" + report), "Enviar análise"))
                }, Modifier.fillMaxWidth()) { Text("Compartilhar análise e relatório") }
            }
        }
    }
    Panel("Ações") {
        FeatureRow(Icons.Default.Notifications, "Notificações do WhatsApp", "Precisam estar ligadas para o app responder", null) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.whatsapp")))
        }
        Divider()
        FeatureRow(Icons.Default.Notifications, "Notificações do WhatsApp Business", "Se você usa o Business", null) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.whatsapp.w4b")))
        }
        Divider()
        // Re-binding the listener fixes the common "stopped replying" case after updates.
        FeatureRow(Icons.Default.Refresh, "Reiniciar o serviço de respostas", "Resolve quando o app para de responder", null) {
            NotificationListenerService.requestRebind(ComponentName(ctx, ReplyService::class.java))
        }
    }
    Panel("Dicas") {
        Hint(
            "• O app só responde quando aparece notificação. Com a conversa aberta ou silenciada, não responde.\n" +
                "• Xiaomi, Samsung e Motorola: libere \"início automático\" e tire o app da economia de bateria.\n" +
                "• Android 15+: se aparecer aviso de notificação sensível, permita o acesso nas configurações.\n" +
                "• Se nada resolver, desligue e ligue o acesso às notificações ou reinicie o celular.",
        )
    }
}

/** Plain-text report: every setting that can stop a reply, the sheet and freight self-tests, and the last messages seen. */
private fun diagnose(ctx: android.content.Context): String {
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
        // The 5 newest also show the path of the decision, step by step.
        if (i < 5) e.trace.forEach { l += "      $it" }
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
    l.add(2, if (causa != null) "➡️ CAUSA MAIS PROVÁVEL: $causa" else "➡️ O ambiente parece saudável; veja o motivo de cada mensagem no fim.")
    return l.joinToString("\n")
}

@Composable
private fun Check(label: String, ok: Boolean, fix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, 32)
        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp))
        if (!ok) TextButton(fix) { Text("Corrigir") }
    }
}

private fun fmt(ms: Long) = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
