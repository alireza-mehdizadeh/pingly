package ir.byalireza.pingly.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val PinglyColorScheme = lightColorScheme(
    primary = PinglyColors.Ink,
    onPrimary = PinglyColors.Surface,
    primaryContainer = PinglyColors.LilacTint,
    onPrimaryContainer = PinglyColors.Ink,
    secondary = PinglyColors.Pink,
    onSecondary = PinglyColors.Surface,
    secondaryContainer = PinglyColors.PinkTint,
    onSecondaryContainer = PinglyColors.Ink,
    tertiary = PinglyColors.Lilac,
    background = PinglyColors.Background,
    onBackground = PinglyColors.Ink,
    surface = PinglyColors.Surface,
    onSurface = PinglyColors.Ink,
    surfaceVariant = PinglyColors.SurfaceMuted,
    onSurfaceVariant = PinglyColors.InkSoft,
    surfaceContainerLowest = PinglyColors.Surface,
    surfaceContainerLow = PinglyColors.Surface,
    surfaceContainer = PinglyColors.Surface,
    surfaceContainerHigh = PinglyColors.Surface,
    surfaceContainerHighest = PinglyColors.SurfaceMuted,
    outline = PinglyColors.Border,
    outlineVariant = PinglyColors.Border,
    error = PinglyColors.Danger,
)

val PinglyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Pingly is Persian-only, so the whole UI is right-to-left regardless of the device language. */
@Composable
fun PinglyTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = PinglyColorScheme,
            typography = PinglyTypography,
            shapes = PinglyShapes,
            content = content,
        )
    }
}
