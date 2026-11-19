package ir.byalireza.pingly.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.byalireza.pingly.PinglyApp
import ir.byalireza.pingly.data.api.toPersianMessage
import ir.byalireza.pingly.domain.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Brings Pingly back after a reboot or an app update (unless the owner paused it). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            -> ServiceController.ensureRunning(context, "boot:${intent.action}")
        }
    }
}

/** Alarm watchdog: checks the service is alive and re-arms itself. */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val container = (context.applicationContext as PinglyApp).container
        if (ServiceController.shouldRun(container)) {
            if (!PinglyService.running) ServiceController.ensureRunning(context, "alarm")
            else ServiceController.scheduleWatchdogs(context)
            container.realtime.nudge()
        }
    }

    companion object {
        const val ACTION = "ir.byalireza.pingly.WATCHDOG"
    }
}

/** WorkManager watchdog: an independent second safety net with its own persistence. */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PinglyApp).container
        if (ServiceController.shouldRun(container) && !PinglyService.running) {
            ServiceController.ensureRunning(applicationContext, "worker")
        }
        return Result.success()
    }
}

/** Quick replies from notifications (e.g. "ping back") without opening the app. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PING_BACK) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val container = (context.applicationContext as PinglyApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.events.send(EventType.Ping)
                Notifications.cancel(context, notificationId)
            } catch (e: Exception) {
                Notifications.update(context, "پینگ فرستاده نشد 😔", e.toPersianMessage(), notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_PING_BACK = "ir.byalireza.pingly.PING_BACK"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pingBackIntent(context: Context, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
            context, notificationId,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_PING_BACK)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
