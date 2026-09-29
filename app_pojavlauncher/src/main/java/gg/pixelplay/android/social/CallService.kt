package gg.pixelplay.android.social

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import gg.pixelplay.android.MainActivity
import gg.pixelplay.android.PixelGraph
import net.kdt.pojavlaunch.R

/** Keeps the microphone and socket alive while a call is in progress (screen off / app in background). */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANGUP) {
            PixelGraph.social.hangup()
            stopSelf()
            return START_NOT_STICKY
        }
        val peer = intent?.getStringExtra(EXTRA_PEER).orEmpty()
        val notif = Notifications.ongoingCall(this, peer)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        runCatching { ServiceCompat.startForeground(this, Notifications.ID_CALL, notif, type) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        NotificationManagerCompat.from(this).cancel(Notifications.ID_CALL)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PEER = "peer"
        const val ACTION_HANGUP = "gg.pixelplay.android.HANGUP"

        fun start(ctx: Context, peer: String) {
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, CallService::class.java).putExtra(EXTRA_PEER, peer))
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CallService::class.java))
        }
    }
}

object Notifications {
    const val CH_MESSAGES = "messages"
    const val CH_CALLS = "calls"
    const val CH_ONGOING = "ongoing"
    const val CH_LAUNCH = "launch"
    const val ID_CALL = 1001
    const val ID_INCOMING = 1002
    const val ID_LAUNCH = 1003
    const val EXTRA_OPEN_CHAT = "open_chat"
    const val EXTRA_CALL_ACTION = "call_action"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_MESSAGES, "Mensajes", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_CALLS, "Llamadas entrantes", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_ONGOING, "Llamada en curso", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_LAUNCH, "Instalación y juego", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    private fun openApp(ctx: Context, req: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            req,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun canPost(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    @Suppress("MissingPermission")
    fun message(ctx: Context, from: String, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_pixel)
            .setContentTitle(from)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, from.hashCode()) { putExtra(EXTRA_OPEN_CHAT, from) })
            .build()
        NotificationManagerCompat.from(ctx).notify(from.lowercase().hashCode(), n)
    }

    @Suppress("MissingPermission")
    fun incomingCall(ctx: Context, from: String) {
        if (!canPost(ctx)) return
        val full = openApp(ctx, 2001) { putExtra(EXTRA_OPEN_CHAT, from) }
        val accept = openApp(ctx, 2002) { putExtra(EXTRA_CALL_ACTION, "accept") }
        val decline = openApp(ctx, 2003) { putExtra(EXTRA_CALL_ACTION, "decline") }
        val n = NotificationCompat.Builder(ctx, CH_CALLS)
            .setSmallIcon(R.drawable.ic_stat_pixel)
            .setContentTitle("Llamada de $from")
            .setContentText("Voz Pixel")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Rechazar", decline)
            .addAction(0, "Aceptar", accept)
            .setTimeoutAfter(45_000)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_INCOMING, n)
    }

    fun cancelIncoming(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_INCOMING)

    fun ongoingCall(ctx: Context, peer: String): Notification {
        val hangup = PendingIntent.getService(
            ctx, 3001,
            Intent(ctx, CallService::class.java).setAction(CallService.ACTION_HANGUP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_pixel)
            .setContentTitle("En llamada con $peer")
            .setContentText("Tocá para volver a Pixel Play")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setContentIntent(openApp(ctx, 3002))
            .addAction(0, "Colgar", hangup)
            .build()
    }

    @Suppress("MissingPermission")
    fun launchProgress(ctx: Context, title: String, text: String, progress: Float?) {
        if (!canPost(ctx)) return
        val b = NotificationCompat.Builder(ctx, CH_LAUNCH)
            .setSmallIcon(R.drawable.ic_stat_pixel)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(progress != null)
            .setContentIntent(openApp(ctx, 4001))
        if (progress != null) b.setProgress(100, (progress * 100).toInt(), progress < 0f)
        NotificationManagerCompat.from(ctx).notify(ID_LAUNCH, b.build())
    }

    fun cancelLaunch(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_LAUNCH)
}
