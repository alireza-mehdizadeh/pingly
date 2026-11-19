package ir.byalireza.pingly.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.byalireza.pingly.MainActivity
import ir.byalireza.pingly.R
import ir.byalireza.pingly.domain.Avatar
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.ui.fullscreen.FullscreenActivity
import ir.byalireza.pingly.ui.theme.PinglyColors
import androidx.compose.ui.graphics.toArgb

/** Notification channels and every notification Pingly posts. */
object Notifications {
    const val CHANNEL_SERVICE = "pingly_service"
    const val CHANNEL_PINGS = "pingly_pings"
    const val CHANNEL_FULLSCREEN = "pingly_fullscreen"
    const val CHANNEL_UPDATES = "pingly_updates"
    const val CHANNEL_HEALTH = "pingly_health"

    const val ID_SERVICE = 1
    const val ID_HEALTH = 2
    const val ID_SESSION = 3

    private val brandColor = PinglyColors.Pink.toArgb()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        fun channel(id: String, name: Int, desc: Int, importance: Int, silent: Boolean = true) =
            NotificationChannel(id, context.getString(name), importance).apply {
                description = context.getString(desc)
                // Pingly plays its own sounds and vibrations, so channels stay quiet.
                if (silent) {
                    setSound(null, null)
                    enableVibration(false)
                }
                setShowBadge(id != CHANNEL_SERVICE)
            }
        manager.createNotificationChannels(
            listOf(
                channel(CHANNEL_SERVICE, R.string.channel_service, R.string.channel_service_desc, NotificationManager.IMPORTANCE_LOW),
                channel(CHANNEL_PINGS, R.string.channel_pings, R.string.channel_pings_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(CHANNEL_FULLSCREEN, R.string.channel_fullscreen, R.string.channel_fullscreen_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(CHANNEL_UPDATES, R.string.channel_updates, R.string.channel_updates_desc, NotificationManager.IMPORTANCE_DEFAULT, silent = false),
                channel(CHANNEL_HEALTH, R.string.channel_health, R.string.channel_health_desc, NotificationManager.IMPORTANCE_HIGH, silent = false),
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context, requestCode: Int = 0): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The persistent foreground-service notification. Deliberately has no "pause" action. */
    fun service(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_pingly)
            .setColor(brandColor)
            .setContentTitle(context.getString(R.string.service_title_ready))
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .build()

    fun notificationIdFor(eventId: String): Int = 1000 + (eventId.hashCode() and 0x7FFFF)

    /**
     * Posts the notification for an event from the partner. When `fullScreen` is set, Android
     * opens [FullscreenActivity] straight away on a locked/sleeping phone.
     */
    @SuppressLint("MissingPermission") // checked by canPost()
    fun partnerEvent(context: Context, event: PinglyEvent, partnerName: String, partnerAvatar: String?, fullScreen: Boolean) {
        if (!canPost(context)) return
        val avatar = Avatar.fromId(partnerAvatar).emoji
        val opensFullscreen = event.type == EventType.Image || event.type == EventType.Message || event.type == EventType.Question
        val id = notificationIdFor(event.id)
        val contentIntent = if (opensFullscreen) FullscreenActivity.pendingIntent(context, event.id) else openAppIntent(context, id)
        val text = event.summary(partnerName)

        val builder = NotificationCompat.Builder(context, if (fullScreen) CHANNEL_FULLSCREEN else CHANNEL_PINGS)
            .setSmallIcon(R.drawable.ic_stat_pingly)
            .setColor(brandColor)
            .setContentTitle("$avatar $partnerName")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (fullScreen) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setWhen(event.createdAt)
            .setShowWhen(true)
            .setContentIntent(contentIntent)

        if (fullScreen) builder.setFullScreenIntent(FullscreenActivity.pendingIntent(context, event.id), true)
        if (event.type == EventType.Ping) {
            builder.addAction(0, "💗 پینگ برگردون", NotificationActionReceiver.pingBackIntent(context, id))
        }
        if (event.type == EventType.Question) {
            builder.addAction(0, "✍️ جواب بده", FullscreenActivity.pendingIntent(context, event.id))
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context, title: String, text: String, tag: Int) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_pingly)
            .setColor(brandColor)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, tag))
            .build()
        NotificationManagerCompat.from(context).notify(tag, notification)
    }

    /** Pingly could not restart itself — say so instead of failing silently. */
    fun cannotStart(context: Context) = health(
        context, ID_HEALTH,
        context.getString(R.string.health_cannot_start_title),
        context.getString(R.string.health_cannot_start_text),
    )

    fun loggedOutElsewhere(context: Context) = health(
        context, ID_SESSION,
        context.getString(R.string.health_logged_out_title),
        context.getString(R.string.health_logged_out_text),
    )

    @SuppressLint("MissingPermission")
    private fun health(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_HEALTH)
            .setSmallIcon(R.drawable.ic_stat_pingly)
            .setColor(brandColor)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, id))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
