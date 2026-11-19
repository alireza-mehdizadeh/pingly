package ir.byalireza.pingly.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import ir.byalireza.pingly.PinglyApp
import ir.byalireza.pingly.R
import ir.byalireza.pingly.data.realtime.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The always-on heart of Pingly: a foreground service that keeps the encrypted realtime
 * connection open so partner actions arrive even when the app is closed or the screen is locked.
 *
 * It is started on login, after boot, after app updates and by two independent watchdogs, and it
 * asks to be restarted whenever the system tears it down. Only the in-app Pause button (or
 * signing out) stops it on purpose.
 */
class PinglyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as PinglyApp).container
    private var stoppingOnPurpose = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        goForeground(getString(R.string.service_text_connecting))
        observeStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(currentText())
        if (!ServiceController.shouldRun(container)) {
            stopOnPurpose()
            return START_NOT_STICKY
        }
        Notifications.cancel(this, Notifications.ID_HEALTH)
        container.realtime.start()
        container.realtime.nudge()
        return START_STICKY
    }

    private fun goForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, Notifications.ID_SERVICE, Notifications.service(this, text), type)
        } catch (e: Exception) {
            // Android refused foreground status (e.g. a background start it didn't allow).
            Log.e(TAG, "startForeground refused", e)
            Notifications.cannotStart(this)
            stopSelf()
        }
    }

    /** Keeps the persistent notification honest about what Pingly is doing right now. */
    private fun observeStatus() {
        scope.launch {
            combine(container.realtime.state, container.account.state) { connection, couple -> connection to couple }
                .distinctUntilChanged()
                .collect { refreshNotification() }
        }
    }

    private fun currentText(): String {
        val couple = container.account.state.value
        return when (container.realtime.state.value) {
            ConnectionState.Connected ->
                if (couple.isPaired) getString(R.string.service_text_partner, couple.partnerName)
                else getString(R.string.service_text_waiting)
            ConnectionState.Reconnecting ->
                if (container.isOnline()) getString(R.string.service_text_connecting) else getString(R.string.service_text_offline)
            else -> getString(R.string.service_text_connecting)
        }
    }

    private fun refreshNotification() {
        if (!Notifications.canPost(this)) return
        runCatching {
            NotificationManagerCompat.from(this).notify(Notifications.ID_SERVICE, Notifications.service(this, currentText()))
        }
    }

    private fun stopOnPurpose() {
        stoppingOnPurpose = true
        container.realtime.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Swiping Pingly away from recents must not switch it off. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (ServiceController.shouldRun(container)) ServiceController.scheduleRestart(this, delayMs = 1_000)
    }

    /** Android 15+ may time-limit some service types; ask to come straight back if it happens. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "foreground service timeout for type $fgsType")
        ServiceController.scheduleRestart(this, delayMs = 5_000)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        if (!stoppingOnPurpose && ServiceController.shouldRun(container)) {
            // Killed by the system rather than paused by the user: come back as soon as allowed.
            ServiceController.scheduleRestart(this, delayMs = 3_000)
        } else {
            container.realtime.stop()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PinglyService"

        @Volatile
        var running: Boolean = false
            private set
    }
}
