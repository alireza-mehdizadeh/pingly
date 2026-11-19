package ir.byalireza.pingly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import ir.byalireza.pingly.service.ServiceController
import ir.byalireza.pingly.ui.PinglyRoot
import ir.byalireza.pingly.ui.theme.PinglyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as PinglyApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PinglyTheme {
                PinglyRoot(container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Opening the app is always allowed to (re)start the service — e.g. after a Force Stop.
        if (ServiceController.shouldRun(container)) {
            ServiceController.ensureRunning(this, "app-open")
            container.realtime.nudge()
        }
        if (container.account.loggedIn.value) {
            lifecycleScope.launch {
                runCatching { container.account.refresh() }
                if (container.account.state.value.isPaired) runCatching { container.events.refresh() }
            }
        }
    }
}
