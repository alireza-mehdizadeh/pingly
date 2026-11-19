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
    displayLarge = style(40, FontWeight.Bold, 52),
    displayMedium = style(34, FontWeight.Bold, 46),
    displaySmall = style(28, FontWeight.Bold, 40),
    headlineLarge = style(26, FontWeight.Bold, 38),
    headlineMedium = style(22, FontWeight.Bold, 34),
    headlineSmall = style(19, FontWeight.Bold, 30),
    titleLarge = style(17, FontWeight.Bold, 27),
    titleMedium = style(15, FontWeight.Bold, 24),
    titleSmall = style(14, FontWeight.Medium, 22),
    bodyLarge = style(15, FontWeight.Normal, 26),
    bodyMedium = style(13, FontWeight.Normal, 22),
    bodySmall = style(12, FontWeight.Normal, 19),
    labelLarge = style(15, FontWeight.Bold, 22),
    labelMedium = style(12, FontWeight.Medium, 18),
    labelSmall = style(11, FontWeight.Medium, 16),
)
