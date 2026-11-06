package app.zapresponde.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zapresponde.AiConfig
import app.zapresponde.Assistant
import app.zapresponde.Contacts
import app.zapresponde.Engine
import app.zapresponde.Incoming
import app.zapresponde.Store
import app.zapresponde.fmt
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* Assistente de IA (changes proposed by the AI), rule tests by the AI and Testar respostas. */

/** A request handed to the assistant by another screen ("Corrigir com a IA" in the Diagnóstico): text + context. */
object AiPrefill { var request by mutableStateOf(""); var context by mutableStateOf("") }

@Composable
fun AssistantScreen() {
    val c = Store.config
    val scope = rememberCoroutineScope()
    var request by rememberSaveable { mutableStateOf("") }
    var context by remember { mutableStateOf("") }
    var proposal by remember { mutableStateOf<AiConfig.Proposal?>(null) }
    var chosen by remember { mutableStateOf(setOf<Int>()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun ask() {
        busy = true; status = ""; proposal = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AiConfig.parse(Assistant.gemini(c.aiKey, c.aiModel, AiConfig.prompt(request, Store.config, Store.rules, context)), Store.config, Store.rules)
                }
            }.fold({ p ->
                proposal = p
                chosen = p.changes.indices.filter { p.changes[it].problems.isEmpty() }.toSet()
                status = if (p.changes.isEmpty()) "ℹ️ A IA não propôs nenhuma mudança." else ""
            }, { status = "❌ " + (it.message ?: "Falhou: tente de novo.") })
            busy = false
        }
    }
    // Arriving from the Diagnóstico's "Corrigir com a IA": the request is filled in and sent right away.
    LaunchedEffect(AiPrefill.request) {
        if (AiPrefill.request.isNotEmpty() && c.aiOn && c.aiKey.isNotBlank()) {
            request = AiPrefill.request; context = AiPrefill.context
            AiPrefill.request = ""; AiPrefill.context = ""
            ask()
        }
    }
    Hint("Peça em português o que quer mudar no app: a IA propõe as mudanças (ajustes e regras) e você escolhe o que aplicar. Nada muda sem o seu toque.")
    Panel {
        SwitchRow("Ativar assistente de IA", c.aiOn) { cfg { copy(aiOn = it) } }
        Field("Chave da API do Gemini", c.aiKey, help = "Crie grátis em aistudio.google.com/apikey e cole aqui") { cfg { copy(aiKey = it.trim()) } }
        Advanced { Field("Modelo", c.aiModel, help = "Padrão: gemini-flash-latest") { cfg { copy(aiModel = it.trim()) } } }
    }
    Panel("O que você quer mudar?") {
        Field("Pedido", request, lines = 2, maxLines = 5,
            help = "Ex.: \"muda o link do grupo para 30 minutos\" · \"coloca Frete Bras Publicações nas exceções\" · \"quando perguntarem quanto tá pagando, responde que o valor está na carga\"") { request = it; context = "" }
        BusyButton(if (busy) "A IA está pensando..." else "Pedir à IA", busy, Modifier.fillMaxWidth().padding(vertical = 8.dp),
            enabled = c.aiOn && c.aiKey.isNotBlank() && request.isNotBlank()) { ask() }
        if (context.isNotEmpty()) Hint("A IA também recebeu o diagnóstico mais recente.")
        if (!busy) Status(status)
        if (!c.aiOn || c.aiKey.isBlank()) Hint("Ligue o assistente e cole a chave acima. Vão para o Gemini os seus ajustes e regras (sem chaves nem link da planilha) e o seu pedido.")
    }
    proposal?.let { p ->
        if (p.changes.isNotEmpty() || p.ignored.isNotEmpty()) Panel("Mudanças propostas") {
            if (p.explanation.isNotBlank()) Text(p.explanation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
            p.changes.forEachIndexed { i, ch ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().clickable(enabled = ch.problems.isEmpty()) { chosen = if (i in chosen) chosen - i else chosen + i }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top) {
                    Checkbox(i in chosen, { chosen = if (it) chosen + i else chosen - i }, enabled = ch.problems.isEmpty())
                    Column(Modifier.weight(1f)) {
                        Text(ch.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Antes: ${ch.before}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Depois: ${ch.after}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        ch.problems.forEach { Text("❌ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            p.ignored.forEach { Text("ℹ️ Não aplicável: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (p.changes.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ proposal = null; status = "" }, Modifier.weight(1f)) { Text("Descartar") }
                Button({
                    Store.saveUndo()
                    val (newCfg, newRules) = AiConfig.apply(Store.config, Store.rules, p.changes.filterIndexed { i, _ -> i in chosen })
                    Store.trace("IA", "Assistente: aplicadas ${chosen.size} de ${p.changes.size} mudança(s) propostas para \"${request.take(100)}\"")
                    Store.updateConfig(newCfg, "IA"); Store.updateRules(newRules, "IA")
                    status = "✅ ${chosen.size} mudança(s) aplicada(s). Dá para desfazer logo abaixo."
                    proposal = null; request = ""
                }, Modifier.weight(1f), enabled = chosen.isNotEmpty()) { Text("Aplicar ${chosen.size}") }
            }
        }
    }
    if (Store.undo.backup.isNotEmpty()) Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Última mudança da IA: ${fmt(Store.undo.time)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton({ Store.undoAi(); status = "✅ Desfeito: ajustes e regras voltaram ao que eram." }) { Text("Desfazer") }
        }
    }
    RuleTestPanel()
}

@Composable
internal fun RuleTestPanel(only: String? = null) {
    val c = Store.config
    val scope = rememberCoroutineScope()
    val active = Store.allRules().filter { it.enabled }
    var picked by remember { mutableStateOf(if (only != null) setOf(only) else active.map { it.id }.toSet()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<List<Pair<Assistant.RuleTest, String>>, List<String>>?>(null) }
    if (active.isEmpty()) { Panel("Testar regras com a IA") { Hint("Nenhuma regra ativa para testar.") }; return }
    Panel("Testar regras com a IA") {
        if (!c.aiOn || c.aiKey.isBlank()) Hint("Ligue o Assistente de IA e cole a chave do Gemini em Ferramentas > Assistente de IA.")
        Hint(if (only != null) "A IA inventa mensagens de clientes para esta regra e o app confere qual regra responderia cada uma, na ordem real." else "Escolha as regras. A IA inventa mensagens de clientes e o app confere qual regra responderia cada uma, na ordem real. Só as regras vão ao Gemini.")
        if (only == null) active.forEach { r ->
            Row(Modifier.fillMaxWidth().clickable { picked = if (r.id in picked) picked - r.id else picked + r.id }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(r.id in picked, { picked = if (it) picked + r.id else picked - r.id })
                Text(r.name)
            }
        }
        BusyButton(if (busy) "A IA está testando..." else "Testar ${picked.size} regra(s)", busy, Modifier.fillMaxWidth().padding(vertical = 8.dp),
            enabled = c.aiOn && c.aiKey.isNotBlank() && picked.isNotEmpty()) {
            busy = true; status = ""; result = null
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
        }
        if (status.isNotEmpty()) Status("❌ $status")
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
