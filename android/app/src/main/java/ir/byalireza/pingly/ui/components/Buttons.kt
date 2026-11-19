package ir.byalireza.pingly.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.byalireza.pingly.ui.theme.PinglyColors

/** Squishes a little when pressed — every tappable thing in Pingly feels soft. */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** Full-width navy pill — the main action of a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = PinglyColors.Ink,
    contentColor: Color = Color.White,
) {
    val active = enabled && !loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.3f))
            .bouncyClick(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(loading, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "loading") { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = contentColor, strokeWidth = 2.5.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                    Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
                }
            }
        }
    }
}

/** Soft full-width button on a tinted background — secondary actions. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = PinglyColors.Ink,
    background: Color = PinglyColors.SurfaceMuted,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(background)
            .bouncyClick(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp).padding(end = 0.dp))
        if (icon != null) Box(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** Compact pill for inline actions on cards and rows. */
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    color: Color = PinglyColors.Ink,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.1f))
            .bouncyClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val content = if (filled) Color.White else color
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Round icon button (back, more, play, send…). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = PinglyColors.Surface,
    tint: Color = PinglyColors.Ink,
    bordered: Boolean = true,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, PinglyColors.Border, CircleShape) else Modifier)
            .bouncyClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Text-only action ("see all", "edit number"). */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = PinglyColors.Lilac, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
    }
}
