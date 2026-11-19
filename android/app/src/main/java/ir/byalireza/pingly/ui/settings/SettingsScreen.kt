package ir.byalireza.pingly.ui.settings

import android.app.Application
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.BuildConfig
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.ReactionSettings
import ir.byalireza.pingly.data.api.AllowDto
import ir.byalireza.pingly.domain.PinglySound
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.AvatarBubble
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.EmojiBadge
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PillButton
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.SectionTitle
import ir.byalireza.pingly.ui.components.ToggleRow
import ir.byalireza.pingly.ui.components.TopBar
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.theme.PinglyColors

class SettingsViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    val reactions = container.prefs.reactions
    val serverUrl get() = container.prefs.serverUrl

    fun setAllow(allow: AllowDto) = action { container.account.updateAllow(allow) }

    fun updateReactions(transform: (ReactionSettings) -> ReactionSettings) = container.prefs.updateReactions(transform)

    fun testSound() = container.sound.playRaw(PinglySound.Ping.res, "test", fromPartner = true)

    fun setServerUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        if (!clean.startsWith("https://") && !clean.startsWith("http://")) {
            toast("آدرس باید با https:// شروع بشه")
            return
        }
        container.prefs.serverUrl = clean
        toast("آدرس سرور ذخیره شد")
    }

    fun logout(onDone: () -> Unit) = action { container.logout(); onDone() }

    fun deleteAccount() = action { container.deleteAccount() }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onConnection: () -> Unit,
    onReliability: () -> Unit,
    onHistory: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val couple by vm.couple.collectAsState()
    val reactions by vm.reactions.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editServer by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }
    val allow = couple.me?.settings?.allow ?: AllowDto()

    PinglyBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar("تنظیمات ⚙️", onBack = onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                PinglyCard(modifier = Modifier.bouncyClick(onClick = onProfile)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBubble(couple.me?.avatar, size = 60.dp, ring = PinglyColors.PinkSoft)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(couple.me?.displayName.orEmpty(), style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink)
                            Text(Persian.prettyPhone(couple.me?.phone.orEmpty()), style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
                        }
                        Text("✏️", style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (couple.isPaired) {
                    SectionTitle("${couple.partnerName} اجازه داره… 🔐")
                    PinglyCard(padding = 14) {
                        Text(
                            "خودت تعیین می‌کنی پارتنرت چه کارهایی روی گوشیت انجام بده. اگه چیزی رو خاموش کنی، اون کار روی سرور هم مسدود می‌شه.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PinglyColors.InkSoft,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        ToggleRow("📳", "گوشیم رو بلرزونه", checked = allow.vibrate, enabled = !busy, onCheckedChange = { vm.setAllow(allow.copy(vibrate = it)) })
                        ToggleRow("🔔", "صدا پخش کنه", checked = allow.sound, enabled = !busy, onCheckedChange = { vm.setAllow(allow.copy(sound = it)) })
                        ToggleRow("🎙️", "صدای ضبط‌شده‌ش رو پخش کنه", checked = allow.voice, enabled = !busy, onCheckedChange = { vm.setAllow(allow.copy(voice = it)) })
                        ToggleRow("🖼️", "عکس تمام‌صفحه نشون بده", checked = allow.image, enabled = !busy, onCheckedChange = { vm.setAllow(allow.copy(image = it)) })
                        ToggleRow("💌", "پیام عاشقانه بفرسته", checked = allow.message, enabled = !busy, onCheckedChange = { vm.setAllow(allow.copy(message = it)) })
                        ToggleRow("❓", "سؤال بپرسه", checked = allow.question, enabled = !busy, onCheckedChange = { vm.setAllow(allow.copy(question = it)) })
                    }
                }

                SectionTitle("وقتی پارتنرم پینگم می‌کنه 💗")
                PinglyCard(padding = 14) {
                    ToggleRow(
                        "🖼️", "باز شدن تمام‌صفحه",
                        subtitle = "عکس‌ها، پیام‌ها و سؤال‌ها خودشون روی صفحه باز بشن",
                        checked = reactions.fullscreen,
                        onCheckedChange = { on -> vm.updateReactions { it.copy(fullscreen = on) } },
                    )
                    ToggleRow(
                        "🔕", "پخش حتی در حالت بی‌صدا",
                        subtitle = "صداها و لرزش‌ها از کانال هشدار پخش می‌شن",
                        checked = reactions.ringThroughSilent,
                        onCheckedChange = { on -> vm.updateReactions { it.copy(ringThroughSilent = on) } },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        EmojiBadge("🔊")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Persian.digits("بلندی صدا: ${(reactions.volume * 100).toInt()}٪"), style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                            Slider(
                                value = reactions.volume,
                                onValueChange = { v -> vm.updateReactions { it.copy(volume = v) } },
                                valueRange = 0.1f..1f,
                                colors = SliderDefaults.colors(thumbColor = PinglyColors.Pink, activeTrackColor = PinglyColors.Pink, inactiveTrackColor = PinglyColors.PinkMist),
                            )
                        }
                        PillButton("▶", onClick = vm::testSound, filled = false)
                    }
                }

                SectionTitle("اتصال و پایداری")
                PinglyCard(padding = 8) {
                    NavRow("💞", "مدیریت اتصال", if (couple.isPaired) "اتصال با ${couple.partnerName}، کد امنیتی، قطع اتصال" else "هنوز به کسی وصل نیستی", onConnection)
                    NavRow("🛡️", "پایداری در پس‌زمینه", "اعلان، باتری، تمام‌صفحه و اجرای خودکار", onReliability)
                    if (couple.isPaired) NavRow("🕘", "تاریخچه", "همه‌ی پینگ‌ها و پیام‌ها", onHistory)
                }

                SectionTitle("حساب")
                PinglyCard(padding = 8) {
                    NavRow("🌐", "آدرس سرور (پیشرفته)", vm.serverUrl, onClick = { editServer = true })
                    NavRow("🚪", "خروج از حساب", "با خروج، اعلان‌ها و سرویس پس‌زمینه هم متوقف می‌شن", onClick = { confirmLogout = true }, danger = true)
                    NavRow("🗑️", "حذف همیشگی حساب", "پروفایل، اتصال، تاریخچه، عکس‌ها و صداها پاک می‌شن", onClick = { confirmDelete = true }, danger = true)
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    Persian.digits("پینگلی نسخه‌ی ${BuildConfig.VERSION_NAME} · ساخته‌شده با 💗 برای دو نفر"),
                    style = MaterialTheme.typography.bodySmall,
                    color = PinglyColors.InkFaint,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "از پینگلی خارج بشی؟",
            text = "تا دوباره وارد نشی، ${couple.partnerName} نمی‌تونه بهت پینگ بده. کلیدهای رمزنگاری این گوشی هم پاک می‌شن.",
            confirm = "خروج",
            destructive = true,
            onConfirm = {
                confirmLogout = false
                vm.logout {}
            },
            onDismiss = { confirmLogout = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "حسابت برای همیشه پاک بشه؟ 🥺",
            text = "پروفایلت، اتصال با ${couple.partnerName}، کل تاریخچه و همه‌ی عکس‌ها و صداهای مشترک از روی سرور پاک می‌شن و قابل برگشت نیستن. هر وقت بخوای می‌تونی دوباره با همین شماره ثبت‌نام کنی.",
            confirm = "حذف همیشگی",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.deleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (editServer) {
        var url by rememberSaveable { mutableStateOf(vm.serverUrl) }
        AlertDialog(
            onDismissRequest = { editServer = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(30.dp),
            title = { Text("آدرس سرور", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text("فقط اگه سرور اختصاصی پینگلی داری عوضش کن.", style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
                    Spacer(Modifier.height(10.dp))
                    PinglyTextField(url, { url = it }, label = "https://…", keyboardType = KeyboardType.Uri, ltr = true)
                }
            },
            confirmButton = { PillButton("ذخیره", onClick = { vm.setServerUrl(url); editServer = false }) },
            dismissButton = {
                PillButton("پیش‌فرض", onClick = { vm.setServerUrl(BuildConfig.DEFAULT_SERVER_URL); editServer = false }, filled = false)
            },
        )
    }
}

@Composable
private fun NavRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).bouncyClick(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(emoji, background = if (danger) PinglyColors.PinkMist else PinglyColors.LilacMist)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) PinglyColors.Danger else PinglyColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft, maxLines = 1)
        }
        Text("‹", style = MaterialTheme.typography.titleLarge, color = PinglyColors.InkFaint)
    }
}
