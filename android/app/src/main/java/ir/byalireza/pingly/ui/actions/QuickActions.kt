package ir.byalireza.pingly.ui.actions

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglySound
import ir.byalireza.pingly.domain.VibrationPattern
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.EmojiBadge
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PillButton
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.TopBar
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class QuickActionViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    val nowPlaying = container.sound.nowPlaying

    fun sendVibration(pattern: VibrationPattern) = action(success = "«${pattern.title}» رفت روی گوشیش ${pattern.emoji}") {
        container.events.send(EventType.Vibrate, meta = buildJsonObject { put("pattern", pattern.id) })
    }

    fun sendSound(sound: PinglySound) = action(success = "«${sound.title}» داره روی گوشیش پخش می‌شه ${sound.emoji}") {
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

    ActionList(
        title = "لرزش از راه دور 📳",
        subtitle = "یه الگو انتخاب کن تا گوشی ${couple.partnerName} همین‌طوری بلرزه",
        onBack = onBack,
    ) {
        VibrationPattern.entries.forEach { pattern ->
            ChoiceCard(
                emoji = pattern.emoji,
                title = pattern.title,
                description = pattern.description,
                tint = PinglyColors.PinkMist,
                previewLabel = "📳 امتحان",
                onPreview = { vm.previewVibration(pattern) },
                onSend = { vm.sendVibration(pattern) },
                enabled = !busy,
            )
        }
    }
}

@Composable
fun SoundScreen(onBack: () -> Unit, vm: QuickActionViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val busy by vm.busy.collectAsState()
    val playing by vm.nowPlaying.collectAsState()
    val toaster = LocalToaster.current
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }

    ActionList(
        title = "پخش صدا 🔔",
        subtitle = "صداهای اختصاصی پینگلی؛ روی گوشی ${couple.partnerName} پخش می‌شن",
        onBack = onBack,
    ) {
        PinglySound.entries.forEach { sound ->
            val isPlaying = playing == "preview:${sound.id}"
            ChoiceCard(
                emoji = sound.emoji,
                title = sound.title,
                description = null,
                tint = PinglyColors.PeachMist,
                previewLabel = if (isPlaying) "⏹ توقف" else "▶ گوش بده",
                onPreview = { vm.previewSound(sound) },
                onSend = { vm.sendSound(sound) },
                enabled = !busy,
            )
        }
    }
}

@Composable
fun ActionList(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    PinglyBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title, onBack = onBack, subtitle = subtitle)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    emoji: String,
    title: String,
    description: String?,
    tint: Color,
    previewLabel: String,
    onPreview: () -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Surface(shape = RoundedCornerShape(26.dp), color = Color.White, shadowElevation = 4.dp, modifier = Modifier.bouncyClick(enabled = enabled, onClick = onSend)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(emoji, background = tint, size = 54)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = PinglyColors.Ink)
                if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
                Spacer(Modifier.height(8.dp))
                PillButton(previewLabel, onClick = onPreview, filled = false, color = PinglyColors.Lilac)
            }
            Spacer(Modifier.width(8.dp))
            PillButton("بفرست", onClick = onSend, enabled = enabled)
        }
    }
}
