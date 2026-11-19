package ir.byalireza.pingly.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ir.byalireza.pingly.ui.theme.PinglyColors

/** Squishes a little when pressed — every tappable thing in Pingly feels soft. */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    brush: Brush = PinglyColors.ButtonGradient,
) {
    val active = enabled && !loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .shadow(if (active) 14.dp else 0.dp, RoundedCornerShape(29.dp), ambientColor = PinglyColors.Pink, spotColor = PinglyColors.Pink)
            .clip(RoundedCornerShape(29.dp))
            .background(brush)
            .bouncyClick(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(loading, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "loading") { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
            } else {
                Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = PinglyColors.Pink,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .bouncyClick(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(27.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.35f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

/** Compact pill button for inline actions on cards. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    color: Color = PinglyColors.Pink,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.alpha(if (enabled) 1f else 0.5f).bouncyClick(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (filled) color else color.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) Color.White else color,
        )
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = PinglyColors.Lilac) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

/** Round icon button with an emoji, used in top bars. */
@Composable
fun EmojiIconButton(emoji: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Boolean = false) {
    Box(modifier) {
        Surface(
            modifier = Modifier.size(46.dp).bouncyClick(onClick = onClick),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(PinglyColors.Pink),
            )
        }
    }
}

@Composable
fun ButtonRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}
