package ir.byalireza.pingly.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun PinglyCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    padding: Int = 20,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = PinglyColors.Lilac.copy(alpha = 0.4f), spotColor = PinglyColors.Lilac.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(28.dp),
        color = color,
    ) {
        Column(Modifier.padding(padding.dp), content = content)
    }
}

/** Screen header with an auto-mirrored back arrow (points right in RTL). */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Surface(Modifier.size(44.dp).bouncyClick(onClick = onBack), shape = CircleShape, color = Color.White, shadowElevation = 3.dp) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "بازگشت", tint = PinglyColors.Ink)
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = PinglyColors.Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
        }
        actions()
    }
}

@Composable
fun PinglyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    ltr: Boolean = false,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(if (maxLength != null) next.take(maxLength) else next) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = PinglyColors.InkFaint) } },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = PinglyColors.Ink,
            textAlign = if (ltr) TextAlign.Left else TextAlign.Start,
            textDirection = if (ltr) androidx.compose.ui.text.style.TextDirection.Ltr else androidx.compose.ui.text.style.TextDirection.Content,
        ),
        supportingText = when {
            supporting != null -> { { Text(supporting) } }
            maxLength != null && !singleLine -> { { Text("${value.length}/$maxLength") } }
            else -> null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PinglyColors.Pink,
            unfocusedBorderColor = PinglyColors.PinkMist,
            focusedLabelColor = PinglyColors.Pink,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            cursorColor = PinglyColors.Pink,
        ),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = PinglyColors.Ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun ToggleRow(
    emoji: String,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .bouncyClick(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(emoji)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PinglyColors.Pink,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = PinglyColors.PinkMist,
                uncheckedBorderColor = PinglyColors.PinkMist,
                uncheckedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
fun EmojiBadge(emoji: String, background: Color = PinglyColors.PinkMist, size: Int = 42) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(background), contentAlignment = Alignment.Center) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = PinglyColors.Ink) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "منصرف شدم",
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(30.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = PinglyColors.InkSoft) },
        confirmButton = {
            PillButton(confirm, onClick = onConfirm, color = if (destructive) PinglyColors.Danger else PinglyColors.Pink)
        },
        dismissButton = { PillButton(dismiss, onClick = onDismiss, filled = false, color = PinglyColors.InkSoft) },
    )
}

// ── Toasts ──────────────────────────────────────────────────────────────────

/** App-wide cute toasts. Screens call `toaster.show("...")`. */
class Toaster {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages
    fun show(message: String) {
        _messages.tryEmit(message)
    }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toaster) {
        toaster.messages.collect { message ->
            current = message
            visible = true
            delay(2_600)
            visible = false
            delay(250)
        }
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PinglyColors.Ink,
                shadowElevation = 8.dp,
                modifier = Modifier.border(1.dp, PinglyColors.Pink.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            ) {
                Text(
                    current.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
fun VerticalSpace(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun Bullet(text: String, emoji: String = "•") {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(emoji, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = PinglyColors.InkSoft)
    }
}
