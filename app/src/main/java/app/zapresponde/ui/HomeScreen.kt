package app.zapresponde.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.zapresponde.Engine
import app.zapresponde.Health
import app.zapresponde.Problems
import app.zapresponde.ReplyService
import app.zapresponde.Sheets
import app.zapresponde.Store
import app.zapresponde.fmt

/* Início (status, attention, today's numbers) and Histórico. */

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
    val today = remember(h) { h.filter { now - it.time < 86_400_000 } }
    val kinds = remember(today) { today.groupingBy { kindOf(it).first }.eachCount() }
    val waiting = Store.state.waiting.size
    val approvals = ReplyService.instance?.approvalsPending() ?: 0

    // Only what needs you: hidden when everything is fine.
    val attention = buildList<Pair<String, String?>> {
        if (approvals > 0) add("$approvals resposta(s) esperando você tocar em Enviar nas notificações" to null)
        if (c.sheetOn && Store.sheet.error.isNotEmpty()) add("Planilha com erro: ${Store.sheet.error.take(80)}" to "help")
        else if (c.sheetOn && Store.sheet.syncedAt > 0 && now - Store.sheet.syncedAt > 2 * 3_600_000L) add("Planilha sem atualizar desde ${fmt(Store.sheet.syncedAt)}" to "help")
        if (!c.freteOn && (c.freteLivreOn || c.altOn || c.waitOn)) add("Funções de frete ligadas, mas Respostas de frete está desligado" to "frete")
    }
    if (attention.isNotEmpty()) Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Precisa da sua atenção", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            attention.forEach { (text, screen) ->
                Text("• $text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = screen != null) { screen?.let(open) }.padding(vertical = 4.dp))
            }
        }
    }

    Text("HOJE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("Cargas enviadas", (kinds["Carga enviada"] ?: 0).toString(), Modifier.weight(1f), KIND_COLORS["Carga enviada"])
        Stat("Indisponíveis", (kinds["Indisponível"] ?: 0).toString(), Modifier.weight(1f), KIND_COLORS["Indisponível"])
        Stat("Outra carga", (kinds["Outra carga"] ?: 0).toString(), Modifier.weight(1f), KIND_COLORS["Outra carga"])
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("Links do grupo", (kinds["Link do grupo"] ?: 0).toString(), Modifier.weight(1f), KIND_COLORS["Link do grupo"])
        Stat("Esperando carga", waiting.toString(), Modifier.weight(1f))
        Stat("Respostas", today.size.toString(), Modifier.weight(1f))
    }

    val routes = remember(today) {
        today.mapNotNull { e -> routeOf(e) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
    }
    if (routes.isNotEmpty()) Panel("Rotas mais pedidas hoje") {
        routes.forEachIndexed { i, (route, n) ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(route, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("$n pedido(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
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
internal fun Stat(label: String, value: String, modifier: Modifier, color: Color? = null) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color ?: MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Histórico ----------

@Composable
internal fun HistoryItem(e: app.zapresponde.HistoryEntry) {
    var open by remember { mutableStateOf(false) }
    val (kind, color) = kindOf(e)
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.chat, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmt(e.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(e.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text(e.reply, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(kind, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp))
            Text("  ${e.rule}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.trace.isNotEmpty()) Text(if (open) "ocultar" else "por quê?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (open) {
            Text(e.reply, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            if (e.trace.isNotEmpty()) {
                Text("Caminho da decisão", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                e.trace.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** Colors of the reply kinds, the same in Início and Histórico. */
internal val KIND_COLORS = mapOf(
    "Carga enviada" to Color(0xFF1E8E3E), "Indisponível" to Color(0xFFD93025), "Outra carga" to Color(0xFFE37400),
    "Link do grupo" to Color(0xFF1A73E8), "Regra" to Color(0xFF7A7A7A),
)

/** What a reply was, for the colored label and the counters: freight sent, unavailable, another load, group link or a rule. */
internal fun kindOf(e: app.zapresponde.HistoryEntry): Pair<String, Color> {
    val k = e.kind.ifEmpty { Problems.replyKind(e.rule, e.reply) }
    return k to (KIND_COLORS[k] ?: Color(0xFF7A7A7A))
}

/** Route asked in a freight reply ("GOIANIA/GO → ANAPOLIS/GO"), saved with the reply. */
internal fun routeOf(e: app.zapresponde.HistoryEntry): String? = e.route.ifEmpty { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val all = Store.history
    var filter by rememberSaveable { mutableStateOf("Todos") }
    var search by rememberSaveable { mutableStateOf("") }
    var shown by rememberSaveable { mutableStateOf(30) }
    val h = remember(all, filter, search) {
        val q = Engine.normalize(search)
        all.filter { e ->
            (filter == "Todos" || kindOf(e).first == filter) &&
                (q.isEmpty() || Engine.normalize(e.chat).contains(q) || Engine.normalize(e.text).contains(q))
        }
    }
    Hint("${all.size} respostas guardadas · ${all.count { System.currentTimeMillis() - it.time < 86_400_000 }} nas últimas 24h · toque numa resposta para ver por quê")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("Todos") + KIND_COLORS.keys).forEach { k ->
            FilterChip(filter == k, { filter = k }, { Text(k) })
        }
    }
    Field("Buscar contato ou mensagem", search) { search = it }
    Panel {
        if (h.isEmpty()) Hint(if (all.isEmpty()) "Nenhuma resposta enviada ainda." else "Nada com esse filtro.")
        h.take(shown).forEachIndexed { i, e ->
            if (i > 0) Divider()
            HistoryItem(e)
        }
        if (h.size > shown) TextButton({ shown += 30 }, Modifier.fillMaxWidth()) { Text("Mostrar mais (${h.size - shown})") }
    }
}
