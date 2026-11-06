package app.zapresponde.ui

import androidx.compose.runtime.LaunchedEffect
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.zapresponde.Assistant
import app.zapresponde.Audience
import app.zapresponde.Problem
import app.zapresponde.Problems
import app.zapresponde.ReplyService
import app.zapresponde.Sheets
import app.zapresponde.Store
import app.zapresponde.TRACE_TIME
import app.zapresponde.currentProblems
import app.zapresponde.diagnose
import app.zapresponde.internalLines
import app.zapresponde.traceText
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* Diagnóstico: checks, report, problems, self-test, record and internal monitoring. */

@Composable
fun HelpScreen(open: (String) -> Unit) {
    val ctx = LocalContext.current
    val access = resumeTick >= 0 && NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    val battery = resumeTick >= 0 && ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    val notifs = resumeTick >= 0 && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    var dtab by rememberSaveable { mutableStateOf(0) }
    Tabs(listOf("Resumo", "Registro", "Monitoramento", "Ajuda"), dtab) { dtab = it }
    if (dtab == 0) Panel("Verificações") {
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
    val c = Store.config
    var report by remember { mutableStateOf("") }
    var problems by remember { mutableStateOf<List<Problem>?>(null) }
    var internal by remember { mutableStateOf<List<String>>(emptyList()) }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val withAi = c.aiDiagOn && c.aiKey.isNotBlank()
    // One button: the full report and, with the AI on, its analysis right after.
    var step by remember { mutableStateOf("") }
    fun run() {
        busy = true; report = ""; answer = ""; problems = null; step = "Rodando o diagnóstico..."
        scope.launch {
            // Everything heavy off the screen thread, and the button always comes back, even after an error.
            try {
                withContext(Dispatchers.IO) {
                    val r = diagnose(ctx)
                    val p = currentProblems()
                    val i = internalLines(ctx)
                    Triple(r, p, i)
                }.let { (r, p, i) -> report = r; problems = p; internal = i }
                if (withAi) {
                    step = "A IA está analisando o relatório..."
                    answer = runCatching {
                        withContext(Dispatchers.IO) { Assistant.gemini(c.aiKey, c.aiModel, Assistant.diagnosisPrompt(report + "\n\n— Monitoramento interno (mais recentes)\n" + traceText(AI_TRACE_LINES), c, Store.allRules(), question), jsonOut = false) }
                    }.getOrElse { "❌ " + (it.message ?: "Falhou: tente de novo.") }
                }
                step = "✅ Diagnóstico pronto."
            } catch (e: Exception) {
                Store.error("diagnóstico", e); step = "❌ O diagnóstico falhou: ${e.message}"
            } finally { busy = false }
        }
    }
    fun share(text: String, title: String) =
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title))

    var selfTest by remember { mutableStateOf<List<String>>(emptyList()) }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    fun everything() = listOfNotNull(
        answer.takeIf { it.isNotEmpty() && !it.startsWith("❌") }?.let { "ANÁLISE DA IA\n\n$it" },
        selfTest.takeIf { it.isNotEmpty() }?.let { "AUTOTESTE\n" + it.joinToString("\n") },
        report.takeIf { it.isNotEmpty() },
    ).joinToString("\n\n")
    var saved by remember { mutableStateOf("") }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        saving = true; saved = "Salvando..."
        val head = everything()
        scope.launch {
            // Thousands of monitoring lines: built and written off the screen thread.
            saved = withContext(Dispatchers.IO) {
                runCatching {
                    val text = "RELATÓRIO COMPLETO ZAPRESPONDE\n\n" + head + "\n\n— Monitoramento interno (${Store.traceLog.size} linhas, mais recentes primeiro)\n" + traceText(null)
                    val out = ctx.contentResolver.openOutputStream(uri) ?: error("o local escolhido não aceitou o arquivo")
                    out.use { it.write(text.toByteArray()) }
                    Store.trace("APP", "Relatório completo salvo (${text.length / 1024} KB)")
                    "✅ Relatório salvo (${text.length / 1024} KB)."
                }.getOrElse { "❌ Não consegui salvar: ${it.message}" }
            }
            saving = false
        }
    }
    if (dtab == 0) Panel("Relatório e testes") {
        if (withAi) Field("Pergunta para a IA (opcional)", question, help = "Ex.: por que respondeu várias vezes para o Meu?", maxLines = 3) { question = it }
        BusyButton(if (withAi) "Rodar diagnóstico e análise da IA" else "Rodar diagnóstico completo", busy) { run() }
        Status(step)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ share(everything() + "\n\n— Monitoramento interno (mais recentes)\n" + traceText(300), "Enviar relatório") }, Modifier.weight(1f), enabled = !busy) { Text("Compartilhar") }
            OutlinedButton({ clipboard.setText(AnnotatedString(everything() + "\n\n— Monitoramento interno\n" + traceText(null))) }, Modifier.weight(1f), enabled = !busy) { Text("Copiar tudo") }
        }
        // The whole file (every monitoring line) goes where you choose: Google Drive, pCloud or a phone folder.
        BusyButton("Salvar relatório completo (escolher onde)", saving, enabled = !busy) {
            saveFile.launch("zapresponde-relatorio-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".txt")
        }
        Status(saved)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Runs the main features on a copy of the state, with the real settings, rules and sheet; nothing is sent.
            BusyButton("Autoteste", testing, Modifier.weight(1f), enabled = !busy, outlined = true) {
                testing = true
                scope.launch {
                    selfTest = withContext(Dispatchers.Default) {
                        runCatching { Problems.selfTest(Store.config, Store.allRules(), Store.sheet.consulta, Store.stateCopy(), LocalDateTime.now(), Sheets.scriptVersion) }
                            .getOrElse { listOf("❌ O autoteste falhou: ${it.message}") }
                    }
                    testing = false
                }
            }
            OutlinedButton({ open("clean") }, Modifier.weight(1f)) { Text("Limpar registro") }
        }
        SwitchRow("Análise pela IA junto", c.aiDiagOn, "O relatório, com o texto das últimas mensagens, vai para o Gemini") { cfg { copy(aiDiagOn = it) } }
        if (c.aiDiagOn && c.aiKey.isBlank()) Hint("Cole a chave do Gemini em Ferramentas > Assistente de IA.")
        SwitchRow("Avisar na hora se algo grave acontecer", c.problemAlertOn, "Vibra quando, por exemplo, o app responde várias vezes a um robô") { cfg { copy(problemAlertOn = it) } }
    }
    if (dtab == 0) problems?.let { list ->
        Panel(if (list.any { it.level <= 1 }) "Problemas encontrados (${list.count { it.level <= 1 }})" else "Problemas encontrados") {
            if (list.isEmpty()) Text("✅ Nenhum problema nas últimas 24 h.")
            list.forEachIndexed { i, pr ->
                if (i > 0) Divider()
                ProblemItem(pr, open)
            }
        }
    }
    if (dtab == 0 && internal.isNotEmpty()) Panel("Funcionamento interno") {
        internal.forEach { line ->
            val title = !line.startsWith(" ")
            Text(line.trim(), style = if (title) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                fontWeight = if (title) FontWeight.Bold else null, modifier = Modifier.padding(top = if (title) 10.dp else 2.dp))
        }
    }
    if (dtab == 0 && answer.isNotEmpty()) Panel("Análise da IA") {
        Text(answer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
    }
    // Problems found → the assistant proposes the fixes (each one still applied only with your tap).
    if (dtab == 0 && report.isNotEmpty() && !busy && (problems.orEmpty().any { it.level <= 1 } || internal.any { "❌" in it || "⚠️" in it })) {
        Button({
            AiPrefill.context = (if (answer.isNotEmpty() && !answer.startsWith("❌")) "ANÁLISE DA IA\n$answer\n\n" else "") + report +
                "\n\n— Monitoramento interno (mais recentes)\n" + traceText(AI_TRACE_LINES)
            AiPrefill.request = "Corrija os problemas e conflitos apontados no diagnóstico. Mude só o necessário e não mexa no que está funcionando."
            open("ai")
        }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = c.aiOn && c.aiKey.isNotBlank()) {
            Icon(Icons.Default.Star, null); Spacer(Modifier.width(6.dp)); Text("Corrigir com a IA")
        }
        if (!c.aiOn || c.aiKey.isBlank()) Hint("Para corrigir com a IA, ligue o Assistente de IA e cole a chave do Gemini.")
    }
    if (dtab == 0 && selfTest.isNotEmpty()) Panel(if (selfTest.any { it.startsWith("❌") }) "Autoteste (${selfTest.count { it.startsWith("❌") }} falha(s))" else "Autoteste") {
        selfTest.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
        Hint("Feito com os seus ajustes, regras e planilha, numa cópia das conversas: nada foi enviado. Agenda e atraso ficam de fora do teste.")
    }
    if (dtab == 1) LogPanel()
    if (dtab == 2) TracePanel()
    if (dtab == 0 && report.isNotEmpty()) Panel("Relatório") {
        Text(report, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
    }
    if (dtab == 3) Panel("Ações") {
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
    if (dtab == 3) Panel("Dicas") {
        Hint(
            "• O app só responde quando aparece notificação. Com a conversa aberta ou silenciada, não responde.\n" +
                "• Xiaomi, Samsung e Motorola: libere \"início automático\" e tire o app da economia de bateria.\n" +
                "• Android 15+: se aparecer aviso de notificação sensível, permita o acesso nas configurações.\n" +
                "• Se nada resolver, desligue e ligue o acesso às notificações ou reinicie o celular.",
        )
    }
}

/** Monitoring lines the AI reads with the diagnostic and with "Corrigir com a IA". */
internal const val AI_TRACE_LINES = 500

/** Everything the app does inside, by area: notifications, decisions, sends, sheet, AI, setting changes, errors. */
@Composable
internal fun TracePanel() {
    var cat by rememberSaveable { mutableStateOf("Tudo") }
    var shown by remember { mutableStateOf(60) }
    val tick = liveTick()
    val all = remember(tick) { Store.traceLog }
    val cats = listOf("Tudo") + all.map { it.cat }.distinct().sorted()
    val list = remember(all, cat) { if (cat == "Tudo") all else all.filter { it.cat == cat } }
    Panel("Monitoramento interno (${all.size})") {
        Hint("Tudo o que acontece por dentro do app, com horário em milissegundos: notificações, decisões e quanto levaram, envios, planilha, IA, mudanças de ajustes e regras (e quem mudou) e erros com o rastro do código. Guarda as últimas 3000 linhas.")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { k -> FilterChip(cat == k, { cat = k; shown = 60 }, { Text(k) }) }
        }
        if (list.isEmpty()) Hint("Nada registrado ainda.")
        list.take(shown).forEach { t ->
            Text("${synchronized(TRACE_TIME) { TRACE_TIME.format(Date(t.time)) }} [${t.cat}] ${t.msg}", style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace, color = if (t.cat == "ERRO") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 2.dp))
        }
        if (list.size > shown) TextButton({ shown += 60 }, Modifier.fillMaxWidth()) { Text("Mostrar mais (${list.size - shown})") }
    }
}

/** The live record of every message and event, with filters; tapping a line shows the path of the decision. */
@Composable
internal fun LogPanel() {
    var filter by rememberSaveable { mutableStateOf("Tudo") }
    var shown by remember { mutableStateOf(40) }
    var open by remember { mutableStateOf(-1L) }
    val tick = liveTick()
    val all = remember(tick) { Store.events }
    val filters = linkedMapOf<String, (app.zapresponde.LogEntry) -> Boolean>(
        "Tudo" to { true },
        "Falhas" to { e -> e.chat == Store.INTERNAL || listOf("Falhou", "não enviado", "não atualizou", "cancelad", "Sem botão").any { it in e.result } },
        "Respondidas" to { e -> e.result.startsWith("Respondido") || e.result.startsWith("📨") },
        "Sem resposta" to { e -> e.result.startsWith("Nenhuma regra") || e.result.startsWith("Frete fora") },
        "Pausas" to { e -> "pausad" in e.result },
        "Erros internos" to { e -> e.chat == Store.INTERNAL },
        "Sistema" to { e -> e.chat == "(sistema)" },
    )
    val list = remember(all, filter) { all.filter(filters.getValue(filter)) }
    Panel("Registro (${all.size})") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.keys.forEach { k -> FilterChip(filter == k, { filter = k; shown = 40 }, { Text(k) }) }
        }
        if (list.isEmpty()) Hint("Nada aqui.")
        list.take(shown).forEach { e ->
            val mark = when {
                e.chat == Store.INTERNAL -> "❌"
                e.result.startsWith("Respondido") || e.result.startsWith("📨") -> "✅"
                "pausad" in e.result -> "⏸"
                else -> "·"
            }
            Column(Modifier.fillMaxWidth().clickable { open = if (open == e.time) -1 else e.time }.padding(vertical = 4.dp)) {
                Text("${SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(e.time))} $mark ${e.chat}" +
                    (if (e.text.isNotEmpty()) ": \"${e.text.take(60).replace('\n', ' ')}\"" else "") + " → ${e.result}",
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                if (open == e.time) e.trace.ifEmpty { listOf("(sem caminho de decisão guardado)") }.forEach {
                    Text("   $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (list.size > shown) TextButton({ shown += 40 }, Modifier.fillMaxWidth()) { Text("Mostrar mais (${list.size - shown})") }
    }
}

/** Changes every 3 s while the screen is open: the record and the monitoring are plain lists, not observed state. */
@Composable
private fun liveTick(): Int {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(3_000); tick++ } }
    return tick
}

/** One problem: what happened, why, and a button that fixes it when one tap can. */
@Composable
internal fun ProblemItem(p: Problem, open: (String) -> Unit) {
    var done by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("${p.icon} ${p.title}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        val fix = p.fix ?: return@Column
        if (done) { Text("✅ Feito", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium); return@Column }
        TextButton({
            if (fix.startsWith("open:")) open(fix.removePrefix("open:"))
            else if (fix.startsWith("exclude:")) {
                val chat = fix.removePrefix("exclude:")
                val c = Store.config
                // Exceptions only make sense in "Todos, exceto minha lista"; other modes are changed by hand on the screen.
                if (!c.contactsOn || c.contactsMode == Audience.ALL || c.contactsMode == Audience.EXCEPT_LIST) {
                    cfg { copy(contactsOn = true, contactsMode = Audience.EXCEPT_LIST, contactsList = (contactsList.trim() + "\n" + chat).trim()) }
                    done = true
                } else open("contacts")
            }
        }) { Text(if (fix.startsWith("exclude:")) "Colocar nas exceções" else "Corrigir") }
    }
}

@Composable
internal fun Check(label: String, ok: Boolean, fix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, 32)
        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp))
        if (!ok) TextButton(fix) { Text("Corrigir") }
    }
}
