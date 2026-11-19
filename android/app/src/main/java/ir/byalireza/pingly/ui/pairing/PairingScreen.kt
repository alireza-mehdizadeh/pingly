package ir.byalireza.pingly.ui.pairing

import android.app.Application
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.R
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.api.ApiException
import ir.byalireza.pingly.data.api.IncomingInviteDto
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.AvatarBubble
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.EmojiIconButton
import ir.byalireza.pingly.ui.components.FloatingHearts
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PillButton
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.TopBar
import ir.byalireza.pingly.ui.theme.PinglyColors
import androidx.compose.runtime.LaunchedEffect

class PairingViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state

    fun invite(phoneInput: String, onSent: () -> Unit) {
        val phone = Persian.normalizePhone(phoneInput)
        if (phone == null) {
            toast(ApiException.messageFor("invalid_phone"))
            return
        }
        action(success = "دعوتت فرستاده شد 💌") {
            container.account.invite(phone)
            onSent()
        }
    }

    fun cancel(id: String) = action(success = "دعوت لغو شد") { container.account.cancelInvite(id) }
    fun accept(id: String) = action(success = "حالا به هم وصلید! 💞") { container.account.acceptInvite(id) }
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

    PinglyBackground {
        FloatingHearts(count = 8, maxAlpha = 0.3f)
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            TopBar("سلام ${couple.me?.displayName.orEmpty()} 👋", subtitle = "بیا پارتنرت رو پیدا کنیم") {
                EmojiIconButton("🔄", onClick = vm::refresh)
                Spacer(Modifier.width(8.dp))
                EmojiIconButton("⚙️", onClick = onSettings)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WaitingForLy()
                Spacer(Modifier.height(16.dp))

                couple.invites.incoming.forEach { invite ->
                    IncomingInviteCard(invite, busy, onAccept = { vm.accept(invite.id) }, onDecline = { declining = invite.id })
                    Spacer(Modifier.height(14.dp))
                }

                val outgoing = couple.invites.outgoing
                if (outgoing != null) {
                    PinglyCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏳", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("منتظر جواب ${Persian.prettyPhone(outgoing.toPhone)}", style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                                Text(
                                    "وقتی پارتنرت پینگلی رو نصب کنه و دعوت رو قبول کنه، به هم وصل می‌شید. دعوت تا ۷ روز معتبره.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PinglyColors.InkSoft,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("📤 فرستادن لینک نصب", onClick = {
                                val text = "بیا پینگلی رو نصب کن تا بتونیم از راه دور هم کنار هم باشیم 💗\nبعد از نصب با همین شماره وارد شو، دعوتم منتظرته!"
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "دعوت به پینگلی"))
                            })
                            PillButton("لغو دعوت", onClick = { vm.cancel(outgoing.id) }, filled = false, color = PinglyColors.InkSoft)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                PinglyCard {
                    Text(
                        if (outgoing == null) "پارتنرت رو دعوت کن 💌" else "دعوت از یه شماره‌ی دیگه",
                        style = MaterialTheme.typography.titleLarge,
                        color = PinglyColors.Ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "شماره‌ی موبایل پارتنرت رو وارد کن. تا وقتی خودش دعوت رو قبول نکنه، هیچ اتصالی برقرار نمی‌شه و هیچ کاری روی گوشیش انجام نمی‌شه.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PinglyColors.InkSoft,
                    )
                    Spacer(Modifier.height(16.dp))
                    PinglyTextField(phone, { phone = it.take(14) }, label = "شماره موبایل پارتنرت", placeholder = "۰۹۳۵۱۲۳۴۵۶۷", keyboardType = KeyboardType.Phone, ltr = true)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("ارسال دعوت 💞", onClick = { vm.invite(phone) { phone = "" } }, loading = busy, enabled = phone.length >= 10)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    declining?.let { id ->
        ConfirmDialog(
            title = "دعوت رو رد کنی؟",
            text = "طرف مقابل متوجه می‌شه که دعوتش رد شده. بعداً هم می‌تونه دوباره دعوتت کنه.",
            confirm = "رد کن",
            destructive = true,
            onConfirm = {
                vm.decline(id)
                declining = null
            },
            onDismiss = { declining = null },
        )
    }
}

/** Pi waiting next to Ly's dotted outline — "your other half goes here". */
@Composable
private fun WaitingForLy() {
    val bob = rememberInfiniteTransition(label = "wait")
    val tilt by bob.animateFloat(-6f, 6f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "tilt")
    val dash by bob.animateFloat(0f, 40f, infiniteRepeatable(tween(1600)), label = "dash")
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val s = size.minDimension / 100f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(50 * s, 50 * s)
                cubicTo(49.4f * s, 40.5f * s, 49.8f * s, 31.5f * s, 51.7f * s, 26 * s)
                cubicTo(54.2f * s, 18.6f * s, 59.4f * s, 14.5f * s, 66.4f * s, 14.5f * s)
                cubicTo(78.4f * s, 14.5f * s, 88 * s, 24.4f * s, 88 * s, 38 * s)
                cubicTo(88 * s, 56.5f * s, 70.5f * s, 73 * s, 58.4f * s, 82.6f * s)
                cubicTo(55.4f * s, 85 * s, 51.6f * s, 84.6f * s, 50.8f * s, 81.2f * s)
                cubicTo(49.4f * s, 74.5f * s, 49.4f * s, 62 * s, 50 * s, 50 * s)
                close()
            }
            drawPath(
                path, PinglyColors.Lilac.copy(alpha = 0.7f),
                style = Stroke(width = 2.5f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * s, 4 * s), dash)),
            )
            drawCircle(PinglyColors.Lilac.copy(alpha = 0.6f), radius = 3 * s, center = Offset(69 * s, 47 * s))
        }
        Image(
            painterResource(R.drawable.ic_mascot_pi), null,
            Modifier.size(200.dp).graphicsLayer {
                rotationZ = tilt
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.45f, 0.85f)
            },
        )
        Text("?", style = MaterialTheme.typography.headlineMedium, color = PinglyColors.Lilac, modifier = Modifier.padding(start = 70.dp, bottom = 30.dp))
    }
}

@Composable
private fun IncomingInviteCard(invite: IncomingInviteDto, busy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    PinglyCard(color = PinglyColors.PinkMist) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBubble(invite.from.avatar, size = 58.dp, ring = PinglyColors.Pink)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${invite.from.displayName.ifBlank { "یه نفر" }} می‌خواد با تو وصل بشه 💞",
                    style = MaterialTheme.typography.titleSmall,
                    color = PinglyColors.Ink,
                )
                Text(Persian.prettyPhone(invite.from.phone), style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "با قبول دعوت، این شخص می‌تونه برات لرزش، صدا، عکس و پیام بفرسته. هر وقت بخوای می‌تونی اتصال رو قطع کنی یا هر قابلیت رو خاموش کنی.",
            style = MaterialTheme.typography.bodySmall,
            color = PinglyColors.InkSoft,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("قبول می‌کنم 💗", onClick = onAccept, enabled = !busy)
            PillButton("نه، ممنون", onClick = onDecline, filled = false, color = PinglyColors.InkSoft, enabled = !busy)
        }
    }
}
