package ir.byalireza.pingly.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.AvatarBubble
import ir.byalireza.pingly.ui.components.Bullet
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.EmptyState
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.SecondaryButton
import ir.byalireza.pingly.ui.components.SectionTitle
import ir.byalireza.pingly.ui.components.TopBar
import ir.byalireza.pingly.ui.theme.PinglyColors

class ConnectionViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    fun safetyCode(): String? = container.account.safetyCode()

    fun unpair(onDone: () -> Unit) = action(success = "اتصال قطع شد") {
        container.account.unpair()
        container.events.clear()
        container.media.clear()
        onDone()
    }
}

@Composable
fun ConnectionScreen(onBack: () -> Unit, vm: ConnectionViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }
    val partner = couple.partner
    val pair = couple.pair

    PinglyBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar("مدیریت اتصال 💞", onBack = onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                if (partner == null || pair == null) {
                    EmptyState("هنوز به کسی وصل نیستی", "از صفحه‌ی اصلی پارتنرت رو دعوت کن 💌", Modifier.fillMaxWidth().padding(top = 40.dp))
                    return@Column
                }
                PinglyCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBubble(partner.avatar, size = 64.dp, ring = PinglyColors.LilacSoft)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(couple.partnerName, style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink)
                            Text(Persian.prettyPhone(partner.phone), style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val since = Persian.toJalali(pair.createdAt)
                    Bullet("از ${since} به هم وصلید", "📅")
                    Bullet(Persian.digits("${Persian.daysBetween(pair.createdAt, System.currentTimeMillis())} روز با پینگلی"), "💗")
                    Bullet(if (partner.paused) "${couple.partnerName} الان پینگلی رو مکث کرده" else "پینگلی ${couple.partnerName} روشنه", if (partner.paused) "⏸" else "✅")
                }

                SectionTitle("کد امنیتی رمزنگاری 🔐")
                PinglyCard(color = PinglyColors.LilacMist) {
                    val code = vm.safetyCode()
                    if (code == null) {
                        Text("کد امنیتی بعد از آماده شدن کلیدهای هر دو گوشی نمایش داده می‌شه.", style = MaterialTheme.typography.bodyMedium, color = PinglyColors.InkSoft)
                    } else {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                Persian.digits(code),
                                style = MaterialTheme.typography.headlineSmall,
                                color = PinglyColors.Ink,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(androidx.compose.ui.graphics.Color.White)
                                    .padding(16.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "پیام‌ها، سؤال‌ها، صداها و عکس‌هاتون سرتاسری رمز می‌شن و حتی سرور پینگلی هم نمی‌تونه بازشون کنه. اگه این کد روی گوشی هر دوتون یکی باشه، یعنی هیچ‌کس وسط راه نیست.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PinglyColors.InkSoft,
                        )
                    }
                }

                SectionTitle("قطع اتصال")
                PinglyCard {
                    Text(
                        "هر کدوم از شما هر وقت بخواد می‌تونه اتصال رو قطع کنه. با قطع اتصال، تاریخچه، عکس‌ها و صداهای مشترک برای همیشه پاک می‌شن.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PinglyColors.InkSoft,
                    )
                    Spacer(Modifier.height(14.dp))
                    SecondaryButton("قطع اتصال با ${couple.partnerName}", onClick = { confirm = true }, enabled = !busy, color = PinglyColors.Danger)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "مطمئنی می‌خوای اتصال رو قطع کنی؟ 💔",
            text = "همه‌ی تاریخچه، عکس‌ها و صداهای مشترک شما از روی سرور پاک می‌شه و قابل برگشت نیست. ${couple.partnerName} هم باخبر می‌شه.",
            confirm = "قطع اتصال",
            destructive = true,
            onConfirm = {
                confirm = false
                vm.unpair(onBack)
            },
            onDismiss = { confirm = false },
        )
    }
}
