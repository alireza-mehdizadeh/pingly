package ir.byalireza.pingly.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.byalireza.pingly.R

/**
 * Vazirmatn Round-Dots, Farsi-digits build: soft round dots for a friendly voice, and every
 * digit is drawn as a Persian digit without any conversion code.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_light, FontWeight.Light),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_black, FontWeight.Black),
)

private fun style(size: Int, weight: FontWeight, line: Int) =
    TextStyle(fontFamily = Vazirmatn, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)

val PinglyTypography = Typography(
    displayLarge = style(44, FontWeight.Black, 60),
    displayMedium = style(36, FontWeight.Black, 50),
    displaySmall = style(30, FontWeight.Black, 44),
    headlineLarge = style(28, FontWeight.Black, 40),
    headlineMedium = style(24, FontWeight.Bold, 36),
    headlineSmall = style(21, FontWeight.Bold, 32),
    titleLarge = style(19, FontWeight.Bold, 28),
    titleMedium = style(16, FontWeight.Bold, 26),
    titleSmall = style(14, FontWeight.Bold, 22),
    bodyLarge = style(16, FontWeight.Normal, 28),
    bodyMedium = style(14, FontWeight.Normal, 24),
    bodySmall = style(12, FontWeight.Normal, 20),
    labelLarge = style(15, FontWeight.Bold, 22),
    labelMedium = style(13, FontWeight.Medium, 20),
    labelSmall = style(11, FontWeight.Medium, 16),
)
