package ir.byalireza.pingly.ui.home

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.R
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.api.AllowDto
import ir.byalireza.pingly.data.api.toPersianMessage
import ir.byalireza.pingly.data.realtime.ConnectionState
import ir.byalireza.pingly.domain.CoupleState
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.AvatarBubble
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.EmojiBadge
import ir.byalireza.pingly.ui.components.EmojiIconButton
import ir.byalireza.pingly.ui.components.FloatingHearts
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PillButton
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.SectionTitle
import ir.byalireza.pingly.ui.components.StatusChip
import ir.byalireza.pingly.ui.components.TextLink
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.components.drawHeart
import ir.byalireza.pingly.ui.setup.BackgroundHealth
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class HomeViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    val history = container.events.history
    val paused = container.prefs.paused
    val connection = container.realtime.state
    val incoming = container.events.incoming
    private var lastPing = 0L

    fun refresh() {
        viewModelScope.launch {
            runCatching { container.account.refresh() }
            if (container.account.state.value.isPaired) runCatching { container.events.refresh() }
        }
    }

    /** Pings skip the busy flag so they feel instant, with a tiny cooldown against double taps. */
    fun ping(onSent: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastPing < 800) return
        lastPing = now
        container.haptics.tap()
        viewModelScope.launch {
            try {
                container.events.send(EventType.Ping)
                onSent()
            } catch (e: Exception) {
                toast(e.toPersianMessage())
            }
        }
    }

    fun setPaused(paused: Boolean) = action(success = if (paused) "پینگلی روی مکث رفت ⏸" else "پینگلی دوباره روشنه 💗") {
        container.account.setPaused(paused)
    }
}

data class ActionTile(val type: EventType, val title: String, val emoji: String, val color: Color, val allowed: (AllowDto) -> Boolean)

private val tiles = listOf(
    ActionTile(EventType.Vibrate, "لرزش", "📳", PinglyColors.PinkMist) { it.vibrate },
    ActionTile(EventType.Sound, "صدا", "🔔", PinglyColors.PeachMist) { it.sound },
    ActionTile(EventType.Voice, "صدای من", "🎙️", PinglyColors.LilacMist) { it.voice },
    ActionTile(EventType.Image, "عکس", "🖼️", PinglyColors.MintMist) { it.image },
    ActionTile(EventType.Message, "پیام عاشقانه", "💌", Color(0xFFFFE8F1)) { it.message },
    ActionTile(EventType.Question, "سؤال", "❓", PinglyColors.ButterMist) { it.question },
)

@Composable
fun HomeScreen(
    onAction: (EventType) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onReliability: () -> Unit,
    onOpenEvent: (PinglyEvent) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val couple by vm.couple.collectAsState()
    val history by vm.history.collectAsState()
    val paused by vm.paused.collectAsState()
    val connection by vm.connection.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var burstKey by remember { mutableIntStateOf(0) }
    var confirmPause by remember { mutableStateOf(false) }
    var healthIssues by remember { mutableIntStateOf(0) }

    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }
    LaunchedEffect(vm) {
        vm.incoming.collect { event -> if (!event.mine) toaster.show(event.summary(couple.partnerName)) }
    }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        healthIssues = BackgroundHealth.items(context, serviceShouldRun = !paused, autostartConfirmed = true)
            .count { it.essential && !it.ok }
        onPauseOrDispose { }
    }

    PinglyBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Header(couple, onHistory, onSettings)
            Spacer(Modifier.height(18.dp))
            CoupleCard(couple)
            Spacer(Modifier.height(22.dp))
            PingButton(
                partnerName = couple.partnerName,
                enabled = couple.partner?.paused != true,
                burstKey = burstKey,
                onPing = { vm.ping { burstKey++ } },
            )
            Spacer(Modifier.height(10.dp))
            SectionTitle("یه کار قشنگ بکن ✨")
            ActionGrid(couple, onTile = { tile ->
                val allow = couple.partner?.allow ?: AllowDto()
                when {
                    couple.partner?.paused == true -> toaster.show("${couple.partnerName} فعلاً پینگلی رو مکث کرده ⏸")
                    !tile.allowed(allow) -> toaster.show("${couple.partnerName} «${tile.title}» رو برای خودش خاموش کرده 🔒")
                    else -> onAction(tile.type)
                }
            })
            Spacer(Modifier.height(18.dp))
            MyStatusCard(
                paused = paused,
                connection = connection,
                healthIssues = healthIssues,
                busy = busy,
                onPauseToggle = { if (paused) vm.setPaused(false) else confirmPause = true },
                onReliability = onReliability,
            )
            Spacer(Modifier.height(18.dp))
            RecentActivity(history.take(4), couple.partnerName, onHistory, onOpenEvent)
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmPause) {
        ConfirmDialog(
            title = "پینگلی رو مکث کنیم؟ ⏸",
            text = "تا وقتی خودت دوباره «ادامه» رو نزنی، پینگلی روی این گوشی هیچ لرزش، صدا یا پیامی از ${couple.partnerName} دریافت نمی‌کنه و سرویس پس‌زمینه خاموش می‌مونه. ${couple.partnerName} هم می‌بینه که در حالت مکثی.",
            confirm = "مکث کن",
            onConfirm = {
                confirmPause = false
                // PinglyApp stops the service once the server has confirmed the pause.
                vm.setPaused(true)
            },
            onDismiss = { confirmPause = false },
        )
    }
}

private val weekdays = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه")

@Composable
private fun Header(couple: CoupleState, onHistory: () -> Unit, onSettings: () -> Unit) {
    val now = System.currentTimeMillis()
    val today = Persian.toJalali(now)
    val weekday = weekdays[java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran")).get(java.util.Calendar.DAY_OF_WEEK) - 1]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("سلام ${couple.me?.displayName.orEmpty()} 👋", style = MaterialTheme.typography.headlineSmall, color = PinglyColors.Ink)
            Text("$weekday ${Persian.digits(today.day)} ${today.monthName}", style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
        }
        EmojiIconButton("🕘", onClick = onHistory)
        Spacer(Modifier.width(10.dp))
        EmojiIconButton("⚙️", onClick = onSettings)
    }
}

@Composable
private fun CoupleCard(couple: CoupleState) {
    val partner = couple.partner
    val days = couple.pair?.let { Persian.daysBetween(it.createdAt, System.currentTimeMillis()) } ?: 0
    val beat = rememberInfiniteTransition(label = "beat")
    val heart by beat.animateFloat(1f, 1.25f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h")

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(34.dp), spotColor = PinglyColors.Pink)
            .clip(RoundedCornerShape(34.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF8FAB), PinglyColors.Pink, PinglyColors.Lilac))),
    ) {
        FloatingHearts(Modifier.matchParentSize(), count = 9, colors = listOf(Color.White), maxAlpha = 0.25f)
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                AvatarBubble(couple.me?.avatar, size = 70.dp, ring = Color.White)
                Box(Modifier.width(76.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        drawLine(Color.White.copy(alpha = 0.6f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                    }
                    Text("💗", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.graphicsLayer { scaleX = heart; scaleY = heart })
                }
                AvatarBubble(partner?.avatar, size = 70.dp, ring = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "${couple.me?.displayName.orEmpty()} و ${couple.partnerName}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                if (days <= 0) "امروز به هم وصل شدید 💞" else Persian.digits("$days روزه که به هم وصلید 💞"),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(12.dp))
            val status = when {
                partner == null -> "…"
                partner.paused -> "⏸ ${couple.partnerName} پینگلی رو مکث کرده"
                partner.online -> "🟢 ${couple.partnerName} آنلاینه"
                partner.lastSeenAt != null -> "⚪ آخرین بار ${Persian.relative(partner.lastSeenAt)}"
                else -> "⚪ آفلاین — پیام‌ها بعداً می‌رسن"
            }
            StatusChip(status, Color.White.copy(alpha = 0.25f), textColor = Color.White)
        }
    }
}

/** The signature action: one tap, one heartbeat on their phone. */
@Composable
private fun PingButton(partnerName: String, enabled: Boolean, burstKey: Int, onPing: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "ping")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2000)), label = "ring")
    val breathe by pulse.animateFloat(1f, 1.05f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val burst = remember { Animatable(1f) }
    LaunchedEffect(burstKey) {
        if (burstKey > 0) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(900))
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                if (enabled) {
                    for (i in 0..1) {
                        val p = (ring + i * 0.5f) % 1f
                        drawCircle(PinglyColors.Pink.copy(alpha = (1 - p) * 0.25f), radius = size.minDimension * (0.3f + p * 0.2f))
                    }
                }
                // Hearts flying out after a successful ping.
                if (burst.value < 1f) {
                    for (i in 0 until 10) {
                        val angle = i * 36f * (Math.PI / 180f).toFloat()
                        val d = size.minDimension * (0.25f + burst.value * 0.3f)
                        val s = 22.dp.toPx() * (1f - burst.value * 0.5f)
                        drawHeart(
                            if (i % 2 == 0) PinglyColors.Pink else PinglyColors.Lilac,
                            Offset(center.x + cos(angle) * d - s / 2, center.y + sin(angle) * d - s / 2),
                            Size(s, s), alpha = 1f - burst.value,
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer { scaleX = breathe; scaleY = breathe }
                    .alpha(if (enabled) 1f else 0.5f)
                    .bouncyClick(enabled = enabled, onClick = onPing),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 14.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_pingly_mark), contentDescription = "پینگ", modifier = Modifier.size(104.dp))
                }
            }
        }
        Text(
            if (enabled) "بزن تا قلبش برات بتپه" else "$partnerName فعلاً در حالت مکثه",
            style = MaterialTheme.typography.labelLarge,
            color = PinglyColors.InkSoft,
        )
    }
}

@Composable
private fun ActionGrid(couple: CoupleState, onTile: (ActionTile) -> Unit) {
    val allow = couple.partner?.allow ?: AllowDto()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    val locked = !tile.allowed(allow) || couple.partner?.paused == true
                    Surface(
                        modifier = Modifier.weight(1f).aspectRatio(0.95f).bouncyClick { onTile(tile) },
                        shape = RoundedCornerShape(26.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(10.dp).alpha(if (locked) 0.45f else 1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EmojiBadge(if (locked) "🔒" else tile.emoji, background = tile.color, size = 54)
                            Spacer(Modifier.height(8.dp))
                            Text(tile.title, style = MaterialTheme.typography.labelMedium, color = PinglyColors.Ink, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyStatusCard(
    paused: Boolean,
    connection: ConnectionState,
    healthIssues: Int,
    busy: Boolean,
    onPauseToggle: () -> Unit,
    onReliability: () -> Unit,
) {
    PinglyCard(color = if (paused) PinglyColors.ButterMist else Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(if (paused) "⏸" else "💓", background = if (paused) Color.White else PinglyColors.MintMist)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("پینگلی روی گوشی تو", style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                Text(
                    when {
                        paused -> "در حالت مکث — چیزی دریافت نمی‌شه"
                        connection == ConnectionState.Connected -> "روشن و آماده‌ی دریافت ✨"
                        else -> "روشن؛ در حال اتصال به سرور…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PinglyColors.InkSoft,
                )
            }
            // The one and only control that intentionally switches Pingly off.
            PillButton(if (paused) "▶ ادامه" else "⏸ مکث", onClick = onPauseToggle, filled = paused, enabled = !busy)
        }
        if (healthIssues > 0 && !paused) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PinglyColors.PinkMist)
                    .bouncyClick(onClick = onReliability)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚠️", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    Persian.digits("$healthIssues تنظیم مهم پس‌زمینه کامل نیست؛ ممکنه پینگ‌ها دیر برسن. درستش کن ›"),
                    style = MaterialTheme.typography.bodySmall,
                    color = PinglyColors.Ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecentActivity(events: List<PinglyEvent>, partnerName: String, onHistory: () -> Unit, onOpen: (PinglyEvent) -> Unit) {
    SectionTitle("آخرین اتفاق‌ها") { TextLink("همه ›", onClick = onHistory) }
    if (events.isEmpty()) {
        PinglyCard {
            Text("هنوز اتفاقی نیفتاده… اولین پینگ رو تو بزن! 💗", style = MaterialTheme.typography.bodyMedium, color = PinglyColors.InkSoft)
        }
        return
    }
    PinglyCard(padding = 8) {
        events.forEach { event -> EventRow(event, partnerName, onClick = { onOpen(event) }) }
    }
}

@Composable
fun EventRow(event: PinglyEvent, partnerName: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(event.type.emoji, background = if (event.mine) PinglyColors.LilacMist else PinglyColors.PinkMist, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.summary(partnerName), style = MaterialTheme.typography.bodyMedium, color = PinglyColors.Ink, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Persian.relative(event.createdAt), style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkFaint)
                if (event.mine) {
                    Text("· ${event.status.icon} ${event.status.label}", style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkFaint)
                }
            }
        }
    }
}
