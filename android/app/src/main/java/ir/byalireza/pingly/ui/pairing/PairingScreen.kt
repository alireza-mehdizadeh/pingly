package ir.byalireza.pingly.ui.pairing

import android.app.Application
import android.content.Intent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.api.ApiException
import ir.byalireza.pingly.data.api.IncomingInviteDto
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.Avatar
import ir.byalireza.pingly.ui.components.CircleIconButton
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.InfoLine
import ir.byalireza.pingly.ui.components.LargeHeader
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyScreen
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.SectionHeader
import ir.byalireza.pingly.ui.components.SmallButton
import ir.byalireza.pingly.ui.components.ToastKind
import ir.byalireza.pingly.ui.theme.PinglyColors

class PairingViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state

    fun invite(phoneInput: String, onSent: () -> Unit) {
        val phone = Persian.normalizePhone(phoneInput)
        if (phone == null) {
            toast(ApiException.messageFor("invalid_phone"), ToastKind.Error)
            return
        }
        action(success = "دعوتت فرستاده شد") {
            container.account.invite(phone)
            onSent()
        }
    }

    fun cancel(id: String) = action(success = "دعوت لغو شد") { container.account.cancelInvite(id) }
    fun accept(id: String) = action(success = "حالا به هم وصلید") { container.account.acceptInvite(id) }
    fun decline(id: String) = action(success = "دعوت رد شد") { container.account.declineInvite(id) }
    fun refresh() = action { container.account.refresh() }
}

@Composable
fun PairingScreen(onSettings: () -> Unit, vm: PairingViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var phone by rememberSaveable { mutableStateOf("") }
    var declining by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }

    PinglyScreen {
        LargeHeader(
            title = "سلام، ${couple.me?.displayName.orEmpty()}",
            subtitle = "بیا نیمه‌ی دیگه‌ت رو پیدا کنیم",
            leading = { Avatar(couple.me?.displayName, couple.me?.avatar, size = 48.dp) },
        ) {
            CircleIconButton(Icons.Rounded.Refresh, "به‌روزرسانی", vm::refresh)
            CircleIconButton(Icons.Rounded.Settings, "تنظیمات", onSettings)
        }
        WaitingIllustration(couple.me?.displayName, couple.me?.avatar)

        if (couple.invites.incoming.isNotEmpty()) {
            SectionHeader("دعوت‌های تو")
            couple.invites.incoming.forEach { invite ->
                IncomingInviteCard(invite, busy, onAccept = { vm.accept(invite.id) }, onDecline = { declining = invite.id })
                Spacer(Modifier.height(12.dp))
            }
        }

        val outgoing = couple.invites.outgoing
        if (outgoing != null) {
            SectionHeader("دعوت فرستاده‌شده")
            PinglyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Schedule, PinglyColors.Butter, PinglyColors.ButterTint, size = 46.dp, shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("منتظر تأیید ${Persian.prettyPhone(outgoing.toPhone)}", style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                        Text("وقتی پارتنرت پینگلی رو نصب کنه و دعوت رو قبول کنه، به هم وصل می‌شید. دعوت تا ۷ روز معتبره.", style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("فرستادن لینک نصب", icon = Icons.Rounded.Share, onClick = {
                        val text = "بیا پینگلی رو نصب کن تا از راه دور هم کنار هم باشیم.\nبعد از نصب با همین شماره وارد شو؛ دعوتم منتظرته."
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "دعوت به پینگلی"))
                    })
                    SmallButton("لغو دعوت", icon = Icons.Rounded.Close, onClick = { vm.cancel(outgoing.id) }, filled = false, color = PinglyColors.Danger)
                }
            }
        }

        SectionHeader(if (outgoing == null) "دعوت از پارتنر" else "دعوت از شماره‌ی دیگه")
        PinglyCard(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.PersonAddAlt1, PinglyColors.Lilac, PinglyColors.LilacTint, size = 46.dp, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "شماره‌ی موبایل پارتنرت رو وارد کن تا براش دعوت بفرستیم.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PinglyColors.InkSoft,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            PinglyTextField(
                phone, { phone = it.take(14) }, placeholder = "۰۹۳۵ ۱۲۳ ۴۵۶۷", label = "شماره موبایل پارتنر",
                icon = Icons.Rounded.Smartphone, keyboardType = KeyboardType.Phone, ltr = true,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                "ارسال دعوت", onClick = { vm.invite(phone) { phone = "" } }, icon = Icons.AutoMirrored.Rounded.Send,
                loading = busy, enabled = phone.length >= 10,
            )
            Spacer(Modifier.height(12.dp))
            InfoLine(Icons.Rounded.Shield, "تا وقتی خودش دعوت رو قبول نکنه، هیچ اتصالی برقرار نمی‌شه و هیچ کاری روی گوشیش انجام نمی‌شه.")
        }
    }

    declining?.let { id ->
        ConfirmDialog(
            icon = Icons.Rounded.Close,
            title = "دعوت رو رد کنی؟",
            text = "طرف مقابل متوجه می‌شه که دعوتش رد شده. بعداً هم می‌تونه دوباره دعوتت کنه.",
            confirm = "رد کردن دعوت",
            destructive = true,
            onConfirm = {
                vm.decline(id)
                declining = null
            },
            onDismiss = { declining = null },
        )
    }
}

/** You on one side, a dashed "?" on the other, a beating heart in between. */
@Composable
private fun WaitingIllustration(name: String?, avatar: String?) {
    val beat = rememberInfiniteTransition(label = "beat")
    val dash by beat.animateFloat(0f, 24f, infiniteRepeatable(tween(1200)), label = "dash")
    val pulse by beat.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(PinglyColors.SoftGradient)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, avatar, size = 72.dp, ring = Color.White)
                Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        drawLine(
                            PinglyColors.PinkSoft, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                            strokeWidth = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), dash),
                        )
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = PinglyColors.Pink, modifier = Modifier.size((20 * pulse).dp))
                    }
                }
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(72.dp)) {
                        drawCircle(
                            PinglyColors.Lilac, radius = size.minDimension / 2 - 2.dp.toPx(),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), dash)),
                        )
                    }
                    Icon(Icons.Rounded.QuestionMark, contentDescription = null, tint = PinglyColors.Lilac, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("منتظر نیمه‌ی دیگه‌ت هستیم", style = MaterialTheme.typography.titleMedium, color = PinglyColors.Ink)
            Text("دعوت بفرست یا دعوت پارتنرت رو قبول کن", style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun IncomingInviteCard(invite: IncomingInviteDto, busy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    PinglyCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(invite.from.displayName, invite.from.avatar, size = 54.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${invite.from.displayName.ifBlank { "یه نفر" }} می‌خواد با تو وصل بشه", style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                Text(Persian.prettyPhone(invite.from.phone), style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
            }
            IconBadge(Icons.Rounded.MarkEmailUnread, PinglyColors.Pink, PinglyColors.PinkTint, size = 38.dp)
        }
        Spacer(Modifier.height(12.dp))
        InfoLine(Icons.Rounded.Lock, "با قبول دعوت، این شخص می‌تونه برات لرزش، صدا، عکس و پیام بفرسته. هر وقت بخوای می‌تونی اتصال رو قطع کنی یا هر قابلیت رو خاموش کنی.")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("قبول می‌کنم", onClick = onAccept, icon = Icons.Rounded.Check, enabled = !busy, modifier = Modifier.weight(1f))
            ir.byalireza.pingly.ui.components.TonalButton(
                "رد", onClick = onDecline, icon = Icons.Rounded.Close, enabled = !busy,
                modifier = Modifier.weight(0.6f), height = 56.dp, tint = PinglyColors.Danger, background = PinglyColors.DangerTint,
            )
        }
    }
}
