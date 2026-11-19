package ir.byalireza.pingly.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.byalireza.pingly.PinglyApp
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.service.ServiceController
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.InfoLine
import ir.byalireza.pingly.ui.components.ListRow
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyScreen
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.RowDivider
import ir.byalireza.pingly.ui.components.SectionHeader
import ir.byalireza.pingly.ui.components.SmallButton
import ir.byalireza.pingly.ui.theme.PinglyColors

private val HealthItem.Key.icon: ImageVector
    get() = when (this) {
        HealthItem.Key.Notifications -> Icons.Rounded.NotificationsActive
        HealthItem.Key.Battery -> Icons.Rounded.BatteryChargingFull
        HealthItem.Key.FullScreen -> Icons.Rounded.Fullscreen
        HealthItem.Key.Overlay -> Icons.Rounded.Layers
        HealthItem.Key.ExactAlarms -> Icons.Rounded.Alarm
        HealthItem.Key.Service -> Icons.Rounded.WifiTethering
        HealthItem.Key.Autostart -> Icons.Rounded.RocketLaunch
    }

/**
 * Walks the user through every Android setting that decides whether partner actions arrive in
 * the background. Shown once after sign-up, and any time from Settings or the dashboard banner.
 */
@Composable
fun ReliabilityScreen(firstRun: Boolean, onDone: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val container = (context.applicationContext as PinglyApp).container
    val paused by container.prefs.paused.collectAsState()
    var refreshTick by remember { mutableIntStateOf(0) }
    var autostartConfirmed by remember { mutableStateOf(container.prefs.autostartConfirmed) }

    LifecycleResumeEffect(Unit) {
        refreshTick++
        if (ServiceController.shouldRun(container)) ServiceController.ensureRunning(context, "reliability-screen")
        onPauseOrDispose { }
    }

    val items = remember(refreshTick, paused, autostartConfirmed) {
        BackgroundHealth.items(context, serviceShouldRun = !paused, autostartConfirmed = autostartConfirmed)
    }
    val okCount = items.count { it.ok }
    val essentialsOk = items.filter { it.essential }.all { it.ok }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshTick++
        if (!granted) BackgroundHealth.openFirst(context, listOf(BackgroundHealth.notificationSettingsIntent(context)))
    }

    fun fix(item: HealthItem) {
        when (item.key) {
            HealthItem.Key.Notifications ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else BackgroundHealth.openFirst(context, listOf(BackgroundHealth.notificationSettingsIntent(context)))
            HealthItem.Key.Battery -> BackgroundHealth.openFirst(context, listOf(BackgroundHealth.batteryIntent(context), BackgroundHealth.appDetails(context)))
            HealthItem.Key.FullScreen -> BackgroundHealth.openFirst(context, listOf(BackgroundHealth.fullScreenIntent(context)))
            HealthItem.Key.Overlay -> BackgroundHealth.openFirst(context, listOf(BackgroundHealth.overlayIntent(context)))
            HealthItem.Key.ExactAlarms -> BackgroundHealth.openFirst(context, listOf(BackgroundHealth.exactAlarmIntent(context)))
            HealthItem.Key.Service -> {
                ServiceController.ensureRunning(context, "manual")
                refreshTick++
            }
            HealthItem.Key.Autostart -> {
                BackgroundHealth.openFirst(context, BackgroundHealth.autostartIntents(context))
                container.prefs.autostartConfirmed = true
                autostartConfirmed = true
            }
        }
    }

    PinglyScreen(
        title = if (firstRun) null else "پایداری در پس‌زمینه",
        onBack = onBack,
        bottom = if (firstRun) {
            {
                PrimaryButton(
                    if (essentialsOk) "همه‌چیز آماده‌ست، بریم" else "فعلاً ادامه بده",
                    onClick = onDone,
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                )
            }
        } else null,
    ) {
        if (firstRun) {
            Spacer(Modifier.height(24.dp))
            Text("یه قدم تا آمادگی کامل", style = MaterialTheme.typography.headlineLarge, color = PinglyColors.Ink)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            "پینگلی باید همیشه بیدار بمونه تا وقتی گوشی قفله یا برنامه بسته‌ست هم پارتنرت بهت برسه. این تنظیمات رو یه بار فعال کن.",
            style = MaterialTheme.typography.bodyMedium,
            color = PinglyColors.InkSoft,
        )
        Spacer(Modifier.height(16.dp))
        ProgressCard(okCount, items.size)
        SectionHeader("چک‌لیست")
        PinglyCard(padding = 6.dp) {
            items.forEachIndexed { index, item ->
                if (index > 0) RowDivider()
                ListRow(
                    icon = item.key.icon,
                    title = item.title,
                    subtitle = item.description,
                    iconTint = if (item.ok) PinglyColors.Mint else if (item.essential) PinglyColors.Pink else PinglyColors.Lilac,
                    iconBackground = if (item.ok) PinglyColors.MintTint else if (item.essential) PinglyColors.PinkTint else PinglyColors.LilacTint,
                    onClick = if (item.ok) null else ({ fix(item) }),
                    trailing = {
                        if (item.ok) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "فعال", tint = PinglyColors.Mint, modifier = Modifier.size(26.dp))
                        } else {
                            SmallButton("فعال‌سازی", onClick = { fix(item) })
                        }
                    },
                )
            }
        }
        SectionHeader("خوبه بدونی")
        PinglyCard {
            InfoLine(Icons.Rounded.RestartAlt, "پینگلی بعد از خاموش و روشن کردن گوشی، خودش دوباره روشن می‌شه.")
            InfoLine(Icons.Rounded.SwipeUp, "بستن برنامه از لیست برنامه‌های اخیر، پینگلی رو خاموش نمی‌کنه.")
            InfoLine(Icons.Rounded.PowerSettingsNew, "اگه از تنظیمات اندروید «توقف اجباری» رو بزنی، اندروید اجازه نمی‌ده پینگلی خودش روشن بشه؛ کافیه یه بار بازش کنی.")
            InfoLine(Icons.Rounded.PauseCircle, "تنها راه خاموش کردن عمدی پینگلی، دکمه‌ی «مکث» در صفحه‌ی خانه‌ست.")
            InfoLine(Icons.Rounded.Lightbulb, "اگه پینگلی نتونه خودش روشن بشه، با یه اعلان خبرت می‌کنیم.")
        }
    }
}

/** Ring progress like the task-progress rings in the references. */
@Composable
private fun ProgressCard(done: Int, total: Int) {
    val progress by animateFloatAsState(if (total == 0) 0f else done / total.toFloat(), label = "progress")
    PinglyCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(72.dp)) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2
                    drawArc(PinglyColors.SurfaceMuted, 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                    drawArc(
                        if (done == total) PinglyColors.Mint else PinglyColors.Pink,
                        -90f, 360f * progress, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Text(Persian.digits("${(progress * 100).toInt()}٪"), style = MaterialTheme.typography.labelMedium, color = PinglyColors.Ink)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (done == total) "همه‌چیز آماده‌ست" else Persian.digits("$done از $total مورد آماده‌ست"),
                    style = MaterialTheme.typography.titleMedium,
                    color = PinglyColors.Ink,
                )
                Text(
                    if (done == total) "پینگ‌ها حتی با گوشی قفل هم فوراً می‌رسن." else "موارد قرمز برای رسیدن پینگ‌ها در پس‌زمینه ضروری‌ان.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PinglyColors.InkSoft,
                )
            }
            IconBadge(
                if (done == total) Icons.Rounded.CheckCircle else Icons.Rounded.WifiTethering,
                tint = if (done == total) PinglyColors.Mint else PinglyColors.Pink,
                background = if (done == total) PinglyColors.MintTint else PinglyColors.PinkTint,
                size = 40.dp,
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}
