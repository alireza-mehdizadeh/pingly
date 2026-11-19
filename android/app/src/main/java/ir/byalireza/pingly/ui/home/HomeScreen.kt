package ir.byalireza.pingly.ui.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.api.AllowDto
import ir.byalireza.pingly.data.realtime.ConnectionState
import ir.byalireza.pingly.domain.CoupleState
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.Avatar
import ir.byalireza.pingly.ui.components.AvatarPair
import ir.byalireza.pingly.ui.components.Chevron
import ir.byalireza.pingly.ui.components.CircleIconButton
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.EmptyState
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyScreen
import ir.byalireza.pingly.ui.components.RowDivider
import ir.byalireza.pingly.ui.components.SectionHeader
import ir.byalireza.pingly.ui.components.SmallButton
import ir.byalireza.pingly.ui.components.StatusChip
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.components.colors
import ir.byalireza.pingly.ui.components.visual
import ir.byalireza.pingly.ui.setup.BackgroundHealth
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    val history = container.events.history
    val paused = container.prefs.paused
    val connection = container.realtime.state
    val incoming = container.events.incoming

    fun refresh() {
        viewModelScope.launch {
            runCatching { container.account.refresh() }
            if (container.account.state.value.isPaired) runCatching { container.events.refresh() }
        }
    }

    fun setPaused(paused: Boolean) = action(success = if (paused) "پینگلی روی مکث رفت" else "پینگلی دوباره روشن شد") {
        container.account.setPaused(paused)
    }
}

data class ActionTile(val type: EventType, val title: String, val allowed: (AllowDto) -> Boolean)

private val tiles = listOf(
    ActionTile(EventType.Vibrate, "لرزش") { it.vibrate },
    ActionTile(EventType.Sound, "صدا") { it.sound },
    ActionTile(EventType.Voice, "صدای من") { it.voice },
    ActionTile(EventType.Image, "عکس") { it.image },
    ActionTile(EventType.Message, "پیام عاشقانه") { it.message },
    ActionTile(EventType.Question, "سؤال") { it.question },
)

@Composable
fun HomeScreen(
    bottomInset: Dp,
    onAction: (EventType) -> Unit,
    onHistory: () -> Unit,
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
    var confirmPause by remember { mutableStateOf(false) }
    var healthIssues by remember { mutableIntStateOf(0) }

    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }
    LaunchedEffect(vm) {
        vm.incoming.collect { event -> if (!event.mine) toaster.info(event.summary(couple.partnerName)) }
    }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        healthIssues = BackgroundHealth.items(context, serviceShouldRun = !paused, autostartConfirmed = true).count { it.essential && !it.ok }
        onPauseOrDispose { }
    }

    PinglyScreen(bottomInset = bottomInset) {
        Header(couple, onHistory)
        HeroCard(couple, history)
        SectionHeader("ارسال برای ${couple.partnerName}")
        QuickActions(couple) { tile ->
            val allow = couple.partner?.allow ?: AllowDto()
            when {
                couple.partner?.paused == true -> toaster.info("${couple.partnerName} فعلاً پینگلی رو مکث کرده")
                !tile.allowed(allow) -> toaster.info("${couple.partnerName} «${tile.title}» رو برای خودش خاموش کرده")
                else -> onAction(tile.type)
            }
        }
        Spacer(Modifier.height(16.dp))
        StatusCard(
            paused = paused,
            connection = connection,
            healthIssues = healthIssues,
            busy = busy,
            onPauseToggle = { if (paused) vm.setPaused(false) else confirmPause = true },
            onReliability = onReliability,
        )
        SectionHeader("فعالیت‌های اخیر", action = if (history.isNotEmpty()) "همه" else null, onAction = onHistory)
        if (history.isEmpty()) {
            PinglyCard {
                EmptyState(
                    Icons.Rounded.Favorite, "هنوز اتفاقی نیفتاده",
                    "اولین پینگ رو تو بزن؛ دکمه‌ی قلب پایین صفحه رو لمس کن.",
                    tint = PinglyColors.Pink, background = PinglyColors.PinkTint,
                )
            }
        } else {
            PinglyCard(padding = 6.dp) {
                history.take(5).forEachIndexed { index, event ->
                    if (index > 0) RowDivider()
                    EventRow(event, couple.partnerName, onClick = { onOpenEvent(event) })
                }
            }
        }
    }

    if (confirmPause) {
        ConfirmDialog(
            icon = Icons.Rounded.Pause,
            title = "پینگلی رو مکث کنیم؟",
            text = "تا وقتی خودت دوباره «ادامه» رو نزنی، این گوشی هیچ لرزش، صدا یا پیامی از ${couple.partnerName} دریافت نمی‌کنه و سرویس پس‌زمینه خاموش می‌مونه. ${couple.partnerName} هم می‌بینه که در حالت مکثی.",
            confirm = "مکث کن",
            onConfirm = {
                confirmPause = false
                vm.setPaused(true)
            },
            onDismiss = { confirmPause = false },
        )
    }
}

private val weekdays = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")

@Composable
private fun Header(couple: CoupleState, onHistory: () -> Unit) {
    val today = Persian.toJalali(System.currentTimeMillis())
    val weekday = weekdays[java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran")).get(java.util.Calendar.DAY_OF_WEEK) - 1]
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(couple.me?.displayName, couple.me?.avatar, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("سلام، ${couple.me?.displayName.orEmpty()}", style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$weekday ${Persian.digits(today.day)} ${today.monthName}", style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
        }
        CircleIconButton(Icons.Rounded.NotificationsNone, "تاریخچه", onHistory)
    }
}

@Composable
private fun HeroCard(couple: CoupleState, history: List<PinglyEvent>) {
    val partner = couple.partner
    val now = System.currentTimeMillis()
    val days = couple.pair?.let { Persian.daysBetween(it.createdAt, now) } ?: 0
    val today = history.filter { Persian.daysBetween(it.createdAt, now) == 0L }
    val sentToday = today.count { it.mine }
    val receivedToday = today.size - sentToday

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(PinglyColors.HeroGradient)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarPair(couple.me?.displayName, couple.me?.avatar, couple.partnerName, partner?.avatar, size = 50.dp)
            Spacer(Modifier.weight(1f))
            val (label, dotColor) = when {
                partner == null -> "…" to Color.White
                partner.paused -> "در حالت مکث" to PinglyColors.ButterTint
                partner.online -> "آنلاین" to Color(0xFF5BF0B0)
                partner.lastSeenAt != null -> Persian.relative(partner.lastSeenAt) to Color.White.copy(alpha = 0.6f)
                else -> "آفلاین" to Color.White.copy(alpha = 0.6f)
            }
            StatusChip(label, color = Color.White, background = Color.White.copy(alpha = 0.2f), dot = true, dotColor = dotColor)
        }
        Spacer(Modifier.height(18.dp))
        Text("${couple.me?.displayName.orEmpty()} و ${couple.partnerName}", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (days <= 0) "روز اول" else Persian.digits(days), style = MaterialTheme.typography.displayMedium, color = Color.White)
            if (days > 0) {
                Spacer(Modifier.width(8.dp))
                Text("روز با هم", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat(Icons.AutoMirrored.Rounded.CallMade, "امروز فرستادی", sentToday, Modifier.weight(1f))
            HeroStat(Icons.AutoMirrored.Rounded.CallReceived, "امروز گرفتی", receivedToday, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(icon: ImageVector, label: String, value: Int, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.18f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text(Persian.digits(value), style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    }
}

@Composable
private fun QuickActions(couple: CoupleState, onTile: (ActionTile) -> Unit) {
    val allow = couple.partner?.allow ?: AllowDto()
    PinglyCard(padding = 12.dp) {
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    val locked = !tile.allowed(allow) || couple.partner?.paused == true
                    val visual = tile.type.visual
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .bouncyClick { onTile(tile) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            IconBadge(
                                visual.icon, visual.tint, visual.background,
                                modifier = Modifier.alpha(if (locked) 0.4f else 1f),
                                size = 58.dp, shape = RoundedCornerShape(20.dp),
                            )
                            if (locked) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-6).dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(PinglyColors.Ink),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(tile.title, style = MaterialTheme.typography.labelMedium, color = PinglyColors.Ink, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    paused: Boolean,
    connection: ConnectionState,
    healthIssues: Int,
    busy: Boolean,
    onPauseToggle: () -> Unit,
    onReliability: () -> Unit,
) {
    PinglyCard(padding = 14.dp, color = if (paused) PinglyColors.ButterTint else PinglyColors.Surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                if (paused) Icons.Rounded.Pause else Icons.Rounded.WifiTethering,
                tint = if (paused) PinglyColors.Butter else PinglyColors.Mint,
                background = if (paused) Color.White else PinglyColors.MintTint,
                size = 46.dp,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("پینگلی روی گوشی تو", style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                Text(
                    when {
                        paused -> "در حالت مکث؛ چیزی دریافت نمی‌شه"
                        connection == ConnectionState.Connected -> "روشن و آماده‌ی دریافت"
                        else -> "روشن؛ در حال اتصال…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PinglyColors.InkSoft,
                )
            }
            // The one and only control that intentionally switches Pingly off.
            SmallButton(
                if (paused) "ادامه" else "مکث",
                onClick = onPauseToggle,
                icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                filled = paused,
                enabled = !busy,
            )
        }
        if (healthIssues > 0 && !paused) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PinglyColors.DangerTint)
                    .bouncyClick(onClick = onReliability)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = PinglyColors.Danger, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    Persian.digits("$healthIssues تنظیم مهم پس‌زمینه کامل نیست؛ ممکنه پینگ‌ها دیر برسن"),
                    style = MaterialTheme.typography.bodySmall,
                    color = PinglyColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Chevron()
            }
        }
    }
}

/** One interaction in a list: coloured icon, summary, time and (for yours) the receipt. */
@Composable
fun EventRow(event: PinglyEvent, partnerName: String, onClick: () -> Unit) {
    val visual = event.type.visual
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(visual.icon, visual.tint, visual.background, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.summary(partnerName), style = MaterialTheme.typography.bodyMedium, color = PinglyColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (if (event.mine) "تو" else partnerName) + " · " + Persian.relative(event.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = PinglyColors.InkFaint,
            )
        }
        if (event.mine) {
            Spacer(Modifier.width(8.dp))
            val (fg, bg) = event.status.colors
            StatusChip(event.status.label, color = fg, background = bg)
        }
    }
}
