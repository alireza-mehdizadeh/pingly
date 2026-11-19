package ir.byalireza.pingly.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.byalireza.pingly.AppContainer
import ir.byalireza.pingly.data.api.toPersianMessage
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.ui.actions.MessageScreen
import ir.byalireza.pingly.ui.actions.QuestionScreen
import ir.byalireza.pingly.ui.actions.SoundScreen
import ir.byalireza.pingly.ui.actions.VibrateScreen
import ir.byalireza.pingly.ui.auth.AuthScreen
import ir.byalireza.pingly.ui.components.AnimatedMascot
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.ToastHost
import ir.byalireza.pingly.ui.components.Toaster
import ir.byalireza.pingly.ui.fullscreen.FullscreenActivity
import ir.byalireza.pingly.ui.gallery.GalleryScreen
import ir.byalireza.pingly.ui.history.HistoryScreen
import ir.byalireza.pingly.ui.home.HomeScreen
import ir.byalireza.pingly.ui.onboarding.OnboardingScreen
import ir.byalireza.pingly.ui.pairing.PairingScreen
import ir.byalireza.pingly.ui.profile.ProfileScreen
import ir.byalireza.pingly.ui.settings.ConnectionScreen
import ir.byalireza.pingly.ui.settings.SettingsScreen
import ir.byalireza.pingly.ui.setup.ReliabilityScreen
import ir.byalireza.pingly.ui.theme.PinglyColors
import ir.byalireza.pingly.ui.voice.VoiceScreen
import kotlinx.coroutines.launch

private enum class Stage { Onboarding, Auth, Loading, Profile, Setup, Main }

/** Picks the right stage of the app and hosts the main navigation graph. */
@Composable
fun PinglyRoot(container: AppContainer) {
    val toaster = remember { Toaster() }
    val loggedIn by container.account.loggedIn.collectAsState()
    val couple by container.account.state.collectAsState()
    var onboarded by remember { mutableStateOf(container.prefs.onboardingDone) }
    var setupDone by remember(loggedIn) { mutableStateOf(container.prefs.setupDone) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // A fresh login or app start: load the account once.
    LaunchedEffect(loggedIn) {
        if (loggedIn && !couple.loaded) {
            loadError = null
            runCatching { container.account.refresh() }.onFailure { loadError = it.toPersianMessage() }
        }
    }

    val stage = when {
        !onboarded -> Stage.Onboarding
        !loggedIn -> Stage.Auth
        couple.me == null -> Stage.Loading
        couple.me?.displayName.isNullOrBlank() -> Stage.Profile
        !setupDone -> Stage.Setup
        else -> Stage.Main
    }

    CompositionLocalProvider(LocalToaster provides toaster) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
                label = "stage",
            ) { current ->
                when (current) {
                    Stage.Onboarding -> OnboardingScreen(onDone = {
                        container.prefs.onboardingDone = true
                        onboarded = true
                    })
                    Stage.Auth -> AuthScreen()
                    Stage.Loading -> LoadingScreen(loadError) {
                        loadError = null
                        scope.launch {
                            runCatching { container.account.refresh() }.onFailure { loadError = it.toPersianMessage() }
                        }
                    }
                    Stage.Profile -> ProfileScreen(firstTime = true, onDone = {})
                    Stage.Setup -> ReliabilityScreen(firstRun = true, onDone = {
                        container.prefs.setupDone = true
                        setupDone = true
                    })
                    Stage.Main -> MainNavigation(container, isPaired = couple.isPaired)
                }
            }
            ToastHost(toaster, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun LoadingScreen(error: String?, onRetry: () -> Unit) {
    PinglyBackground {
        Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedMascot(size = 160.dp)
            Spacer(Modifier.height(24.dp))
            if (error == null) {
                CircularProgressIndicator(color = PinglyColors.Pink)
            } else {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = PinglyColors.InkSoft, modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(Modifier.height(16.dp))
                PrimaryButton("دوباره امتحان کن", onClick = onRetry, modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val VIBRATE = "vibrate"
    const val SOUND = "sound"
    const val VOICE = "voice"
    const val GALLERY = "gallery"
    const val MESSAGE = "message"
    const val QUESTION = "question"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val CONNECTION = "connection"
    const val RELIABILITY = "reliability"
}

@Composable
private fun MainNavigation(container: AppContainer, isPaired: Boolean) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val back: () -> Unit = { nav.popBackStack() }

    // If the connection ends while deep inside an action screen, go back to the start.
    LaunchedEffect(isPaired) { if (!isPaired) nav.popBackStack(Routes.HOME, inclusive = false) }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally { -it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { -it / 4 } + fadeOut() },
    ) {
        composable(Routes.HOME) {
            if (isPaired) {
                HomeScreen(
                    onAction = { type ->
                        nav.navigate(
                            when (type) {
                                EventType.Vibrate -> Routes.VIBRATE
                                EventType.Sound -> Routes.SOUND
                                EventType.Voice -> Routes.VOICE
                                EventType.Image -> Routes.GALLERY
                                EventType.Message -> Routes.MESSAGE
                                else -> Routes.QUESTION
                            },
                        )
                    },
                    onHistory = { nav.navigate(Routes.HISTORY) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onReliability = { nav.navigate(Routes.RELIABILITY) },
                    onOpenEvent = { openEvent(context, it) },
                )
            } else {
                PairingScreen(onSettings = { nav.navigate(Routes.SETTINGS) })
            }
        }
        composable(Routes.VIBRATE) { VibrateScreen(onBack = back) }
        composable(Routes.SOUND) { SoundScreen(onBack = back) }
        composable(Routes.VOICE) { VoiceScreen(onBack = back) }
        composable(Routes.GALLERY) { GalleryScreen(onBack = back) }
        composable(Routes.MESSAGE) { MessageScreen(onBack = back) }
        composable(Routes.QUESTION) { QuestionScreen(onBack = back) }
        composable(Routes.HISTORY) { HistoryScreen(onBack = back, onOpen = { openEvent(context, it) }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onProfile = { nav.navigate(Routes.PROFILE) },
                onConnection = { nav.navigate(Routes.CONNECTION) },
                onReliability = { nav.navigate(Routes.RELIABILITY) },
                onHistory = { nav.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.PROFILE) { ProfileScreen(firstTime = false, onDone = back, onBack = back) }
        composable(Routes.CONNECTION) { ConnectionScreen(onBack = back) }
        composable(Routes.RELIABILITY) { ReliabilityScreen(firstRun = false, onDone = back, onBack = back) }
    }
}

/** Photos, notes and questions open in the full-screen view; the rest have nothing more to show. */
private fun openEvent(context: Context, event: PinglyEvent) {
    if (event.type == EventType.Image || event.type == EventType.Message || event.type == EventType.Question) {
        context.startActivity(FullscreenActivity.intent(context, event.id))
    } else if (event.type == EventType.Answer && event.replyTo != null) {
        context.startActivity(FullscreenActivity.intent(context, event.replyTo))
    }
}
