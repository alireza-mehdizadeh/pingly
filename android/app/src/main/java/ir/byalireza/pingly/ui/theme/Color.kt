package ir.byalireza.pingly.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Pingly design tokens.
 *
 * Calm, airy neutrals carry the layout; deep navy is the action colour (buttons, active
 * states); the brand pink→lilac lives in the hero areas; and five soft accent pairs
 * (strong + tint) colour-code every kind of interaction.
 */
object PinglyColors {
    // Neutrals
    val Background = Color(0xFFF6F7FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF1F2F7)
    val Border = Color(0xFFEAECF2)
    val Ink = Color(0xFF15173D)
    val InkSoft = Color(0xFF6B6F86)
    val InkFaint = Color(0xFFA6AABB)

    // Brand
    val Pink = Color(0xFFFF5C8A)
    val PinkSoft = Color(0xFFFF9BB3)
    val PinkTint = Color(0xFFFFE9F0)
    val Lilac = Color(0xFF7B6CF6)
    val LilacSoft = Color(0xFFC2B6FF)
    val LilacTint = Color(0xFFEFEDFF)

    // Accents
    val Sky = Color(0xFF2F9BDB)
    val SkyTint = Color(0xFFE1F2FC)
    val Peach = Color(0xFFF0883E)
    val PeachTint = Color(0xFFFFEEDF)
    val Mint = Color(0xFF22A876)
    val MintTint = Color(0xFFDDF5EB)
    val Butter = Color(0xFFD99A00)
    val ButterTint = Color(0xFFFFF4D3)
    val Danger = Color(0xFFEF4462)
    val DangerTint = Color(0xFFFDE8EC)

    /** Pi → Ly, the only place the full brand gradient appears. */
    val HeroGradient = Brush.linearGradient(listOf(Color(0xFFFF7FA6), Color(0xFFC77DEB), Color(0xFF7B6CF6)))
    val SoftGradient = Brush.verticalGradient(listOf(Color(0xFFFDEFF4), Color(0xFFF6F7FB)))
}
