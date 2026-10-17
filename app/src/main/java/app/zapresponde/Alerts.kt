package app.zapresponde

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** The app's own notifications: load alerts and replies waiting for approval. */
object Alerts {
    private const val CHANNEL = "alertas"

    private fun channel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Alertas do ZapResponde", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun base(ctx: Context, title: String, text: String, open: PendingIntent?) =
        NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open) // WhatsApp's own intent: opens that chat
            .setAutoCancel(true)

    private fun post(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        channel(ctx)
        runCatching { NotificationManagerCompat.from(ctx).notify(id, b.build()) } // no permission: skip silently
    }

    fun show(ctx: Context, title: String, text: String, open: PendingIntent?) =
        post(ctx, (System.currentTimeMillis() % 100_000).toInt() + 100_000, base(ctx, title, text, open))

    fun approval(ctx: Context, id: Int, chat: String, reply: String, open: PendingIntent?) {
        fun act(send: Boolean) = PendingIntent.getBroadcast(
            ctx, id * 2 + if (send) 1 else 0,
            Intent(ctx, ApprovalReceiver::class.java).putExtra("id", id).putExtra("send", send),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(ctx, id, base(ctx, "Aprovar resposta para $chat", reply, open).setAutoCancel(false)
            .addAction(0, "Enviar", act(true)).addAction(0, "Descartar", act(false)))
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}

class ApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val svc = ReplyService.instance
        if (svc == null) Alerts.cancel(ctx, id) else svc.approve(id, intent.getBooleanExtra("send", false))
    }
}
