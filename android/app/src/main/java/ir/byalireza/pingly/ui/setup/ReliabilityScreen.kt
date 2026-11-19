package ir.byalireza.pingly.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.byalireza.pingly.PinglyApp
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.service.ServiceController
import ir.byalireza.pingly.ui.components.Bullet
import ir.byalireza.pingly.ui.components.EmojiBadge
import ir.byalireza.pingly.ui.components.PillButton
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.TopBar
import ir.byalireza.pingly.ui.theme.PinglyColors

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

    PinglyBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            if (firstRun) Spacer(Modifier.statusBarsPadding().height(16.dp))
            else TopBar("پایداری در پس‌زمینه", onBack = onBack)

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                if (firstRun) {
                    Text("یه قدم دیگه تا آمادگی کامل 💪", style = MaterialTheme.typography.headlineSmall, color = PinglyColors.Ink)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    "پینگلی باید همیشه بیدار بمونه تا وقتی گوشی قفله یا برنامه بسته‌ست هم پارتنرت بهت برسه. این تنظیمات رو یه بار فعال کن:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PinglyColors.InkSoft,
                )
                Spacer(Modifier.height(16.dp))
                PinglyCard(padding = 18) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Persian.digits("$okCount از ${items.size} مورد آماده‌ست"),
                            style = MaterialTheme.typography.titleSmall,
                            color = PinglyColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(if (okCount == items.size) "🎉" else "🛠️", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { okCount / items.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        color = PinglyColors.Pink,
                        trackColor = PinglyColors.PinkMist,
                        strokeCap = StrokeCap.Round,
                    )
                }
                Spacer(Modifier.height(16.dp))
                items.forEach { item ->
                    HealthRow(item, onFix = { fix(item) })
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(8.dp))
                PinglyCard(color = PinglyColors.LilacMist, padding = 18) {
                    Text("خوبه بدونی 💡", style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                    Spacer(Modifier.height(6.dp))
                    Bullet("پینگلی بعد از خاموش‌وروشن‌کردن گوشی خودش دوباره روشن می‌شه.")
                    Bullet("بستن برنامه از لیست برنامه‌های اخیر، پینگلی رو خاموش نمی‌کنه.")
                    Bullet("اگه از تنظیمات اندروید «توقف اجباری» رو بزنی، اندروید اجازه نمی‌ده پینگلی خودش روشن بشه؛ کافیه یه بار بازش کنی.")
                    Bullet("تنها راه خاموش‌کردن عمدی پینگلی، دکمه‌ی «مکث» توی صفحه‌ی اصلیه.")
                }
                Spacer(Modifier.height(20.dp))
            }
            if (firstRun) {
                PrimaryButton(
                    if (items.filter { it.essential }.all { it.ok }) "همه‌چی آماده‌ست! بریم 💞" else "فعلاً ادامه بده",
                    onClick = onDone,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun HealthRow(item: HealthItem, onFix: () -> Unit) {
    val background by animateColorAsState(if (item.ok) PinglyColors.MintMist else androidx.compose.ui.graphics.Color.White, label = "bg")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(item.emoji, background = if (item.ok) androidx.compose.ui.graphics.Color.White else PinglyColors.PinkMist)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                if (item.essential && !item.ok) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(PinglyColors.Danger))
                }
            }
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
        }
        Spacer(Modifier.width(8.dp))
        if (item.ok) {
            Text("✅", style = MaterialTheme.typography.titleLarge)
        } else {
            PillButton("فعال کن", onClick = onFix)
        }
    }
}
