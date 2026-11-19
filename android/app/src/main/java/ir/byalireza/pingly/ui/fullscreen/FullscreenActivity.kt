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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ir.byalireza.pingly.AppContainer
import ir.byalireza.pingly.PinglyApp
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.api.toPersianMessage
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.MessageStyle
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.service.Notifications
import ir.byalireza.pingly.ui.components.Avatar
import ir.byalireza.pingly.ui.components.CircleIconButton
import ir.byalireza.pingly.ui.components.EmptyState
import ir.byalireza.pingly.ui.components.FloatingHearts
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.LinkButton
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.components.visual
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

    val sender = Sender(
        name = if (event?.mine == true) couple.me?.displayName else couple.partnerName,
        avatar = if (event?.mine == true) couple.me?.avatar else couple.partner?.avatar,
        label = if (event?.mine == true) "فرستاده‌ی تو" else "از طرف ${couple.partnerName}",
    )
    Box(Modifier.fillMaxSize()) {
        when {
            event != null -> when (event.type) {
                EventType.Image -> ImageView(container, event, sender, couple.partnerName, onClose)
                EventType.Question -> QuestionView(container, event, sender, couple.partnerName, onClose)
                else -> MessageView(event, sender, onClose)
            }
            loadFailed -> Column(
                Modifier.fillMaxSize().background(PinglyColors.Surface).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(Icons.Rounded.HourglassTop, "این مورد دیگه در دسترس نیست", "شاید اتصال قطع شده یا تاریخچه پاک شده باشه.")
                PrimaryButton("بستن", onClick = onClose, icon = Icons.Rounded.Close)
            }
            else -> Box(Modifier.fillMaxSize().background(PinglyColors.Surface), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinglyColors.Ink)
            }
        }
    }
}

private data class Sender(val name: String?, val avatar: String?, val label: String)

/** Close button + who sent it, at the top of every full-screen view. */
@Composable
private fun TopRow(event: PinglyEvent, sender: Sender, light: Boolean, onClose: () -> Unit) {
    val content = if (light) Color.White else PinglyColors.Ink
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (light) Color.White.copy(alpha = 0.18f) else PinglyColors.SurfaceMuted)
                .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(sender.name, sender.avatar, size = 36.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(sender.label, style = MaterialTheme.typography.labelMedium, color = content)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = content.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Persian.relative(event.createdAt), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        CircleIconButton(
            Icons.Rounded.Close, "بستن", onClose,
            background = if (light) Color.White.copy(alpha = 0.18f) else PinglyColors.SurfaceMuted,
            tint = content, bordered = false,
        )
    }
}

@Composable
private fun MessageView(event: PinglyEvent, sender: Sender, onClose: () -> Unit) {
    val style = MessageStyle.fromId(event.metaString("style"))
    val light = style.text == Color.White
    val appear by animateFloatAsState(1f, spring(dampingRatio = 0.55f), label = "appear")
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(style.colors))) {
        FloatingHearts(count = 18, colors = listOf(Color.White), maxAlpha = 0.3f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopRow(event, sender, light, onClose)
            Spacer(Modifier.weight(1f))
            Icon(
                if (event.locked) Icons.Rounded.Lock else Icons.Rounded.Favorite,
                contentDescription = null,
                tint = style.text.copy(alpha = 0.9f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                if (event.locked) "این پیام برای گوشی قبلی رمز شده بود و اینجا باز نمی‌شه." else event.text.orEmpty(),
                style = MaterialTheme.typography.displaySmall,
                color = style.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(appear).padding(horizontal = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                if (event.mine) "بستن" else "منم دوستت دارم",
                onClick = onClose,
                icon = if (event.mine) Icons.Rounded.Close else Icons.Rounded.Favorite,
                color = if (light) Color.White else PinglyColors.Ink,
                contentColor = if (light) style.colors.last() else Color.White,
            )
        }
    }
}

@Composable
private fun ImageView(container: AppContainer, event: PinglyEvent, sender: Sender, partnerName: String, onClose: () -> Unit) {
    val file by produceState<Result<java.io.File>?>(null, event.mediaId) {
        value = event.mediaId?.let { id -> runCatching { container.media.file(id) } } ?: Result.failure(IllegalStateException())
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0F1030))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopRow(event, sender, light = true, onClose)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val result = file) {
                    null -> CircularProgressIndicator(color = Color.White)
                    else -> result.fold(
                        onSuccess = {
                            AsyncImage(
                                model = it,
                                contentDescription = "عکس از طرف $partnerName",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                            )
                        },
                        onFailure = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(it.toPersianMessage(), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                if (event.mine) "بستن" else "چه قشنگه",
                onClick = onClose,
                icon = if (event.mine) Icons.Rounded.Close else Icons.Rounded.Favorite,
                color = Color.White,
                contentColor = PinglyColors.Ink,
            )
        }
    }
}

@Composable
private fun QuestionView(container: AppContainer, event: PinglyEvent, sender: Sender, partnerName: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var freeText by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<String?>(null) }
    val history by container.events.history.collectAsState()
    val answer = history.firstOrNull { it.type == EventType.Answer && it.replyTo == event.id }
    val answered = answer != null || event.status.wire == "answered"
    val visual = EventType.Question.visual

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

    Box(Modifier.fillMaxSize().background(PinglyColors.Background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopRow(event, sender, light = false, onClose)
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(PinglyColors.Surface)
                    .border(1.dp, PinglyColors.Border, RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconBadge(visual.icon, visual.tint, visual.background, size = 56.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (event.locked) "این سؤال برای گوشی قبلی رمز شده بود." else event.question.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = PinglyColors.Ink,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = answered,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
                label = "answer",
            ) { done ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        done -> {
                            IconBadge(Icons.Rounded.CheckCircle, PinglyColors.Mint, PinglyColors.MintTint, size = 64.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (event.mine) "$partnerName جواب داد" else "جوابت رسید",
                                style = MaterialTheme.typography.titleMedium,
                                color = PinglyColors.InkSoft,
                            )
                            answer?.answer?.let {
                                Spacer(Modifier.height(6.dp))
                                Text("«$it»", style = MaterialTheme.typography.headlineSmall, color = PinglyColors.Ink, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(24.dp))
                            PrimaryButton("بستن", onClick = onClose, icon = Icons.Rounded.Close)
                        }
                        event.mine -> {
                            IconBadge(Icons.Rounded.HourglassTop, PinglyColors.Butter, PinglyColors.ButterTint, size = 56.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("منتظر جواب $partnerName هستیم", style = MaterialTheme.typography.titleMedium, color = PinglyColors.InkSoft)
                            Spacer(Modifier.height(24.dp))
                            PrimaryButton("بستن", onClick = onClose, icon = Icons.Rounded.Close)
                        }
                        event.locked -> PrimaryButton("بستن", onClick = onClose, icon = Icons.Rounded.Close)
                        event.options.isNotEmpty() -> {
                            event.options.forEach { option ->
                                val selected = choice == option
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) PinglyColors.LilacTint else PinglyColors.Surface)
                                        .border(1.5.dp, if (selected) PinglyColors.Lilac else PinglyColors.Border, RoundedCornerShape(20.dp))
                                        .bouncyClick(enabled = !sending) { choice = option }
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selected) PinglyColors.Lilac else PinglyColors.InkFaint,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(option, style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton("ارسال جواب", onClick = { choice?.let(::send) }, icon = Icons.AutoMirrored.Rounded.Send, loading = sending, enabled = choice != null)
                        }
                        else -> {
                            PinglyTextField(freeText, { freeText = it }, placeholder = "جوابت رو بنویس…", icon = Icons.Rounded.EditNote, singleLine = false, minLines = 3, maxLength = 300)
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton("ارسال جواب", onClick = { send(freeText) }, icon = Icons.AutoMirrored.Rounded.Send, loading = sending, enabled = freeText.isNotBlank())
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = PinglyColors.Danger, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                    if (!done && !event.mine && !event.locked) {
                        LinkButton("بعداً جواب می‌دم", onClick = onClose, color = PinglyColors.InkSoft, icon = Icons.Rounded.QuestionAnswer)
                    }
                }
            }
        }
    }
}
