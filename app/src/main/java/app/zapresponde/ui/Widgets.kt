package app.zapresponde.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String = "", onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onChange)
    }
}

/** A feature card: its own on/off switch plus a "Configurar" button, as every feature must have. */
@Composable
fun FeatureCard(title: String, subtitle: String, on: Boolean?, onToggle: (Boolean) -> Unit = {}, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onOpen() }) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                TextButton(onOpen, Modifier.padding(0.dp)) { Text("Configurar") }
            }
            if (on != null) Switch(on, onToggle)
        }
    }
}

@Composable
fun Field(label: String, value: String, lines: Int = 1, help: String = "", onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth().padding(vertical = 4.dp),
        label = { Text(label) }, singleLine = lines == 1, minLines = lines,
        supportingText = if (help.isEmpty()) null else ({ Text(help) }),
    )
}

@Composable
fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { t -> text = t.filter { it.isDigit() }.take(6); text.toIntOrNull()?.let(onChange) },
        Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
fun <T> Choice(label: String, value: T, options: List<T>, name: (T) -> String, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(name(value) + "  ▾", style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(name(o)) }, { onChange(o); open = false }) }
        }
    }
}

private val DAYS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

@Composable
fun DaysPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DAYS.forEachIndexed { i, d ->
            val day = i + 1
            FilterChip(day in days, { onChange(if (day in days) days - day else days + day) }, { Text(d) })
        }
    }
}

@Composable
fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}
