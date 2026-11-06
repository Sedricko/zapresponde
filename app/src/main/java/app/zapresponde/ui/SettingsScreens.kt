package app.zapresponde.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.zapresponde.AUDIENCE
import app.zapresponde.Audience
import app.zapresponde.Contacts
import app.zapresponde.Engine
import app.zapresponde.Sheets
import app.zapresponde.Store
import app.zapresponde.WaitMode
import app.zapresponde.fmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* Ajustes and the screen of each feature. */

// ---------- Ajustes ----------

@Composable
fun MoreTab(open: (String) -> Unit) {
    val c = Store.config
    fun on(b: Boolean) = if (b) "ligado" else "desligado"
    // Every freight feature works only with "Respostas de frete" on; say so instead of failing silently.
    val needsFrete = if (c.freteOn) "" else " · precisa de Respostas de frete ligado"
    // One tab per context of use: the long list became four short pages.
    var tab by rememberSaveable { mutableStateOf(0) }
    Tabs(listOf("Fretes", "Respostas", "Controle", "Ferramentas"), tab) { tab = it }
    if (tab == 0) Panel("Fretes") {
        FeatureRow(Icons.Default.Search, "Respostas de frete", "Anúncio do FreteBras ou link: manda a carga da planilha", c.freteOn, { on -> cfg { copy(freteOn = on) } }) { open("frete") }
        Divider()
        FeatureRow(Icons.Default.Edit, "Pedido escrito de qualquer jeito", "\"Goiânia para Anápolis\", \"tem carga pra Brasília?\"$needsFrete", c.freteLivreOn, { on -> cfg { copy(freteLivreOn = on) } }) { open("frete") }
        Divider()
        FeatureRow(Icons.Default.Place, "Oferecer outra carga", "Sem a carga pedida, oferece uma parecida$needsFrete", c.altOn, { on -> cfg { copy(altOn = on) } }) { open("alt") }
        Divider()
        FeatureRow(Icons.Default.Notifications, "Avisar quando a carga aparecer", "${c.waitMode.label}$needsFrete", c.waitOn, { on -> cfg { copy(waitOn = on) } }) { open("wait") }
        Divider()
        FeatureRow(Icons.Default.Share, "Link do grupo depois da conversa", "Acompanhamento: ${c.followMin} min depois da última mensagem", c.followOn, { on -> cfg { copy(followOn = on) } }) { open("follow") }
        Divider()
        FeatureRow(Icons.AutoMirrored.Filled.List, "Leads e alerta de carga", "Aba Leads ${on(c.leadsOn)} · alerta ${on(c.alertOn)}", null) { open("frete") }
    }
    if (tab == 1) Panel("Respostas automáticas") {
        FeatureRow(Icons.Default.Face, "Boas-vindas", "Mensagem no primeiro contato", c.welcomeOn, { on -> cfg { copy(welcomeOn = on) } }) { open("welcome") }
        Divider()
        FeatureRow(Icons.Default.ShoppingCart, "Pedido pela conversa", "Faz perguntas e grava na aba Pedidos", c.formOn, { on -> cfg { copy(formOn = on) } }) { open("form") }
        Divider()
        FeatureRow(Icons.Default.Email, "Cabeçalho e rodapé", "Texto fixo antes e depois das respostas", c.headerFooterOn, { on -> cfg { copy(headerFooterOn = on) } }) { open("header") }
    }
    if (tab == 2) Panel("Quem e quando") {
        FeatureRow(Icons.Default.AccountCircle, "Contatos", "Vale para o app todo: " + AUDIENCE[c.contactsMode].orEmpty() + if (c.groupsOn) " · grupos sim" else " · grupos não", c.contactsOn, { on -> cfg { copy(contactsOn = on) } }) { open("contacts") }
        Divider()
        FeatureRow(Icons.Default.DateRange, "Agenda", "Só responde em ${c.scheduleHours}", c.scheduleOn, { on -> cfg { copy(scheduleOn = on) } }) { open("schedule") }
    }
    if (tab == 2) Panel("Segurança") {
        FeatureRow(Icons.Default.CheckCircle, "Aprovar antes de enviar", "Você confirma cada resposta pela notificação", c.approvalOn, { on -> cfg { copy(approvalOn = on) } }) { open("approval") }
        Divider()
        FeatureRow(Icons.Default.Person, "Pausa quando você assume", "Para por ${c.takeoverMin} min quando você escreve, toca na notificação ou lê o que o app não respondeu", c.takeoverOn, { on -> cfg { copy(takeoverOn = on) } }) { open("takeover") }
        Divider()
        FeatureRow(Icons.Default.Refresh, "Atraso humano", "Espera de ${c.delayMinSec} a ${c.delayMaxSec} segundos antes de responder", c.delayOn, { on -> cfg { copy(delayOn = on) } }) { open("delay") }
        Divider()
        FeatureRow(Icons.Default.Warning, "Proteção contra loop", "Pausa se outro robô ficar respondendo", c.loopGuardOn, { on -> cfg { copy(loopGuardOn = on) } }) { open("loop") }
    }
    if (tab == 3) Panel("Ferramentas") {
        FeatureRow(Icons.Default.PlayArrow, "Testar respostas", "Simule uma mensagem sem enviar nada", null) { open("test") }
        Divider()
        FeatureRow(Icons.Default.Info, "Diagnóstico", "Por que respondeu ou não · IA ${on(c.aiDiagOn)}", null) { open("help") }
        Divider()
        FeatureRow(Icons.Default.Star, "Assistente de IA", "Peça mudanças em português: ajustes e regras; testa as regras", c.aiOn, { on -> cfg { copy(aiOn = on) } }) { open("ai") }
        Divider()
        FeatureRow(Icons.Default.Delete, "Limpar dados", if (c.cleanAutoOn) "Automático: mantém ${keepLabel(c.cleanKeepHours)}" else "Histórico, registro e estado das conversas", null) { open("clean") }
        Divider()
        FeatureRow(Icons.Default.Lock, "Backup", "Google Drive, pCloud, arquivo ou planilha", null) { open("backup") }
    }
    val ctx = LocalContext.current
    Hint("ZapResponde versão " + ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName)
}

internal val KEEP = listOf(24 to "24 horas", 168 to "7 dias", 360 to "15 dias", 720 to "30 dias")
internal fun keepLabel(h: Int) = KEEP.firstOrNull { it.first == h }?.second ?: "$h h"

@Composable
fun CleanScreen() {
    val c = Store.config
    var history by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf(true) }
    var chats by remember { mutableStateOf(true) }
    var waiting by remember { mutableStateOf(true) }
    var monitor by remember { mutableStateOf(false) }
    var chat by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf("") }
    val who = chat.trim().ifEmpty { null }
    Hint("Para testar do zero: limpa o que o app guardou das conversas. Configurações, regras e a planilha do Google nunca são apagadas.")
    Panel("Limpar agora") {
        Field("Só desta conversa (vazio = todas)", chat, help = "O nome como aparece no Histórico, ex.: Johon") { chat = it }
        val recent = remember { (Store.history.map { it.chat } + Store.events.map { it.chat }).filter { it != "(sistema)" && it.isNotBlank() }.distinct().take(8) }
        if (recent.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { n -> OutlinedButton({ chat = n }) { Text(n, maxLines = 1) } }
        }
        fun n(l: List<String>) = l.count { who == null || it == who }
        SwitchRow("Histórico de respostas", history, "${n(Store.history.map { it.chat })} respostas · aba Histórico e números da Início") { history = it }
        SwitchRow("Registro do Diagnóstico", events, "${n(Store.events.map { it.chat })} mensagens · motivos e problemas") { events = it }
        SwitchRow("Estado das conversas", chats, "${n(synchronized(Store) { Store.state.chats.keys.toList() })} conversas · pausas, boas-vindas, regras \"uma vez\", link do grupo e carga já enviados") { chats = it }
        SwitchRow("Pedidos esperando carga", waiting, "${n(synchronized(Store) { Store.state.waiting.map { it.chat } })} pedidos") { waiting = it }
        if (who == null) SwitchRow("Monitoramento interno", monitor, "${Store.traceLog.size} linhas · desligado de fábrica: é o que se analisa depois") { monitor = it }
        Button({ confirm = true }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = history || events || chats || waiting || monitor) {
            Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text(if (who == null) "Limpar de todas as conversas" else "Limpar só de $who")
        }
        if (done.isNotEmpty()) Text("✅ Apagado: $done", color = MaterialTheme.colorScheme.primary)
    }
    Panel("Limpar automaticamente") {
        SwitchRow("Limpar sozinho todo dia", c.cleanAutoOn, "Histórico e Registro mais antigos que o tempo abaixo") { cfg { copy(cleanAutoOn = it) } }
        KEEP.forEach { (h, label) ->
            Row(Modifier.fillMaxWidth().clickable { cfg { copy(cleanKeepHours = h) } }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(c.cleanKeepHours == h, { cfg { copy(cleanKeepHours = h) } })
                Text("Manter $label")
            }
        }
        Hint("O estado das conversas fica fora da limpeza automática: apagar sozinho faria os contatos receberem boas-vindas e o link do grupo de novo. Esse você limpa acima, à mão.")
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Apagar?") },
        text = { Text((if (who == null) "De todas as conversas: " else "Da conversa $who: ") + listOfNotNull(
            "histórico".takeIf { history }, "registro".takeIf { events }, "estado das conversas".takeIf { chats }, "pedidos esperando".takeIf { waiting }, "monitoramento interno".takeIf { monitor && who == null },
        ).joinToString() + ". Não dá para desfazer.") },
        confirmButton = { TextButton({
            done = listOfNotNull(Store.clear(history, events, chats, waiting, who).ifEmpty { null },
                if (monitor && who == null) "${Store.clearTrace()} linhas do monitoramento" else null).joinToString()
            confirm = false
        }) { Text("Apagar") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancelar") } },
    )
}

@Composable
fun AltScreen() {
    val c = Store.config
    Hint("Quando o motorista pede uma rota sem carga ativa, oferece a carga mais parecida em vez de só dizer que não tem.")
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
    Hint("Anota quem pediu uma rota sem carga e avisa (ou manda a carga) quando ela entrar na planilha.")
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
        NumberField("Com pedido esperando, ler a planilha a cada", c.waitReadMin, "min") { cfg { copy(waitReadMin = it.coerceIn(1, 60)) } }
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
        SwitchRow("Pausar quando você ler uma mensagem que o app não respondeu", c.takeoverOnUnanswered,
            "Funciona também no WhatsApp Web: se você abriu a conversa para responder o que o robô não respondeu, ele sai") { cfg { copy(takeoverOnUnanswered = it) } }
        SwitchRow("Pausar sempre que a conversa for lida", c.takeoverOnRead, "Até só para olhar. Mais forte: deixe desligado se você abre muitas conversas no Web.") { cfg { copy(takeoverOnRead = it) } }
    }
    Panel("Duração") {
        NumberField("Tempo de pausa", c.takeoverMin, "min") { cfg { copy(takeoverMin = it) } }
        Hint("Quando você toca na notificação ou escreve na conversa, o robô para de responder naquela conversa.")
    }
    var tick by remember { mutableStateOf(0) }
    // A copy under the lock: the reply service changes the chats while this screen reads them.
    val paused = synchronized(Store) { Store.state.chats.filter { tick >= 0 && it.value.pauseUntil > System.currentTimeMillis() }.toMap() }
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

@Composable
fun ContactsScreen(open: (String) -> Unit) {
    val c = Store.config
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) open("contactpick") }
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
        Button({ if (Contacts.allowed(ctx)) open("contactpick") else ask.launch(Manifest.permission.READ_CONTACTS) }, Modifier.fillMaxWidth()) {
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
}

/** Full-screen list of the phone's contacts with search and checkboxes. */
@Composable
internal fun ContactPicker(pad: PaddingValues, done: () -> Unit) {
    val ctx = LocalContext.current
    val selected = remember { Store.config.contactsList.lines().map { it.trim() }.filter { it.isNotEmpty() } }
    var all by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { Contacts.all(ctx) } }
    var query by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    fun finish() { cfg { copy(contactsList = chosen.distinct().joinToString("\n")) }; done() }
    // A normal screen inside the Scaffold: its padding already keeps clear of the status and navigation bars.
    Column(Modifier.fillMaxSize().padding(pad).imePadding().padding(horizontal = 16.dp)) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ chosen.removeAll(shown) }) { Text("Desmarcar visíveis") }
            TextButton({ chosen.addAll(shown.filter { it !in chosen }) }) { Text("Marcar visíveis") }
        }
        Button({ finish() }, Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text("Concluir (${chosen.size})") }
    }
}

@Composable
fun BackupScreen() {
    val ctx = LocalContext.current
    val c = Store.config
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var working by remember { mutableStateOf("") } // which button is running: "", "salvar", "restaurar"
    var ask by remember { mutableStateOf("") }     // which restore waits for confirmation: "", "arquivo", "planilha"
    // Restoring replaces settings and rules: a copy of the current ones is kept, so "Desfazer" can bring them back.
    fun restore(read: () -> String, from: String) {
        working = "restaurar"; msg = "Restaurando..."
        scope.launch {
            msg = withContext(Dispatchers.IO) {
                runCatching {
                    val text = read()
                    Store.saveUndo(); Store.importBackup(text, "backup ($from)")
                    "✅ Backup restaurado ($from): configurações e ${Store.rules.size} regras. Dá para desfazer abaixo."
                }.getOrElse { "❌ " + (it.message ?: "Não consegui restaurar.") }
            }
            Sheets.schedule(ctx); working = ""
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) msg = runCatching {
            (ctx.contentResolver.openOutputStream(uri) ?: error("o local escolhido não aceitou o arquivo")).use { it.write(Store.exportBackup().toByteArray()) }
            Store.trace("APP", "Backup salvo em arquivo"); "✅ Backup salvo."
        }.getOrElse { "❌ Não consegui salvar: ${it.message}" }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restore({ (ctx.contentResolver.openInputStream(uri) ?: error("não consegui abrir o arquivo")).use { it.readBytes().decodeToString() } }, "arquivo")
    }
    Hint("O backup leva todas as configurações (inclusive o link e a chave da planilha) e as regras do app; a chave do Gemini e o histórico ficam de fora.")
    Panel("Arquivo: Google Drive, pCloud ou celular") {
        Hint("Ao tocar, o Android abre a lista de lugares: escolha Google Drive, pCloud (com o app instalado) ou uma pasta do celular.")
        BusyButton("Salvar backup", false, enabled = working.isEmpty()) { export.launch("zapresponde-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json") }
        BusyButton("Restaurar de um arquivo", working == "restaurar", enabled = working.isEmpty(), outlined = true) { ask = "arquivo" }
    }
    Panel("Online, na planilha do Google") {
        SwitchRow("Backup diário na planilha", c.backupAutoOn, "Uma vez por dia, na aba Backup da planilha conectada") { cfg { copy(backupAutoOn = it) } }
        BusyButton("Salvar backup na planilha agora", working == "salvar", enabled = c.sheetUrl.isNotBlank() && working.isEmpty(), outlined = true) {
            working = "salvar"; msg = "Salvando o backup na planilha..."
            scope.launch {
                msg = withContext(Dispatchers.IO) {
                    runCatching {
                        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date())
                        Sheets.append(Store.config, "Backup", listOf("Data", "Backup"), listOf(listOf(date, Store.exportBackup())))
                        "✅ Backup salvo agora na aba Backup da planilha."
                    }.getOrElse { "❌ Não consegui salvar na planilha: ${it.message}" }
                }
                working = ""
            }
        }
        BusyButton("Restaurar o backup da planilha", working == "restaurar", enabled = c.sheetUrl.isNotBlank() && working.isEmpty(), outlined = true) { ask = "planilha" }
        Hint("Celular novo: conecte a planilha pelo QR (aba Planilha) e toque em \"Restaurar o backup da planilha\".")
    }
    Status(msg)
    if (Store.undo.backup.isNotEmpty()) Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ajustes e regras de antes de ${fmt(Store.undo.time)} guardados", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton({ Store.undoAi(); msg = "✅ Desfeito: ajustes e regras voltaram ao que eram." }) { Text("Desfazer") }
        }
    }
    if (ask.isNotEmpty()) Confirm(
        "Restaurar o backup?", "As configurações e regras atuais serão substituídas pelas do backup ($ask). Uma cópia das atuais fica guardada para desfazer.",
        "Restaurar", { ask = "" },
    ) {
        if (ask == "arquivo") import.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        else restore({ Sheets.readBackup(Store.config) ?: error("A planilha ainda não tem backup.") }, "planilha")
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
    Hint("Depois da conversa, manda uma vez o link do grupo para o contato acompanhar as novas cargas.")
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
    Hint("Responde pedidos de frete com a mensagem pronta da planilha de cargas: anúncio do FreteBras, link ou texto escrito do jeito do motorista.")
    Panel {
        SwitchRow("Respostas de frete", c.freteOn) { cfg { copy(freteOn = it) } }
        SwitchRow(
            "Pedido escrito de qualquer jeito", c.freteLivreOn,
            "\"Goiânia para Anápolis\", \"goiania-GO anapolis\", \"tem carga pra Brasília?\": a 1ª cidade é a origem e a 2ª o destino",
        ) { cfg { copy(freteLivreOn = it) } }
        if (c.freteLivreOn) Field("Não tratar como pedido se tiver", c.freteBlock, lines = 2, maxLines = 4,
            help = "Separadas por vírgula. Ex.: \"Fechei uma carga para Rio Verde\" conta uma novidade, não pede carga.") { cfg { copy(freteBlock = it) } }
        Divider()
        SwitchRow("Gravar na aba Leads", c.leadsOn, "Data, motorista, rota, carga e se estava disponível") { cfg { copy(leadsOn = it) } }
        SwitchRow("Alerta de carga pedida", c.alertOn, "Notificação no celular quando a carga pedida está disponível; tocar abre a conversa") { cfg { copy(alertOn = it) } }
        Divider()
        Field("Se pedirem a mesma carga de novo (até 1 h)", c.freteRepeatText, lines = 2, maxLines = 4, help = "Vai no lugar da carga repetida. Vazio = não responde.") { cfg { copy(freteRepeatText = it) } }
        Field("Quando a carga não estiver na planilha", c.freteNotFound, lines = 5, help = "Deixe vazio para não responder.") { cfg { copy(freteNotFound = it) } }
    }
    Panel {
        Hint("Quando chega \"estou disponível para carregar seu frete de Origem/UF para Destino/UF\" (ou o link do FreteBras), o app procura a rota na planilha ligada em ZapResponde > Ligar planilha de cargas. A coluna A tem a rota (\"Ponta Grossa/PR para Rio de Janeiro/RJ\") e a mensagem pronta da linha é enviada. Com mais de uma carga na rota, manda só uma: a do mesmo tipo de carga e com a coleta mais próxima (linha \"Coleta: dd/mm\" da mensagem). A planilha precisa estar ligada em Planilha > Google Sheets; sem isso, o app usa a última cópia das cargas, que não atualiza.")
    }
}
