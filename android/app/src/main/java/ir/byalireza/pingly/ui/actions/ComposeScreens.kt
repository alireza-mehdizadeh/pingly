package ir.byalireza.pingly.ui.actions

import android.app.Application
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.MessageStyle
import ir.byalireza.pingly.domain.Presets
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.FloatingHearts
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PillButton
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.SectionTitle
import ir.byalireza.pingly.ui.components.TopBar
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

class ComposeViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state

    fun sendMessage(text: String, style: MessageStyle, onSent: () -> Unit) =
        action(success = "پیامت تمام‌صفحه روی گوشیش باز می‌شه 💌") {
            container.events.send(
                EventType.Message,
                meta = buildJsonObject { put("style", style.id) },
                payload = buildJsonObject { put("text", text.trim()) },
            )
            onSent()
        }

    fun sendQuestion(question: String, options: List<String>, onSent: () -> Unit) =
        action(success = "سؤالت رفت؛ منتظر جوابش باش ❓") {
            val clean = options.map { it.trim() }.filter { it.isNotEmpty() }
            container.events.send(
                EventType.Question,
                meta = buildJsonObject { put("kind", if (clean.isEmpty()) "text" else "choice") },
                payload = buildJsonObject {
                    put("q", question.trim())
                    putJsonArray("options") { clean.forEach { add(it) } }
                },
            )
            onSent()
        }
}

@Composable
fun MessageScreen(onBack: () -> Unit, vm: ComposeViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    var text by rememberSaveable { mutableStateOf("") }
    var styleId by rememberSaveable { mutableStateOf(MessageStyle.Blush.id) }
    val style = MessageStyle.fromId(styleId)
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }

    PinglyBackground {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            TopBar("پیام عاشقانه 💌", onBack = onBack, subtitle = "تمام‌صفحه روی گوشی ${couple.partnerName} باز می‌شه")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                // Live preview of what the partner will see.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .shadow(14.dp, RoundedCornerShape(30.dp))
                        .clip(RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(style.colors))
                        .animateContentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    FloatingHearts(Modifier.matchParentSize(), count = 10, colors = listOf(Color.White), maxAlpha = 0.35f)
                    Text(
                        text.ifBlank { "پیامت اینجا دیده می‌شه…" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = style.text.copy(alpha = if (text.isBlank()) 0.7f else 1f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(28.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MessageStyle.entries.forEach { s ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.bouncyClick { styleId = s.id }) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(s.colors))
                                    .then(if (s == style) Modifier.border(3.dp, PinglyColors.Ink, CircleShape) else Modifier),
                            )
                            Text(s.title, style = MaterialTheme.typography.labelSmall, color = PinglyColors.InkSoft)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PinglyTextField(text, { text = it }, label = "متن پیامت", singleLine = false, minLines = 3, maxLength = 300)
                SectionTitle("پیشنهادهای آماده ✨")
                Presets.messages.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).bouncyClick { text = preset },
                    ) {
                        Text(preset, style = MaterialTheme.typography.bodyMedium, color = PinglyColors.Ink, modifier = Modifier.padding(14.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            PrimaryButton(
                "بفرست تا قلبش آب شه 💗",
                onClick = { vm.sendMessage(text, style) { text = "" } },
                loading = busy,
                enabled = text.isNotBlank(),
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
fun QuestionScreen(onBack: () -> Unit, vm: ComposeViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    var question by rememberSaveable { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var freeAnswer by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }

    fun applyPreset(preset: Presets.QuestionPreset) {
        question = preset.question
        options.clear()
        if (preset.options.isEmpty()) {
            freeAnswer = true
            options.addAll(listOf("", ""))
        } else {
            freeAnswer = false
            options.addAll(preset.options)
        }
    }

    PinglyBackground {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            TopBar("یه سؤال بپرس ❓", onBack = onBack, subtitle = "${couple.partnerName} همون لحظه می‌تونه جواب بده")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                PinglyCard {
                    PinglyTextField(question, { question = it }, label = "سؤالت چیه؟", singleLine = false, minLines = 2, maxLength = 200)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("🔘 چندگزینه‌ای", onClick = { freeAnswer = false }, filled = !freeAnswer)
                        PillButton("✍️ جواب آزاد", onClick = { freeAnswer = true }, filled = freeAnswer)
                    }
                    if (!freeAnswer) {
                        Spacer(Modifier.height(12.dp))
                        options.forEachIndexed { index, value ->
                            PinglyTextField(value, { options[index] = it }, label = "گزینه‌ی ${index + 1}", maxLength = 40)
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (options.size < 4) PillButton("+ گزینه", onClick = { options.add("") }, filled = false)
                            if (options.size > 2) PillButton("− حذف آخری", onClick = { options.removeAt(options.lastIndex) }, filled = false, color = PinglyColors.InkSoft)
                        }
                    }
                }
                SectionTitle("سؤال‌های آماده 🎲")
                Presets.questions.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).bouncyClick { applyPreset(preset) },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.question, style = MaterialTheme.typography.bodyMedium, color = PinglyColors.Ink, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(if (preset.options.isEmpty()) "✍️" else "🔘", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            val validOptions = options.count { it.isNotBlank() } >= 2
            PrimaryButton(
                "بپرس 💭",
                onClick = { vm.sendQuestion(question, if (freeAnswer) emptyList() else options.toList()) { question = "" } },
                loading = busy,
                enabled = question.isNotBlank() && (freeAnswer || validOptions),
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}
