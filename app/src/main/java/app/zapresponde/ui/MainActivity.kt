package app.zapresponde.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zapresponde.Config
import app.zapresponde.Sheets
import app.zapresponde.Store

/* The activity, the tabs and the navigation between screens. Each area of the app lives in its own file. */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Store.trace("APP", "App aberto · versão ${packageManager.getPackageInfo(packageName, 0).versionName} · Android ${Build.VERSION.RELEASE}")
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
internal var resumeTick by mutableStateOf(0)

internal fun cfg(f: Config.() -> Config) = Store.updateConfig(Store.config.f())

internal data class Tab(val key: String, val label: String, val icon: ImageVector)

internal val TABS = listOf(
    Tab("home", "Início", Icons.Default.Home),
    Tab("rules", "Regras", Icons.Default.Build),
    Tab("sheet", "Planilha", Icons.AutoMirrored.Filled.List),
    Tab("history", "Histórico", Icons.Default.Notifications),
    Tab("more", "Ajustes", Icons.Default.Settings),
)

internal val TITLES = mapOf(
    "welcome" to "Boas-vindas", "form" to "Pedido pela conversa", "delay" to "Atraso humano",
    "schedule" to "Agenda", "header" to "Cabeçalho e rodapé", "takeover" to "Pausa quando você assume",
    "loop" to "Proteção contra loop", "contacts" to "Contatos", "backup" to "Backup", "approval" to "Aprovar antes de enviar", "frete" to "Respostas de frete", "follow" to "Link do grupo depois da conversa", "alt" to "Oferecer outra carga", "wait" to "Avisar quando a carga aparecer", "ai" to "Assistente de IA", "test" to "Testar respostas", "help" to "Diagnóstico", "clean" to "Limpar dados",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var tab by rememberSaveable { mutableStateOf("home") }
    // Detail screen on top of the tabs: a feature key or "rule:<id>".
    var sub by rememberSaveable { mutableStateOf("") }
    var newRule by remember { mutableStateOf(false) }
    // One scroll position per tab, kept while a detail screen is open, so "back" returns to the same spot.
    val scrolls = remember { HashMap<String, ScrollState>() }
    BackHandler(sub.isNotEmpty() || tab != "home") { if (sub == "contactpick") sub = "contacts" else if (sub.isNotEmpty()) sub = "" else tab = "home" }
    val open: (String) -> Unit = { sub = it }
    val bar = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)

    if (sub == "contactpick") {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Escolher contatos") }, colors = bar,
                    navigationIcon = { IconButton({ sub = "contacts" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                )
            },
        ) { pad -> ContactPicker(pad) { sub = "contacts" } }
        return
    }
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
            Page(pad, remember(sub) { ScrollState(0) }) {
                if (ruleId != null) RuleEdit(ruleId) { sub = "" }
                else when (sub) {
                    "welcome" -> WelcomeScreen()
                    "form" -> FormScreen()
                    "delay" -> DelayScreen()
                    "schedule" -> ScheduleScreen()
                    "header" -> HeaderScreen()
                    "takeover" -> TakeoverScreen()
                    "loop" -> LoopScreen()
                    "contacts" -> ContactsScreen(open)
                    "backup" -> BackupScreen()
                    "approval" -> ApprovalScreen()
                    "frete" -> FreteScreen()
                    "follow" -> FollowScreen()
                    "alt" -> AltScreen()
                    "wait" -> WaitScreen()
                    "test" -> TestScreen()
                    "ai" -> AssistantScreen()
                    "help" -> HelpScreen(open)
                    "clean" -> CleanScreen()
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
        Page(pad, scrolls.getOrPut(tab) { ScrollState(0) }) {
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
internal fun Page(pad: PaddingValues, scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(scroll).padding(horizontal = 16.dp)) {
        content()
        Spacer(Modifier.height(96.dp))
    }
}
