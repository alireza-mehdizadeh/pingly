package ir.byalireza.pingly.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.EmojiNature
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ir.byalireza.pingly.domain.EventStatus
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglySound
import ir.byalireza.pingly.domain.VibrationPattern
import ir.byalireza.pingly.ui.theme.PinglyColors

/** Icon + colour pair that identifies a kind of thing everywhere in the app. */
data class Visual(val icon: ImageVector, val tint: Color, val background: Color)

private val Indigo = Color(0xFF4F5BD5)
private val IndigoTint = Color(0xFFE8EAFC)

val EventType.visual: Visual
    get() = when (this) {
        EventType.Ping -> Visual(Icons.Rounded.Favorite, PinglyColors.Pink, PinglyColors.PinkTint)
        EventType.Vibrate -> Visual(Icons.Rounded.Vibration, PinglyColors.Lilac, PinglyColors.LilacTint)
        EventType.Sound -> Visual(Icons.Rounded.MusicNote, PinglyColors.Peach, PinglyColors.PeachTint)
        EventType.Voice -> Visual(Icons.Rounded.Mic, PinglyColors.Sky, PinglyColors.SkyTint)
        EventType.Image -> Visual(Icons.Rounded.Image, PinglyColors.Mint, PinglyColors.MintTint)
        EventType.Message -> Visual(Icons.Rounded.Mail, PinglyColors.Butter, PinglyColors.ButterTint)
        EventType.Question -> Visual(Icons.Rounded.QuestionAnswer, Indigo, IndigoTint)
        EventType.Answer -> Visual(Icons.AutoMirrored.Rounded.Reply, Indigo, IndigoTint)
    }

val VibrationPattern.icon: ImageVector
    get() = when (this) {
        VibrationPattern.Heartbeat -> Icons.Rounded.MonitorHeart
        VibrationPattern.Hug -> Icons.Rounded.VolunteerActivism
        VibrationPattern.Tickle -> Icons.Rounded.Gesture
        VibrationPattern.Knock -> Icons.Rounded.DoorFront
        VibrationPattern.Butterfly -> Icons.Rounded.EmojiNature
        VibrationPattern.Purr -> Icons.Rounded.Pets
    }

val PinglySound.icon: ImageVector
    get() = when (this) {
        PinglySound.Ping -> Icons.Rounded.WifiTethering
        PinglySound.Chime -> Icons.Rounded.Air
        PinglySound.MusicBox -> Icons.Rounded.MusicNote
        PinglySound.Bubbles -> Icons.Rounded.BubbleChart
        PinglySound.Bells -> Icons.Rounded.NotificationsActive
        PinglySound.Heartbeat -> Icons.Rounded.Favorite
        PinglySound.Sparkle -> Icons.Rounded.AutoAwesome
        PinglySound.Harp -> Icons.Rounded.Piano
    }

/** Text and colours for delivery receipts. */
val EventStatus.colors: Pair<Color, Color>
    get() = when (this) {
        EventStatus.Sending, EventStatus.Sent -> PinglyColors.InkSoft to PinglyColors.SurfaceMuted
        EventStatus.Delivered -> PinglyColors.Sky to PinglyColors.SkyTint
        EventStatus.Opened -> PinglyColors.Mint to PinglyColors.MintTint
        EventStatus.Answered -> Indigo to IndigoTint
        EventStatus.Expired -> PinglyColors.Danger to PinglyColors.DangerTint
    }
