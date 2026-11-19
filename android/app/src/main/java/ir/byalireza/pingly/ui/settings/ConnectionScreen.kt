package ir.byalireza.pingly.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.WifiTethering
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
import ir.byalireza.pingly.ui.components.AvatarPair
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.EmptyState
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.InfoLine
import ir.byalireza.pingly.ui.components.ListRow
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyScreen
import ir.byalireza.pingly.ui.components.RowDivider
import ir.byalireza.pingly.ui.components.SectionHeader
import ir.byalireza.pingly.ui.components.TonalButton
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

    PinglyScreen(title = "مدیریت اتصال", onBack = onBack) {
        if (partner == null || pair == null) {
            PinglyCard { EmptyState(Icons.Rounded.Favorite, "هنوز به کسی وصل نیستی", "از صفحه‌ی خانه پارتنرت رو دعوت کن.", tint = PinglyColors.Pink, background = PinglyColors.PinkTint) }
            return@PinglyScreen
        }
        Spacer(Modifier.height(8.dp))
        PinglyCard(padding = 20.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarPair(couple.me?.displayName, couple.me?.avatar, couple.partnerName, partner.avatar, size = 64.dp)
                Spacer(Modifier.height(14.dp))
                Text("${couple.me?.displayName.orEmpty()} و ${couple.partnerName}", style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink)
                Text(Persian.prettyPhone(partner.phone), style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
            }
            Spacer(Modifier.height(12.dp))
            val days = Persian.daysBetween(pair.createdAt, System.currentTimeMillis())
            ListRow(Icons.Rounded.CalendarMonth, "تاریخ اتصال", subtitle = Persian.toJalali(pair.createdAt).toString(), iconTint = PinglyColors.Sky, iconBackground = PinglyColors.SkyTint, trailing = null)
            RowDivider()
            ListRow(Icons.Rounded.Favorite, "روزهای با هم", subtitle = if (days <= 0) "امروز روز اولتونه" else Persian.digits("$days روز"), iconTint = PinglyColors.Pink, iconBackground = PinglyColors.PinkTint, trailing = null)
            RowDivider()
            ListRow(
                if (partner.paused) Icons.Rounded.Pause else Icons.Rounded.WifiTethering,
                "وضعیت ${couple.partnerName}",
                subtitle = if (partner.paused) "پینگلی رو مکث کرده" else if (partner.online) "آنلاین و آماده‌ی دریافت" else "آفلاین؛ پیام‌ها بعداً می‌رسن",
                iconTint = if (partner.paused) PinglyColors.Butter else PinglyColors.Mint,
                iconBackground = if (partner.paused) PinglyColors.ButterTint else PinglyColors.MintTint,
                trailing = null,
            )
        }

        SectionHeader("کد امنیتی رمزنگاری")
        PinglyCard(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Lock, PinglyColors.Ink, PinglyColors.LilacTint, size = 42.dp, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(12.dp))
                Text("رمزنگاری سرتاسری فعاله", style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
            }
            Spacer(Modifier.height(14.dp))
            val code = vm.safetyCode()
            if (code == null) {
                Text("کد امنیتی بعد از آماده شدن کلیدهای هر دو گوشی نمایش داده می‌شه.", style = MaterialTheme.typography.bodyMedium, color = PinglyColors.InkSoft)
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val groups = code.split(" ")
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PinglyColors.SurfaceMuted).padding(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        groups.chunked(3).forEach { line ->
                            Text(
                                Persian.digits(line.joinToString("   ")),
                                style = MaterialTheme.typography.headlineSmall,
                                color = PinglyColors.Ink,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                InfoLine(Icons.Rounded.Lock, "پیام‌ها، سؤال‌ها، صداها و عکس‌هاتون روی گوشی رمز می‌شن و حتی سرور پینگلی هم نمی‌تونه بازشون کنه. اگه این کد روی گوشی هر دوتون یکی باشه، یعنی هیچ‌کس وسط راه نیست.")
            }
        }

        SectionHeader("قطع اتصال")
        PinglyCard(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.HeartBroken, PinglyColors.Danger, PinglyColors.DangerTint, size = 42.dp, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "هر کدوم از شما هر وقت بخواد می‌تونه اتصال رو قطع کنه. تاریخچه، عکس‌ها و صداهای مشترک برای همیشه پاک می‌شن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PinglyColors.InkSoft,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            TonalButton(
                "قطع اتصال با ${couple.partnerName}", onClick = { confirm = true }, icon = Icons.Rounded.LinkOff,
                tint = PinglyColors.Danger, background = PinglyColors.DangerTint, enabled = !busy,
            )
        }
    }

    if (confirm) {
        ConfirmDialog(
            icon = Icons.Rounded.LinkOff,
            title = "اتصال قطع بشه؟",
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
