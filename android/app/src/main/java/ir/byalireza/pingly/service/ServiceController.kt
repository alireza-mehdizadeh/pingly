package ir.byalireza.pingly.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ir.byalireza.pingly.AppContainer
import ir.byalireza.pingly.PinglyApp
import java.util.concurrent.TimeUnit

/**
 * Decides whether the background service should be running and gets it there.
 *
 * Recovery layers, from fastest to slowest:
 *  1. START_STICKY + restart alarms from onDestroy / onTaskRemoved
 *  2. BOOT_COMPLETED / MY_PACKAGE_REPLACED receivers
 *  3. an alarm watchdog every 15 minutes (allowed while idle)
 *  4. a WorkManager periodic watchdog (survives reboots and app updates on its own)
 *
 * None of these can override a user's Force Stop — Android deliberately keeps a force-stopped app
 * down until it is opened again — and Pingly never tries to.
 */
object ServiceController {
    private const val TAG = "PinglyController"
    private const val WORK_NAME = "pingly-watchdog"
    private const val WATCHDOG_INTERVAL_MS = 15 * 60_000L

    fun shouldRun(container: AppContainer): Boolean =
        container.account.loggedIn.value && !container.prefs.paused.value

    /** Starts the service if it should run. Returns false if Android refused. */
    fun ensureRunning(context: Context, reason: String): Boolean {
        val container = (context.applicationContext as PinglyApp).container
        if (!shouldRun(container)) {
            stop(context)
            return false
        }
        scheduleWatchdogs(context)
        return try {
            ContextCompat.startForegroundService(context, Intent(context, PinglyService::class.java))
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException and friends: tell the user, don't go quiet.
            Log.w(TAG, "could not start service ($reason)", e)
            Notifications.cannotStart(context)
            false
        }
    }

    /** Stops the service. Only called for the explicit Pause button and for signing out. */
    fun stop(context: Context) {
        context.stopService(Intent(context, PinglyService::class.java))
        cancelAlarm(context)
    }

    fun scheduleWatchdogs(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build(),
        )
        scheduleAlarm(context, WATCHDOG_INTERVAL_MS)
    }

    fun scheduleRestart(context: Context, delayMs: Long) = scheduleAlarm(context, delayMs)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, WatchdogReceiver::class.java).setAction(WatchdogReceiver.ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleAlarm(context: Context, delayMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + delayMs
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            // Exact alarms (when the user allows them) also let Android start the service from the alarm.
            if (exactAllowed) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmIntent(context))
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmIntent(context))
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmIntent(context))
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }

    fun cancelWatchdogs(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        cancelAlarm(context)
    }
}
