package ir.byalireza.pingly

import android.app.Application
import ir.byalireza.pingly.service.Notifications
import ir.byalireza.pingly.service.ServiceController
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class PinglyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)

        // Any time the process starts (launcher, boot, alarm, push of an update…) make sure the
        // background service is up if it should be.
        if (ServiceController.shouldRun(container)) ServiceController.ensureRunning(this, "process-start")

        // Follow sign-in and the Pause button: they are the only things that start/stop Pingly.
        container.scope.launch {
            combine(container.account.loggedIn, container.prefs.paused) { loggedIn, paused -> loggedIn && !paused }
                .distinctUntilChanged()
                .drop(1)
                .collect { shouldRun ->
                    if (shouldRun) ServiceController.ensureRunning(this@PinglyApp, "state-change")
                    else ServiceController.stop(this@PinglyApp)
                }
        }
    }
}
