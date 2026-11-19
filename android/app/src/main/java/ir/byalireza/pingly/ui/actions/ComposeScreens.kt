package ir.byalireza.pingly.ui.actions

import android.app.Application
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.MessageStyle
import ir.byalireza.pingly.domain.Presets
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.FieldLabel
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.InfoLine
import ir.byalireza.pingly.ui.components.ListRow
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyScreen
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.RowDivider
import ir.byalireza.pingly.ui.components.SectionHeader
import ir.byalireza.pingly.ui.components.SegmentedControl
import ir.byalireza.pingly.ui.components.TonalButton
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.components.visual
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class ComposeViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state

    fun sendMessage(text: String, style: MessageStyle, onSent: () -> Unit) =
        action(success = "پیامت تمام‌صفحه روی گوشیش باز می‌شه") {
            container.events.send(
                EventType.Message,
                meta = buildJsonObject { put("style", style.id) },
                payload = buildJsonObject { put("text", text.trim()) },
            )
            onSent()
        }

    fun sendQuestion(question: String, options: List<String>, onSent: () -> Unit) =
        action(success = "سؤالت رفت؛ منتظر جوابش باش") {
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

    PinglyScreen(
        title = "پیام عاشقانه",
        subtitle = "تمام‌صفحه روی گوشی ${couple.partnerName}",
        onBack = onBack,
        bottom = {
            PrimaryButton(
                "ارسال پیام", onClick = { vm.sendMessage(text, style) { text = "" } },
                icon = Icons.AutoMirrored.Rounded.Send, loading = busy, enabled = text.isNotBlank(),
            )
        },
    ) {
        Spacer(Modifier.height(8.dp))
        // Live preview of what the partner will see.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(style.colors))
                .padding(20.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = style.text.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("پیش‌نمایش", style = MaterialTheme.typography.labelSmall, color = style.text.copy(alpha = 0.8f))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text.ifBlank { "پیامت اینجا دیده می‌شه…" },
                style = MaterialTheme.typography.headlineSmall,
                color = style.text.copy(alpha = if (text.isBlank()) 0.65f else 1f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }
        Spacer(Modifier.height(20.dp))
        FieldLabel("رنگ پس‌زمینه")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MessageStyle.entries.forEach { s ->
                val selected = s == style
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.bouncyClick { styleId = s.id }) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(s.colors))
                            .then(if (selected) Modifier.border(3.dp, PinglyColors.Ink, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = s.text, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(s.title, style = MaterialTheme.typography.labelSmall, color = if (selected) PinglyColors.Ink else PinglyColors.InkSoft)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        PinglyTextField(
            text, { text = it }, placeholder = "هرچی تو دلته بنویس…", label = "متن پیام",
            icon = Icons.Rounded.EditNote, singleLine = false, minLines = 3, maxLength = 300,
        )
        SectionHeader("پیشنهادهای آماده")
        PinglyCard(padding = 6.dp) {
            Presets.messages.forEachIndexed { index, preset ->
                if (index > 0) RowDivider()
                ListRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = preset,
                    iconTint = EventType.Message.visual.tint,
                    iconBackground = EventType.Message.visual.background,
                    onClick = { text = preset },
                    trailing = { Icon(Icons.Rounded.Add, contentDescription = "انتخاب", tint = PinglyColors.InkFaint, modifier = Modifier.size(20.dp)) },
                )
            }
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

    val validOptions = options.count { it.isNotBlank() } >= 2
    PinglyScreen(
        title = "سؤال بپرس",
        subtitle = "${couple.partnerName} همون لحظه جواب می‌ده",
        onBack = onBack,
        bottom = {
            PrimaryButton(
                "ارسال سؤال",
                onClick = { vm.sendQuestion(question, if (freeAnswer) emptyList() else options.toList()) { question = "" } },
                icon = Icons.AutoMirrored.Rounded.Send,
                loading = busy,
                enabled = question.isNotBlank() && (freeAnswer || validOptions),
            )
        },
    ) {
        Spacer(Modifier.height(8.dp))
        PinglyTextField(
            question, { question = it }, placeholder = "مثلاً: امشب کجا بریم؟", label = "سؤالت",
            icon = Icons.AutoMirrored.Rounded.HelpOutline, singleLine = false, minLines = 2, maxLength = 200,
        )
        Spacer(Modifier.height(18.dp))
        FieldLabel("نوع جواب")
        SegmentedControl(
            options = listOf("چندگزینه‌ای", "جواب آزاد"),
            icons = listOf(Icons.Rounded.Checklist, Icons.AutoMirrored.Rounded.Notes),
            selected = if (freeAnswer) 1 else 0,
            onSelect = { freeAnswer = it == 1 },
        )
        Spacer(Modifier.height(16.dp))
        if (freeAnswer) {
            InfoLine(Icons.Rounded.Lightbulb, "${couple.partnerName} جوابش رو خودش می‌نویسه.")
        } else {
            options.forEachIndexed { index, value ->
                PinglyTextField(
                    value, { options[index] = it }, placeholder = "گزینه‌ی ${index + 1}",
                    icon = Icons.Rounded.RadioButtonUnchecked, maxLength = 40,
                    trailing = if (options.size > 2) {
                        {
                            Icon(
                                Icons.Rounded.Close, contentDescription = "حذف گزینه", tint = PinglyColors.InkFaint,
                                modifier = Modifier.size(20.dp).clip(CircleShape).bouncyClick { options.removeAt(index) },
                            )
                        }
                    } else null,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (options.size < 4) {
                TonalButton("افزودن گزینه", onClick = { options.add("") }, icon = Icons.Rounded.Add, tint = PinglyColors.Lilac, background = PinglyColors.LilacTint, height = 46.dp)
            }
        }
        SectionHeader("سؤال‌های آماده")
        PinglyCard(padding = 6.dp) {
            Presets.questions.forEachIndexed { index, preset ->
                if (index > 0) RowDivider()
                ListRow(
                    icon = if (preset.options.isEmpty()) Icons.AutoMirrored.Rounded.Notes else Icons.Rounded.Checklist,
                    title = preset.question,
                    subtitle = if (preset.options.isEmpty()) "جواب آزاد" else preset.options.joinToString("  ·  "),
                    iconTint = EventType.Question.visual.tint,
                    iconBackground = EventType.Question.visual.background,
                    onClick = { applyPreset(preset) },
                    trailing = { IconBadge(Icons.Rounded.Add, PinglyColors.InkSoft, PinglyColors.SurfaceMuted, size = 30.dp) },
                )
            }
        }
    }
}
