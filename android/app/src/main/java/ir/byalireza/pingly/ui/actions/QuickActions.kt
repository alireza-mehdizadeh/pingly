package ir.byalireza.pingly.ui.actions

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglySound
import ir.byalireza.pingly.domain.VibrationPattern
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.CircleIconButton
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.InfoLine
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyScreen
import ir.byalireza.pingly.ui.components.RowDivider
import ir.byalireza.pingly.ui.components.icon
import ir.byalireza.pingly.ui.components.visual
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class QuickActionViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    val nowPlaying = container.sound.nowPlaying

    fun sendVibration(pattern: VibrationPattern) = action(success = "«${pattern.title}» رفت روی گوشیش") {
        container.events.send(EventType.Vibrate, meta = buildJsonObject { put("pattern", pattern.id) })
    }

    fun sendSound(sound: PinglySound) = action(success = "«${sound.title}» داره روی گوشیش پخش می‌شه") {
        container.events.send(EventType.Sound, meta = buildJsonObject { put("sound", sound.id) })
    }

    fun previewVibration(pattern: VibrationPattern) = container.haptics.play(pattern, fromPartner = false)

    fun previewSound(sound: PinglySound) {
        if (container.sound.nowPlaying.value == "preview:${sound.id}") container.sound.stop()
        else container.sound.playRaw(sound.res, "preview:${sound.id}", fromPartner = false)
    }

    override fun onCleared() {
        container.sound.stop()
        container.haptics.cancel()
    }
}

@Composable
fun VibrateScreen(onBack: () -> Unit, vm: QuickActionViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }
    val visual = EventType.Vibrate.visual

    PinglyScreen(title = "لرزش از راه دور", onBack = onBack, subtitle = "برای ${couple.partnerName}") {
        Spacer(Modifier.height(8.dp))
        PinglyCard(padding = 6.dp) {
            VibrationPattern.entries.forEachIndexed { index, pattern ->
                if (index > 0) RowDivider(startInset = 74.dp)
                ChoiceRow(
                    icon = pattern.icon,
                    tint = visual.tint,
                    background = visual.background,
                    title = pattern.title,
                    subtitle = pattern.description,
                    previewIcon = Icons.Rounded.Vibration,
                    onPreview = { vm.previewVibration(pattern) },
                    onSend = { vm.sendVibration(pattern) },
                    enabled = !busy,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        InfoLine(Icons.Rounded.Info, "با دکمه‌ی پیش‌نمایش، اول روی گوشی خودت امتحانش کن.")
    }
}

@Composable
fun SoundScreen(onBack: () -> Unit, vm: QuickActionViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val busy by vm.busy.collectAsState()
    val playing by vm.nowPlaying.collectAsState()
    val toaster = LocalToaster.current
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }
    val visual = EventType.Sound.visual

    PinglyScreen(title = "پخش صدا", onBack = onBack, subtitle = "روی گوشی ${couple.partnerName}") {
        Spacer(Modifier.height(8.dp))
        PinglyCard(padding = 6.dp) {
            PinglySound.entries.forEachIndexed { index, sound ->
                if (index > 0) RowDivider(startInset = 74.dp)
                val isPlaying = playing == "preview:${sound.id}"
                ChoiceRow(
                    icon = sound.icon,
                    tint = visual.tint,
                    background = visual.background,
                    title = sound.title,
                    subtitle = if (isPlaying) "در حال پخش…" else "صدای اختصاصی پینگلی",
                    previewIcon = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    onPreview = { vm.previewSound(sound) },
                    onSend = { vm.sendSound(sound) },
                    enabled = !busy,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        InfoLine(Icons.Rounded.Info, "اگه پارتنرت گزینه‌ی «پخش در حالت بی‌صدا» رو روشن گذاشته باشه، صدا حتی با گوشی بی‌صدا هم پخش می‌شه.")
    }
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    tint: Color,
    background: Color,
    title: String,
    subtitle: String,
    previewIcon: ImageVector,
    onPreview: () -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, background, size = 50.dp, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
        }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(previewIcon, "پیش‌نمایش", onPreview, size = 40.dp, background = PinglyColors.SurfaceMuted, bordered = false, tint = PinglyColors.InkSoft)
        Spacer(Modifier.width(8.dp))
        CircleIconButton(Icons.AutoMirrored.Rounded.Send, "ارسال", onSend, size = 40.dp, background = PinglyColors.Ink, tint = Color.White, bordered = false, enabled = enabled)
    }
}
