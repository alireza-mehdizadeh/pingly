package ir.byalireza.pingly.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.byalireza.pingly.R
import ir.byalireza.pingly.ui.components.AnimatedMascot
import ir.byalireza.pingly.ui.components.FloatingHearts
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.TextLink
import ir.byalireza.pingly.ui.components.drawHeart
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.coroutines.launch

private data class Page(val title: String, val text: String)

private val pages = listOf(
    Page(
        "سلام به پینگلی 💗",
        "یه دنیای کوچولو فقط برای دو نفر. هر وقت دلت براش تنگ شد، با یه لمس به گوشیش برس.",
    ),
    Page(
        "از راه دور، کنارش باش",
        "لرزشِ ضربان قلب، صدای خودت، یه عکس یا یه پیام عاشقونه که تمام‌صفحه روی گوشیش باز می‌شه.",
    ),
    Page(
        "فقط با رضایت هر دو نفر 🔐",
        "اتصال فقط وقتی برقرار می‌شه که پارتنرت دعوتت رو قبول کنه. همه‌چیز رمزنگاری سرتاسری می‌شه و هر وقت بخوای می‌تونی پینگلی رو مکث کنی.",
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex

    PinglyBackground {
        FloatingHearts(count = 10, maxAlpha = 0.35f)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!last) TextLink("رد شدن", onClick = onDone, color = PinglyColors.InkSoft)
            }
            HorizontalPager(pagerState, modifier = Modifier.weight(1f)) { index ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                        when (index) {
                            0 -> AnimatedMascot(size = 220.dp)
                            1 -> PhoneIllustration()
                            else -> ShieldIllustration()
                        }
                    }
                    Spacer(Modifier.height(36.dp))
                    Text(pages[index].title, style = MaterialTheme.typography.headlineMedium, color = PinglyColors.Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pages[index].text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PinglyColors.InkSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    val selected = pagerState.currentPage == i
                    val width by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
                    val color by animateColorAsState(if (selected) PinglyColors.Pink else PinglyColors.PinkSoft.copy(alpha = 0.4f), label = "dotc")
                    Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(width).clip(CircleShape).background(color))
                }
            }
            PrimaryButton(
                text = if (last) "بزن بریم 💞" else "بعدی",
                onClick = { if (last) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            )
        }
    }
}

/** A phone with Pi inside, buzzing with love waves. */
@Composable
private fun PhoneIllustration() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val wave by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), label = "wave")
    val shake by pulse.animateFloat(-3f, 3f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "shake")
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(250.dp)) {
            for (i in 0..2) {
                val p = (wave + i / 3f) % 1f
                drawCircle(PinglyColors.Pink.copy(alpha = (1f - p) * 0.35f), radius = size.minDimension * (0.28f + p * 0.24f), style = Stroke(width = 5.dp.toPx()))
            }
        }
        Box(
            Modifier
                .size(width = 118.dp, height = 200.dp)
                .graphicsLayer { rotationZ = shake * (if (wave < 0.35f) 1f else 0f) }
                .shadow(16.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(PinglyColors.Ink)
                .padding(7.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PinglyColors.PinkMist),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_pingly_mark), null, Modifier.size(84.dp))
        }
    }
}

/** Both mascots safe under a little lock heart. */
@Composable
private fun ShieldIllustration() {
    val bob = rememberInfiniteTransition(label = "bob")
    val dy by bob.animateFloat(0f, -10f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "dy")
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(240.dp)) {
            drawCircle(PinglyColors.LilacMist, radius = size.minDimension * 0.46f)
            drawCircle(Color.White, radius = size.minDimension * 0.36f)
            drawHeart(PinglyColors.PinkMist, Offset(size.width * 0.18f, size.height * 0.2f), Size(size.width * 0.64f, size.height * 0.6f))
        }
        Image(painterResource(R.drawable.ic_pingly_mark), null, Modifier.size(150.dp).offset(y = dy.dp))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-14).dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .padding(12.dp),
        ) { Text("🔐", style = MaterialTheme.typography.headlineSmall) }
    }
}
