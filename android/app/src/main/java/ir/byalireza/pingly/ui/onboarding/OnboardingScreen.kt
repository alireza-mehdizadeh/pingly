package ir.byalireza.pingly.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.byalireza.pingly.ui.components.AnimatedMascot
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.LinkButton
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.Wordmark
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.coroutines.launch

private data class Page(val title: String, val text: String, val tint: Color)

private val pages = listOf(
    Page(
        "یه دنیای کوچیک، فقط برای دو نفر",
        "هر وقت دلت برای عشقت تنگ شد، با یه لمس به گوشیش برس؛ حتی وقتی کیلومترها دورید.",
        PinglyColors.PinkTint,
    ),
    Page(
        "از راه دور، کنارش باش",
        "لرزش ضربان قلب، صدای خودت، یه عکس یا یه پیام عاشقانه که تمام‌صفحه روی گوشیش باز می‌شه.",
        PinglyColors.SkyTint,
    ),
    Page(
        "امن، خصوصی و با رضایت هر دو",
        "اتصال فقط با تأیید پارتنرت برقرار می‌شه، همه‌چیز رمزنگاری سرتاسری داره و هر وقت بخوای می‌تونی مکث کنی.",
        PinglyColors.LilacTint,
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(PinglyColors.Surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(markSize = 30.dp)
            Spacer(Modifier.weight(1f))
            if (!last) LinkButton("رد شدن", onDone, color = PinglyColors.InkSoft)
        }
        HorizontalPager(pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.size(300.dp).clip(RoundedCornerShape(56.dp)).background(page.tint),
                    contentAlignment = Alignment.Center,
                ) {
                    when (index) {
                        0 -> LoveIllustration()
                        1 -> ActionsIllustration()
                        else -> PrivacyIllustration()
                    }
                }
                Spacer(Modifier.height(40.dp))
                Text(page.title, style = MaterialTheme.typography.headlineLarge, color = PinglyColors.Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    page.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PinglyColors.InkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val selected = pagerState.currentPage == i
                val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dot")
                val color by animateColorAsState(if (selected) PinglyColors.Ink else PinglyColors.Border, label = "dotc")
                Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(width).clip(CircleShape).background(color))
            }
        }
        PrimaryButton(
            text = if (last) "شروع کنیم" else "بعدی",
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            onClick = { if (last) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
        )
    }
}

/** A white floating icon chip that bobs gently. */
@Composable
private fun BoxScope.FloatingBadge(icon: ImageVector, tint: Color, x: Dp, y: Dp, delay: Int, size: Dp = 52.dp) {
    val bob = rememberInfiniteTransition(label = "bob")
    val dy by bob.animateFloat(0f, -8f, infiniteRepeatable(tween(1300 + delay), RepeatMode.Reverse), label = "dy")
    Box(
        Modifier
            .align(Alignment.Center)
            .offset(x = x, y = y + dy.dp)
            .size(size)
            .shadow(10.dp, RoundedCornerShape(18.dp), spotColor = tint)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
private fun LoveIllustration() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.Center).size(190.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
        AnimatedMascot(Modifier.align(Alignment.Center), size = 170.dp)
        FloatingBadge(Icons.Rounded.Favorite, PinglyColors.Pink, x = (-104).dp, y = (-92).dp, delay = 0)
        FloatingBadge(Icons.Rounded.Vibration, PinglyColors.Lilac, x = 106.dp, y = (-40).dp, delay = 300)
        FloatingBadge(Icons.Rounded.Mic, PinglyColors.Sky, x = (-96).dp, y = 96.dp, delay = 600, size = 46.dp)
    }
}

@Composable
private fun ActionsIllustration() {
    Box(Modifier.fillMaxSize()) {
        // A minimal phone with the vibration action on screen.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 128.dp, height = 214.dp)
                .shadow(18.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(PinglyColors.Ink)
                .padding(6.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(Icons.Rounded.Vibration, PinglyColors.Lilac, PinglyColors.LilacTint, size = 64.dp)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.size(width = 62.dp, height = 8.dp).clip(CircleShape).background(PinglyColors.SurfaceMuted))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(width = 40.dp, height = 8.dp).clip(CircleShape).background(PinglyColors.SurfaceMuted))
            }
        }
        FloatingBadge(Icons.Rounded.MusicNote, PinglyColors.Peach, x = (-104).dp, y = (-70).dp, delay = 0)
        FloatingBadge(Icons.Rounded.Image, PinglyColors.Mint, x = 104.dp, y = (-96).dp, delay = 250)
        FloatingBadge(Icons.Rounded.Mail, PinglyColors.Butter, x = 100.dp, y = 76.dp, delay = 500)
        FloatingBadge(Icons.Rounded.Favorite, PinglyColors.Pink, x = (-100).dp, y = 88.dp, delay = 750, size = 44.dp)
    }
}

@Composable
private fun PrivacyIllustration() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(150.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.75f))
                .border(1.dp, PinglyColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconBadge(Icons.Rounded.Lock, Color.White, PinglyColors.Ink, size = 96.dp)
        }
        FloatingBadge(Icons.Rounded.VerifiedUser, PinglyColors.Mint, x = (-104).dp, y = (-84).dp, delay = 0)
        FloatingBadge(Icons.Rounded.Key, PinglyColors.Butter, x = 104.dp, y = (-56).dp, delay = 300)
        FloatingBadge(Icons.Rounded.PauseCircle, PinglyColors.Lilac, x = 92.dp, y = 96.dp, delay = 600, size = 46.dp)
        FloatingBadge(Icons.Rounded.Favorite, PinglyColors.Pink, x = (-96).dp, y = 92.dp, delay = 900, size = 46.dp)
    }
}
