package ir.byalireza.pingly.ui

import android.app.Application
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
import ir.byalireza.pingly.ui.components.BottomBarHeight
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.MainTab
import ir.byalireza.pingly.ui.components.PinglyBottomBar
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.ToastHost
import ir.byalireza.pingly.ui.components.ToastKind
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
        Box(Modifier.fillMaxSize().background(PinglyColors.Background)) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.97f)) togetherWith fadeOut() },
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
                    Stage.Main -> MainNavigation(isPaired = couple.isPaired)
                }
            }
            ToastHost(toaster, Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), bottomPadding = BottomBarHeight + 28.dp)
        }
    }
}

@Composable
private fun LoadingScreen(error: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(PinglyColors.Surface).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedMascot(size = 140.dp)
        Spacer(Modifier.height(28.dp))
        if (error == null) {
            CircularProgressIndicator(color = PinglyColors.Ink, strokeWidth = 3.dp)
        } else {
            Text(error, style = MaterialTheme.typography.bodyLarge, color = PinglyColors.InkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("دوباره امتحان کن", onClick = onRetry, icon = Icons.Rounded.Refresh)
        }
    }
}

/** The heart in the middle of the tab bar: one tap, one heartbeat on the partner's phone. */
class PingViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    private val _sent = mutableIntStateOf(0)
    val sent: Int get() = _sent.intValue
    private var last = 0L

    fun ping() {
        val now = System.currentTimeMillis()
        if (now - last < 800) return
        last = now
        container.haptics.tap()
        viewModelScope.launch {
            try {
                container.events.send(EventType.Ping)
                _sent.intValue++
                toast("پینگ رفت؛ قلبش برات تپید", ToastKind.Success)
            } catch (e: Exception) {
                toast(e.toPersianMessage(), ToastKind.Error)
            }
        }
    }
}

private object Routes {
    const val VIBRATE = "vibrate"
    const val SOUND = "sound"
    const val VOICE = "voice"
    const val MESSAGE = "message"
    const val QUESTION = "question"
    const val PROFILE = "profile"
    const val CONNECTION = "connection"
    const val RELIABILITY = "reliability"
}

@Composable
private fun MainNavigation(isPaired: Boolean, pingVm: PingViewModel = viewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val couple by pingVm.couple.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val tab = MainTab.fromRoute(entry?.destination?.route)
    val showBar = isPaired && tab != null
    val inset = if (showBar) BottomBarHeight + 8.dp else 0.dp
    val back: () -> Unit = { nav.popBackStack() }

    LaunchedEffect(pingVm) { pingVm.toasts.collect(toaster::show) }
    // If the connection ends while deep inside a screen, go back to the start.
    LaunchedEffect(isPaired) { if (!isPaired) nav.popBackStack(MainTab.Home.route, inclusive = false) }

    fun openTab(target: MainTab) {
        nav.navigate(target.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = MainTab.Home.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(MainTab.Home.route) {
                if (isPaired) {
                    HomeScreen(
                        bottomInset = inset,
                        onAction = { type ->
                            nav.navigate(
                                when (type) {
                                    EventType.Vibrate -> Routes.VIBRATE
                                    EventType.Sound -> Routes.SOUND
                                    EventType.Voice -> Routes.VOICE
                                    EventType.Image -> MainTab.Gallery.route
                                    EventType.Message -> Routes.MESSAGE
                                    else -> Routes.QUESTION
                                },
                            )
                        },
                        onHistory = { openTab(MainTab.History) },
                        onReliability = { nav.navigate(Routes.RELIABILITY) },
                        onOpenEvent = { openEvent(context, it) },
                    )
                } else {
                    PairingScreen(onSettings = { nav.navigate(MainTab.Settings.route) })
                }
            }
            composable(MainTab.History.route) { HistoryScreen(bottomInset = inset, onOpen = { openEvent(context, it) }) }
            composable(MainTab.Gallery.route) { GalleryScreen(bottomInset = inset, onBack = if (showBar) null else back) }
            composable(MainTab.Settings.route) {
                SettingsScreen(
                    bottomInset = inset,
                    onBack = if (isPaired) null else back,
                    onProfile = { nav.navigate(Routes.PROFILE) },
                    onConnection = { nav.navigate(Routes.CONNECTION) },
                    onReliability = { nav.navigate(Routes.RELIABILITY) },
                )
            }
            composable(Routes.VIBRATE) { VibrateScreen(onBack = back) }
            composable(Routes.SOUND) { SoundScreen(onBack = back) }
            composable(Routes.VOICE) { VoiceScreen(onBack = back) }
            composable(Routes.MESSAGE) { MessageScreen(onBack = back) }
            composable(Routes.QUESTION) { QuestionScreen(onBack = back) }
            composable(Routes.PROFILE) { ProfileScreen(firstTime = false, onDone = back, onBack = back) }
            composable(Routes.CONNECTION) { ConnectionScreen(onBack = back) }
            composable(Routes.RELIABILITY) { ReliabilityScreen(firstRun = false, onDone = back, onBack = back) }
        }
        if (showBar) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                PinglyBottomBar(
                    current = tab,
                    onSelect = ::openTab,
                    onPing = {
                        if (couple.partner?.paused == true) toaster.info("${couple.partnerName} فعلاً پینگلی رو مکث کرده")
                        else pingVm.ping()
                    },
                    pingEnabled = couple.partner?.paused != true,
                    pingKey = pingVm.sent,
                )
            }
        }
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
