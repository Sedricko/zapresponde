package app.zapresponde.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.zapresponde.Engine
import app.zapresponde.Sheets
import app.zapresponde.Store
import app.zapresponde.fmt
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* Planilha: Google Sheets connection, QR code and sync. */

// ---------- Planilha ----------

internal fun syncText(): String {
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
        // The sync runs in the background; the spinner stays until the sheet's time changes (or 60 s pass).
        var syncFrom by remember { mutableStateOf(0L) }
        val syncing = syncFrom > 0 && Store.sheet.syncedAt <= syncFrom && System.currentTimeMillis() - syncFrom < 60_000
        LaunchedEffect(syncFrom) { if (syncFrom > 0) { kotlinx.coroutines.delay(60_000); syncFrom = 0 } }
        BusyButton("Sincronizar agora", syncing, Modifier.padding(bottom = 8.dp), enabled = c.sheetOn) {
            syncFrom = System.currentTimeMillis(); Sheets.schedule(ctx); Sheets.syncNow(ctx)
        }
    }
    val scope = rememberCoroutineScope()
    var testMsg by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    fun test() {
        if (testing) return
        testing = true; testMsg = "Testando conexão..."
        scope.launch {
            testMsg = withContext(Dispatchers.IO) {
                runCatching {
                    val (resp, cons) = Sheets.read(Store.config)
                    "✅ Conectado! ${Engine.rulesFromSheet(resp).size} respostas e ${maxOf(0, cons.size - 1)} linhas de consulta."
                }.getOrElse { "❌ Não conectou: ${it.message}" }
            }
            testing = false
            if (testMsg.startsWith("✅")) Sheets.syncNow(ctx)
        }
    }
    Panel("Conexão") {
        Button({
            val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            GmsBarcodeScanning.getClient(ctx, opts).startScan()
                .addOnSuccessListener { b ->
                    val p = Engine.parseConnectQr(b.rawValue.orEmpty())
                    if (p == null) testMsg = "❌ Esse QR code não é do ZapResponde."
                    else {
                        cfg { copy(sheetUrl = p.first, sheetKey = p.second, sheetOn = true) }
                        Sheets.schedule(ctx); test()
                    }
                }
                .addOnFailureListener { testMsg = "❌ Não abriu o leitor: ${it.message}" }
        }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Ler QR code da planilha")
        }
        BusyButton("Testar conexão", testing, enabled = c.sheetUrl.isNotBlank(), outlined = true) { test() }
        Status(testMsg)
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
