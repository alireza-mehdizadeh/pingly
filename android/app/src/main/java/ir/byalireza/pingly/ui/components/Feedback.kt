package ir.byalireza.pingly.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

// ── Toasts ──────────────────────────────────────────────────────────────────

enum class ToastKind { Success, Error, Info }

data class ToastMessage(val text: String, val kind: ToastKind = ToastKind.Info)

/** App-wide toasts. Screens call `toaster.show(...)`. */
class Toaster {
    private val _messages = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<ToastMessage> = _messages
    fun show(message: ToastMessage) {
        _messages.tryEmit(message)
    }
    fun success(text: String) = show(ToastMessage(text, ToastKind.Success))
    fun error(text: String) = show(ToastMessage(text, ToastKind.Error))
    fun info(text: String) = show(ToastMessage(text, ToastKind.Info))
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier, bottomPadding: Dp = 24.dp) {
    var current by remember { mutableStateOf<ToastMessage?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toaster) {
        toaster.messages.collect { message ->
            current = message
            visible = true
            delay(2_800)
            visible = false
            delay(250)
        }
    }
    Box(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = bottomPadding), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            val message = current ?: return@AnimatedVisibility
            val (icon, tint) = when (message.kind) {
                ToastKind.Success -> Icons.Rounded.CheckCircle to PinglyColors.Mint
                ToastKind.Error -> Icons.Rounded.ErrorOutline to PinglyColors.PinkSoft
                ToastKind.Info -> Icons.Rounded.Info to PinglyColors.LilacSoft
            }
            Row(
                Modifier
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(PinglyColors.Ink)
                    .padding(start = 14.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(message.text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ── Dialogs & empty states ─────────────────────────────────────────────────

@Composable
fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "انصراف",
    destructive: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(PinglyColors.Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(
                icon,
                tint = if (destructive) PinglyColors.Danger else PinglyColors.Lilac,
                background = if (destructive) PinglyColors.DangerTint else PinglyColors.LilacTint,
                size = 60.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = PinglyColors.InkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            PrimaryButton(confirm, onClick = onConfirm, color = if (destructive) PinglyColors.Danger else PinglyColors.Ink)
            Spacer(Modifier.height(6.dp))
            LinkButton(dismiss, onClick = onDismiss, color = PinglyColors.InkSoft)
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = PinglyColors.Lilac,
    background: Color = PinglyColors.LilacTint,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(background.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            IconBadge(icon, tint, background, size = 68.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = PinglyColors.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = PinglyColors.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** A row of explanation with a leading icon — used for small reassurances and tips. */
@Composable
fun InfoLine(icon: ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = PinglyColors.InkSoft) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
    }
}
