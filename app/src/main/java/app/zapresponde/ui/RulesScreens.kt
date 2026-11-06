package app.zapresponde.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zapresponde.Freq
import app.zapresponde.Groups
import app.zapresponde.Kind
import app.zapresponde.Match
import app.zapresponde.ReplyMode
import app.zapresponde.Rule
import app.zapresponde.Store
import app.zapresponde.Who

/* Regras: the list, a new rule and the rule editor. */

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
internal fun NewRuleDialog(dismiss: () -> Unit, create: (Rule) -> Unit) {
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
    if (r.enabled) RuleTestPanel(only = id)
}

const val VARS = "Variáveis: {nome} {primeiro_nome} {mensagem} {data} {hora} {dia_semana} {saudacao} {grupo} {contador}"
