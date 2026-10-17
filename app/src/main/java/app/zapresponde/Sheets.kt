package app.zapresponde

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Talks to the Apps Script web app pasted into the user's spreadsheet (see apps-script/Codigo.gs). */
object Sheets {
    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000; readTimeout = 30_000
    }

    private fun readBody(c: HttpURLConnection): String {
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code >= 400) error("HTTP $code")
        return body
    }

    private fun parse(body: String): JsonObject {
        val o = runCatching { Store.json.parseToJsonElement(body).jsonObject }
            .getOrElse { error("Resposta inválida. Confira se o link é o do Apps Script implantado.") }
        o["error"]?.let { error("Planilha recusou: ${it.jsonPrimitive.content}") }
        return o
    }

    fun read(cfg: Config): Pair<List<List<String>>, List<List<String>>> {
        val url = cfg.sheetUrl.trim() + "?key=" + URLEncoder.encode(cfg.sheetKey, "UTF-8")
        val o = parse(readBody(open(url)))
        fun tab(name: String) = (o[name] as? JsonArray).orEmpty().map { row -> row.jsonArray.map { it.jsonPrimitive.content } }
        return tab("Respostas") to tab("Consulta")
    }

    fun append(cfg: Config, sheet: String, header: List<String>, rows: List<List<String>>) {
        val payload = buildJsonObject {
            put("key", cfg.sheetKey); put("sheet", sheet)
            putJsonArray("header") { header.forEach { add(it) } }
            putJsonArray("rows") { rows.forEach { r -> addJsonArray { r.forEach { add(it) } } } }
        }.toString()
        val c = open(cfg.sheetUrl.trim()).apply {
            requestMethod = "POST"; doOutput = true; instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use { it.write(payload.toByteArray()) }
        // Apps Script runs doPost, then answers 302 to a URL that holds the result; fetch it with GET.
        val body = if (c.responseCode in 300..399) readBody(open(c.getHeaderField("Location"))) else readBody(c)
        parse(body)
    }

    fun historyRow(e: HistoryEntry) = listOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(e.time)),
        e.app, e.chat, if (e.isGroup) "grupo" else "contato", e.text, e.reply, e.rule,
    )

    fun schedule(ctx: Context) {
        val cfg = Store.config
        val wm = WorkManager.getInstance(ctx)
        if (!cfg.sheetOn || cfg.sheetUrl.isBlank()) { wm.cancelUniqueWork("sync"); return }
        val req = PeriodicWorkRequestBuilder<SyncWorker>(maxOf(15, cfg.sheetSyncMin).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build()
        wm.enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun syncNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("sync-now", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Store.init(applicationContext)
        val cfg = Store.config
        if (!cfg.sheetOn || cfg.sheetUrl.isBlank()) return@withContext Result.success()
        try {
            val box = Store.outbox
            if (box.historico.isNotEmpty()) Sheets.append(
                cfg, "Historico", listOf("Data", "App", "Conversa", "Tipo", "Mensagem", "Resposta", "Regra"), box.historico,
            )
            if (box.pedidos.isNotEmpty()) {
                val qs = cfg.formQuestions.lines().map { it.trim() }.filter { it.isNotEmpty() }
                Sheets.append(cfg, "Pedidos", listOf("Data", "Conversa") + qs, box.pedidos)
            }
            Store.sent(box.historico.size, box.pedidos.size)
            val (respostas, consulta) = Sheets.read(cfg)
            Store.setSheet(SheetCache(Engine.rulesFromSheet(respostas), consulta, System.currentTimeMillis()))
            Result.success()
        } catch (e: Exception) {
            Store.setSheet(Store.sheet.copy(error = e.message ?: e.toString()))
            Result.retry()
        }
    }
}
