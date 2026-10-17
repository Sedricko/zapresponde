package app.zapresponde

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rastro do serviço de notificações e leitura do ambiente, para o Diagnóstico explicar a causa. */
object Health {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("health", Context.MODE_PRIVATE)
    fun mark(c: Context, key: String) = p(c).edit().putLong(key, System.currentTimeMillis()).apply()
    fun get(c: Context, key: String) = p(c).getLong(key, 0L)
    fun count(c: Context, key: String) = p(c).edit().putInt(key, p(c).getInt(key, 0) + 1).apply()

    /** O listener está conectado agora (não apenas criado). */
    fun connected(c: Context) = get(c, "connectedAt") > get(c, "disconnectedAt")

    /** Linhas de ambiente e de rastro para o relatório do Diagnóstico. */
    fun report(c: Context): List<String> {
        val f = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR"))
        fun t(ms: Long) = if (ms == 0L) "nunca" else f.format(Date(ms))
        val out = mutableListOf<String>()
        out += "ℹ️ Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        out += "ℹ️ Serviço: conectou ${t(get(c, "connectedAt"))} · desconectou ${t(get(c, "disconnectedAt"))}"
        out += "ℹ️ Notificações do WhatsApp vistas: última ${t(get(c, "notifAt"))} · com botão Responder ${t(get(c, "replyNotifAt"))}"
        val ign = p(c).getInt("unreadable", 0)
        if (ign > 0) out += "⚠️ $ign notificações de mensagem não puderam ser lidas (sem título ou texto)"
        if ((c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted)
            out += "❌ Restrição de segundo plano ativa (Android > Apps > ZapResponde > Bateria > Sem restrições)"
        runCatching {
            val b = (c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).appStandbyBucket
            if (b >= 40) out += "⚠️ Prioridade do app no Android é baixa (standby $b)"
        }
        if ((c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).restrictBackgroundStatus ==
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        ) out += "⚠️ Economia de dados ativa: a sincronização com a planilha pode falhar em segundo plano"
        return out
    }
}
