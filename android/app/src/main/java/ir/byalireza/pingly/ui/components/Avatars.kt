package ir.byalireza.pingly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.byalireza.pingly.domain.Avatar
import ir.byalireza.pingly.ui.theme.PinglyColors
import ir.byalireza.pingly.ui.theme.Vazirmatn

/** First letter of the name on the person's gradient — clean, personal, no stock photos. */
@Composable
fun Avatar(name: String?, avatarId: String?, modifier: Modifier = Modifier, size: Dp = 48.dp, ring: Color? = null) {
    val palette = Avatar.fromId(avatarId)
    val initial = name?.trim()?.firstOrNull()?.uppercase() ?: "؟"
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (ring != null) Modifier.background(ring).padding(size * 0.05f) else Modifier),
    ) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(palette.colors)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, color = Color.White, fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

/** The couple: two overlapping avatars with a small heart where they meet. */
@Composable
fun AvatarPair(
    meName: String?,
    meAvatar: String?,
    partnerName: String?,
    partnerAvatar: String?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    ring: Color = Color.White,
) {
    Box(modifier.size(width = size * 1.7f, height = size)) {
        Avatar(meName, meAvatar, Modifier.align(Alignment.CenterStart), size = size, ring = ring)
        Avatar(partnerName, partnerAvatar, Modifier.align(Alignment.CenterEnd), size = size, ring = ring)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 4.dp)
                .size(size * 0.42f)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, PinglyColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = PinglyColors.Pink, modifier = Modifier.size(size * 0.24f))
        }
    }
}
