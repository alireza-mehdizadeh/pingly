package ir.byalireza.pingly.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.byalireza.pingly.R
import ir.byalireza.pingly.domain.Avatar
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlin.math.sin
import kotlin.random.Random

/** Soft pastel gradient with two blurred blobs — the backdrop of every screen. */
@Composable
fun PinglyBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(PinglyColors.BackgroundGradient)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(PinglyColors.PinkSoft.copy(alpha = 0.18f), radius = size.width * 0.55f, center = Offset(size.width * 1.0f, size.height * 0.05f))
            drawCircle(PinglyColors.LilacSoft.copy(alpha = 0.20f), radius = size.width * 0.6f, center = Offset(0f, size.height * 0.85f))
        }
        content()
    }
}

/** Draws a classic heart into a w×h box at `topLeft`. */
fun DrawScope.drawHeart(color: Color, topLeft: Offset, size: Size, alpha: Float = 1f) {
    val (w, h) = size
    val path = Path().apply {
        moveTo(topLeft.x + w / 2, topLeft.y + h * 0.95f)
        cubicTo(topLeft.x + w * 0.05f, topLeft.y + h * 0.62f, topLeft.x - w * 0.02f, topLeft.y + h * 0.22f, topLeft.x + w * 0.27f, topLeft.y + h * 0.08f)
        cubicTo(topLeft.x + w * 0.4f, topLeft.y + h * 0.02f, topLeft.x + w * 0.5f, topLeft.y + h * 0.15f, topLeft.x + w / 2, topLeft.y + h * 0.25f)
        cubicTo(topLeft.x + w * 0.5f, topLeft.y + h * 0.15f, topLeft.x + w * 0.6f, topLeft.y + h * 0.02f, topLeft.x + w * 0.73f, topLeft.y + h * 0.08f)
        cubicTo(topLeft.x + w * 1.02f, topLeft.y + h * 0.22f, topLeft.x + w * 0.95f, topLeft.y + h * 0.62f, topLeft.x + w / 2, topLeft.y + h * 0.95f)
        close()
    }
    drawPath(path, color, alpha = alpha)
}

private class FloatingHeart(val x: Float, val size: Float, val speed: Float, val phase: Float, val sway: Float, val color: Color)

/** Hearts drifting upward forever — used behind love notes, the dashboard and celebrations. */
@Composable
fun FloatingHearts(
    modifier: Modifier = Modifier,
    count: Int = 14,
    colors: List<Color> = listOf(PinglyColors.Pink, PinglyColors.PinkSoft, PinglyColors.Lilac, PinglyColors.LilacSoft),
    maxAlpha: Float = 0.55f,
) {
    val hearts = remember(count) {
        val random = Random(count * 31)
        List(count) {
            FloatingHeart(
                x = random.nextFloat(),
                size = 10f + random.nextFloat() * 22f,
                speed = 0.5f + random.nextFloat() * 0.8f,
                phase = random.nextFloat(),
                sway = 6f + random.nextFloat() * 18f,
                color = colors[random.nextInt(colors.size)],
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "hearts")
    val time by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "t")
    Canvas(modifier.fillMaxSize()) {
        hearts.forEach { heart ->
            val progress = (time * heart.speed + heart.phase) % 1f
            val y = size.height * (1.05f - progress * 1.15f)
            val x = size.width * heart.x + sin((progress * 6.28f * 2) + heart.phase * 6.28f) * heart.sway.dp.toPx()
            val s = heart.size.dp.toPx()
            val alpha = maxAlpha * when {
                progress < 0.15f -> progress / 0.15f
                progress > 0.8f -> (1f - progress) / 0.2f
                else -> 1f
            }
            drawHeart(heart.color, Offset(x - s / 2, y - s / 2), Size(s, s * 0.92f), alpha)
        }
    }
}

/**
 * The animated logo: Pi and Ly slide in from both sides, meet cheek to cheek, and the golden
 * ping bursts above them. Then they keep "breathing" gently.
 */
@Composable
fun AnimatedMascot(modifier: Modifier = Modifier, size: Dp = 180.dp, playIntro: Boolean = true) {
    val slide = remember { Animatable(if (playIntro) 1f else 0f) }
    val burst = remember { Animatable(if (playIntro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (playIntro) {
            slide.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            burst.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
        }
    }
    val breathe = rememberInfiniteTransition(label = "breathe")
    val scale by breathe.animateFloat(1f, 1.04f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")

    Box(modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
        val offsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.35f).toPx() }
        // Pi and Ly are drawn on the same canvas as the logo, so they line up perfectly.
        Image(
            painterResource(R.drawable.ic_mascot_pi), null,
            Modifier.fillMaxSize().graphicsLayer {
                translationX = -offsetPx * slide.value
                rotationZ = -12f * slide.value
                alpha = 1f - slide.value * 0.3f
            },
        )
        Image(
            painterResource(R.drawable.ic_mascot_ly), null,
            Modifier.fillMaxSize().graphicsLayer {
                translationX = offsetPx * slide.value
                rotationZ = 12f * slide.value
                alpha = 1f - slide.value * 0.3f
            },
        )
        Image(
            painterResource(R.drawable.ic_ping_burst), null,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = burst.value
                scaleY = burst.value
                alpha = burst.value.coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.15f)
            },
        )
    }
}

@Composable
fun AvatarBubble(avatarId: String?, modifier: Modifier = Modifier, size: Dp = 64.dp, ring: Color? = null) {
    val avatar = Avatar.fromId(avatarId)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ring ?: Color.White)
            .then(if (ring != null) Modifier else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (ring != null) size - 6.dp else size)
                .clip(CircleShape)
                .background(avatar.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(avatar.emoji, fontSize = (size.value * 0.5f).sp)
        }
    }
}

/** Small mascot with a caption — for empty lists and waiting states. */
@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier, mascot: Int = R.drawable.ic_mascot_pi) {
    androidx.compose.foundation.layout.Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val bob = rememberInfiniteTransition(label = "bob")
        val dy by bob.animateFloat(0f, -8f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
        Image(painterResource(mascot), null, Modifier.size(110.dp).graphicsLayer { translationY = dy.dp.toPx() })
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = PinglyColors.Ink)
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = PinglyColors.InkSoft,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.alpha(0.9f),
        )
    }
}
