package ir.byalireza.pingly.ui.fullscreen

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ir.byalireza.pingly.AppContainer
import ir.byalireza.pingly.PinglyApp
import ir.byalireza.pingly.R
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.api.toPersianMessage
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.MessageStyle
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.service.Notifications
import ir.byalireza.pingly.ui.components.AvatarBubble
import ir.byalireza.pingly.ui.components.FloatingHearts
import ir.byalireza.pingly.ui.components.PillButton
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.theme.PinglyColors
import ir.byalireza.pingly.ui.theme.PinglyTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Full-screen view for a photo, love note or question from the partner. It can show over the
 * lock screen and wake the display; the phone stays locked underneath.
 */
class FullscreenActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as PinglyApp).container
    private var eventId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        setContent {
            PinglyTheme {
                eventId?.let { id -> FullscreenContent(container, id, onClose = ::finish) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        eventId = intent.getStringExtra(EXTRA_EVENT_ID)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val EXTRA_EVENT_ID = "event_id"

        fun intent(context: Context, eventId: String): Intent =
            Intent(context, FullscreenActivity::class.java).putExtra(EXTRA_EVENT_ID, eventId)

        fun pendingIntent(context: Context, eventId: String): PendingIntent = PendingIntent.getActivity(
            context, Notifications.notificationIdFor(eventId),
            intent(context, eventId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

@Composable
private fun FullscreenContent(container: AppContainer, eventId: String, onClose: () -> Unit) {
    val history by container.events.history.collectAsState()
    val couple by container.account.state.collectAsState()
    var loadFailed by remember { mutableStateOf(false) }
    val event = history.firstOrNull { it.id == eventId }

    val context = LocalContext.current
    LaunchedEffect(eventId) { Notifications.cancel(context, Notifications.notificationIdFor(eventId)) }
    LaunchedEffect(eventId, event == null) {
        if (event == null) {
            // Process was restarted since the event arrived: fetch history to find it.
            runCatching { container.events.refresh() }
            if (container.events.find(eventId) == null) loadFailed = true
        }
    }
    LaunchedEffect(event?.id) {
        if (event != null && !event.mine && event.type != EventType.Question) container.markOpened(event.id)
    }

    Box(Modifier.fillMaxSize()) {
        when {
            event != null -> when (event.type) {
                EventType.Image -> ImageView(container, event, couple.partnerName, couple.partner?.avatar, onClose)
                EventType.Question -> QuestionView(container, event, couple.partnerName, couple.partner?.avatar, onClose)
                else -> MessageView(event, couple.partnerName, couple.partner?.avatar, onClose)
            }
            loadFailed -> Centered {
                Text("این مورد دیگه در دسترس نیست 😔", style = MaterialTheme.typography.titleMedium, color = PinglyColors.Ink)
                Spacer(Modifier.height(16.dp))
                PillButton("بستن", onClose)
            }
            else -> Centered { CircularProgressIndicator(color = PinglyColors.Pink) }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(PinglyColors.BackgroundGradient),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun FromHeader(event: PinglyEvent, partnerName: String, avatar: String?, light: Boolean) {
    val color = if (light) Color.White else PinglyColors.Ink
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AvatarBubble(if (event.mine) null else avatar, size = 64.dp, ring = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            if (event.mine) "فرستاده‌ی تو 💗" else "از طرف $partnerName 💗",
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        Text(Persian.relative(event.createdAt), style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.75f))
    }
}

@Composable
private fun MessageView(event: PinglyEvent, partnerName: String, avatar: String?, onClose: () -> Unit) {
    val style = MessageStyle.fromId(event.metaString("style"))
    val appear by animateFloatAsState(1f, spring(dampingRatio = 0.55f), label = "appear")
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(style.colors))) {
        FloatingHearts(count = 22, colors = listOf(Color.White, Color.White.copy(alpha = 0.7f), PinglyColors.PinkMist), maxAlpha = 0.45f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FromHeader(event, partnerName, avatar, light = style.text == Color.White)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    event.locked -> "🔒 این پیام برای گوشی قبلی رمز شده بود و اینجا باز نمی‌شه."
                    else -> event.text.orEmpty()
                },
                style = MaterialTheme.typography.displaySmall,
                color = style.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(appear),
            )
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                if (event.mine) "بستن" else "منم دوستت دارم 🥰",
                onClick = onClose,
                brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.2f))),
            )
        }
    }
}

@Composable
private fun ImageView(container: AppContainer, event: PinglyEvent, partnerName: String, avatar: String?, onClose: () -> Unit) {
    val file by produceState<Result<java.io.File>?>(null, event.mediaId) {
        value = event.mediaId?.let { id -> runCatching { container.media.file(id) } } ?: Result.failure(IllegalStateException())
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1830), PinglyColors.Ink, Color(0xFF4A2E5C))))) {
        FloatingHearts(count = 16, maxAlpha = 0.35f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FromHeader(event, partnerName, avatar, light = true)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val result = file) {
                    null -> CircularProgressIndicator(color = PinglyColors.PinkSoft)
                    else -> result.fold(
                        onSuccess = {
                            AsyncImage(
                                model = it,
                                contentDescription = "عکس از طرف $partnerName",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(24.dp, RoundedCornerShape(28.dp))
                                    .clip(RoundedCornerShape(28.dp)),
                            )
                        },
                        onFailure = {
                            Text(it.toPersianMessage(), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(if (event.mine) "بستن" else "چه قشنگ! 😍", onClick = onClose)
        }
    }
}

@Composable
private fun QuestionView(container: AppContainer, event: PinglyEvent, partnerName: String, avatar: String?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var freeText by remember { mutableStateOf("") }
    val history by container.events.history.collectAsState()
    val answer = history.firstOrNull { it.type == EventType.Answer && it.replyTo == event.id }
    val answered = answer != null || event.status.wire == "answered"

    fun send(text: String) {
        if (text.isBlank() || sending) return
        sending = true
        error = null
        scope.launch {
            try {
                container.events.send(EventType.Answer, payload = buildJsonObject { put("a", text.trim()) }, replyTo = event.id)
            } catch (e: Exception) {
                error = e.toPersianMessage()
            } finally {
                sending = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFFF3D6), PinglyColors.PinkMist, PinglyColors.LilacMist)))) {
        FloatingHearts(count = 12, maxAlpha = 0.3f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FromHeader(event, partnerName, avatar, light = false)
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(32.dp), color = Color.White, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.ic_pingly_mark), null, Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (event.locked) "🔒 این سؤال برای گوشی قبلی رمز شده بود." else event.question.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = PinglyColors.Ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = answered,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                label = "answer",
            ) { done ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        done -> {
                            Text("💌", style = MaterialTheme.typography.displayMedium)
                            Text(
                                if (event.mine) "$partnerName جواب داد:" else "جوابت رسید!",
                                style = MaterialTheme.typography.titleMedium,
                                color = PinglyColors.InkSoft,
                            )
                            answer?.answer?.let {
                                Spacer(Modifier.height(8.dp))
                                Text("«$it»", style = MaterialTheme.typography.headlineSmall, color = PinglyColors.Pink, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(24.dp))
                            PrimaryButton("بستن", onClick = onClose)
                        }
                        event.mine -> {
                            Text("منتظر جوابِ $partnerName هستیم… ⏳", style = MaterialTheme.typography.titleMedium, color = PinglyColors.InkSoft)
                            Spacer(Modifier.height(24.dp))
                            PrimaryButton("بستن", onClick = onClose)
                        }
                        event.locked -> PrimaryButton("بستن", onClick = onClose)
                        event.options.isNotEmpty() -> {
                            event.options.forEach { option ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .bouncyClick(enabled = !sending) { send(option) },
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.White,
                                    shadowElevation = 3.dp,
                                ) {
                                    Text(
                                        option,
                                        modifier = Modifier.padding(vertical = 18.dp, horizontal = 20.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PinglyColors.Ink,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        else -> {
                            PinglyTextField(freeText, { freeText = it }, label = "جوابت رو بنویس…", singleLine = false, minLines = 2, maxLength = 300)
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton("بفرست 💌", onClick = { send(freeText) }, loading = sending, enabled = freeText.isNotBlank())
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = PinglyColors.Danger, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                    if (!done && !event.mine) {
                        Spacer(Modifier.height(12.dp))
                        PillButton("بعداً جواب می‌دم", onClick = onClose, filled = false, color = PinglyColors.InkSoft)
                    }
                }
            }
        }
    }
}
