package ir.byalireza.pingly.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.ui.theme.PinglyColors

/** An icon sitting in a soft tinted circle (or rounded square). */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = CircleShape,
) {
    Box(modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = PinglyColors.InkFaint, modifier = Modifier.size(22.dp))
}

/** Settings-style row: icon tile, title + subtitle, trailing chevron/switch/anything. */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = PinglyColors.Ink,
    iconBackground: Color = PinglyColors.SurfaceMuted,
    titleColor: Color = PinglyColors.Ink,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { Chevron() },
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, iconTint, iconBackground, size = 42.dp, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun PinglySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = PinglyColors.Ink,
            checkedThumbColor = Color.White,
            checkedBorderColor = PinglyColors.Ink,
            uncheckedTrackColor = PinglyColors.SurfaceMuted,
            uncheckedBorderColor = PinglyColors.Border,
            uncheckedThumbColor = PinglyColors.InkFaint,
        ),
    )
}

@Composable
fun SwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    iconTint: Color = PinglyColors.Ink,
    iconBackground: Color = PinglyColors.SurfaceMuted,
    enabled: Boolean = true,
) {
    ListRow(
        icon = icon, title = title, subtitle = subtitle, iconTint = iconTint, iconBackground = iconBackground,
        enabled = enabled, onClick = { onCheckedChange(!checked) },
        trailing = { PinglySwitch(checked, onCheckedChange, enabled) },
    )
}

/** Small pill label, optionally with a leading icon or status dot. */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dot: Boolean = false,
    dotColor: Color? = null,
) {
    Row(
        modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot) Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor ?: color))
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

data class ChipItem(val label: String, val count: Int? = null)

/** Horizontally scrolling filter pills with counters, like "All Tasks 65". */
@Composable
fun FilterChips(items: List<ChipItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selected
            val bg by animateColorAsState(if (isSelected) PinglyColors.Ink else PinglyColors.Surface, label = "chip")
            Row(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .then(if (isSelected) Modifier else Modifier.border(1.dp, PinglyColors.Border, CircleShape))
                    .bouncyClick { onSelect(index) }
                    .padding(start = 16.dp, end = if (item.count != null) 6.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.label, style = MaterialTheme.typography.labelMedium, color = if (isSelected) Color.White else PinglyColors.InkSoft)
                if (item.count != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White.copy(alpha = 0.18f) else PinglyColors.SurfaceMuted)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(Persian.digits(item.count), style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White else PinglyColors.Ink)
                    }
                }
            }
        }
    }
}

/** Two-to-three way switch on a grey track, the selected segment raised in white. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, icons: List<ImageVector?> = emptyList()) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(PinglyColors.SurfaceMuted)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(40.dp)
                    .then(if (isSelected) Modifier.shadow(2.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (isSelected) PinglyColors.Surface else Color.Transparent)
                    .clickable { onSelect(index) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icons.getOrNull(index)?.let {
                    Icon(it, contentDescription = null, tint = if (isSelected) PinglyColors.Ink else PinglyColors.InkFaint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(option, style = MaterialTheme.typography.labelMedium, color = if (isSelected) PinglyColors.Ink else PinglyColors.InkSoft)
            }
        }
    }
}
