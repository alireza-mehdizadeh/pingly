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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.byalireza.pingly.R
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlin.math.sin
import kotlin.random.Random

/** The Pingly mark (Pi + Ly + ping burst). */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_pingly_mark), contentDescription = "پینگلی", modifier = modifier.size(size))
}

/** Mark + «پینگلی» wordmark in the brand gradient. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, markSize: Dp = 30.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrandMark(markSize)
        Text(
            "پینگلی",
            style = MaterialTheme.typography.titleLarge.copy(
                brush = Brush.horizontalGradient(listOf(PinglyColors.Lilac, PinglyColors.Pink)),
                fontWeight = FontWeight.Black,
                fontSize = (markSize.value * 0.7f).sp,
            ),
        )
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

/** Hearts drifting upward — reserved for the full-screen love note, where it means something. */
@Composable
fun FloatingHearts(
    modifier: Modifier = Modifier,
    count: Int = 14,
    colors: List<Color> = listOf(Color.White),
    maxAlpha: Float = 0.4f,
) {
    val hearts = remember(count) {
        val random = Random(count * 31)
        List(count) {
            FloatingHeart(
                x = random.nextFloat(),
                size = 10f + random.nextFloat() * 20f,
                speed = 0.5f + random.nextFloat() * 0.8f,
                phase = random.nextFloat(),
                sway = 6f + random.nextFloat() * 16f,
                color = colors[random.nextInt(colors.size)],
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "hearts")
    val time by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(10_000, easing = LinearEasing)), label = "t")
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
    val offsetPx = with(LocalDensity.current) { (size * 0.35f).toPx() }

    Box(modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
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
                transformOrigin = TransformOrigin(0.5f, 0.15f)
            },
        )
    }
}
