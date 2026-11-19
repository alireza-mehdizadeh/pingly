package ir.byalireza.pingly.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Pingly palette — see branding/README.md. */
object PinglyColors {
    val Pink = Color(0xFFFF6F91)
    val PinkDeep = Color(0xFFFF5C86)
    val PinkSoft = Color(0xFFFF9BB3)
    val PinkMist = Color(0xFFFFE0EA)
    val Lilac = Color(0xFF9B8CFF)
    val LilacDeep = Color(0xFF8B78F4)
    val LilacSoft = Color(0xFFC2B6FF)
    val LilacMist = Color(0xFFEDE8FF)
    val Peach = Color(0xFFFFB38A)
    val PeachMist = Color(0xFFFFEDE2)
    val Butter = Color(0xFFFFC24B)
    val ButterMist = Color(0xFFFFF3D6)
    val Mint = Color(0xFF7ED9C0)
    val MintDeep = Color(0xFF3FB99A)
    val MintMist = Color(0xFFE2F7F1)
    val Ink = Color(0xFF3D2645)
    val InkSoft = Color(0xFF7A6380)
    val InkFaint = Color(0xFFB7A6BB)
    val Cream = Color(0xFFFFF7F3)
    val Surface = Color(0xFFFFFFFF)
    val Danger = Color(0xFFE5486B)

    /** Pi → Ly: every brand gradient runs pink to lilac. */
    val BrandGradient = Brush.linearGradient(listOf(PinkSoft, Pink, Lilac))
    val ButtonGradient = Brush.horizontalGradient(listOf(Pink, LilacDeep))
    val BackgroundGradient = Brush.verticalGradient(listOf(Cream, Color(0xFFFFEEF3), Color(0xFFF4EFFF)))
}
