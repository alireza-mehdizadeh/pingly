package ir.byalireza.pingly.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.BuildConfig
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.ReactionSettings
import ir.byalireza.pingly.data.api.AllowDto
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglySound
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.Avatar
import ir.byalireza.pingly.ui.components.BrandMark
import ir.byalireza.pingly.ui.components.CircleIconButton
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.LargeHeader
import ir.byalireza.pingly.ui.components.LinkButton
import ir.byalireza.pingly.ui.components.ListRow
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyScreen
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.RowDivider
import ir.byalireza.pingly.ui.components.SectionHeader
import ir.byalireza.pingly.ui.components.SwitchRow
import ir.byalireza.pingly.ui.components.ToastKind
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.components.visual
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
            toast("آدرس باید با https:// شروع بشه", ToastKind.Error)
            return
        }
        container.prefs.serverUrl = clean
        toast("آدرس سرور ذخیره شد", ToastKind.Success)
    }

    fun logout() = action { container.logout() }

    fun deleteAccount() = action { container.deleteAccount() }
}

@Composable
fun SettingsScreen(
    bottomInset: Dp,
    onBack: (() -> Unit)?,
    onProfile: () -> Unit,
    onConnection: () -> Unit,
    onReliability: () -> Unit,
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

    PinglyScreen(title = if (onBack != null) "تنظیمات" else null, onBack = onBack, bottomInset = bottomInset) {
        if (onBack == null) LargeHeader("تنظیمات", subtitle = "پینگلی رو برای خودت تنظیم کن")

        // Profile header, like the reference settings screens.
        PinglyCard(padding = 20.dp, onClick = onProfile) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Avatar(couple.me?.displayName, couple.me?.avatar, size = 84.dp)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PinglyColors.Ink)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { androidx.compose.material3.Icon(Icons.Rounded.Edit, contentDescription = "ویرایش", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
                Spacer(Modifier.height(10.dp))
                Text(couple.me?.displayName.orEmpty(), style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink)
                Text(Persian.prettyPhone(couple.me?.phone.orEmpty()), style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
            }
        }

        if (couple.isPaired) {
            SectionHeader("${couple.partnerName} اجازه داره")
            PinglyCard(padding = 6.dp) {
                Text(
                    "خودت تعیین می‌کنی پارتنرت چه کارهایی روی گوشیت انجام بده؛ چیزی که خاموش کنی روی سرور هم مسدود می‌شه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PinglyColors.InkSoft,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                AllowRow(EventType.Vibrate, Icons.Rounded.Vibration, "گوشیم رو بلرزونه", allow.vibrate, !busy) { vm.setAllow(allow.copy(vibrate = it)) }
                RowDivider()
                AllowRow(EventType.Sound, Icons.Rounded.MusicNote, "صدا پخش کنه", allow.sound, !busy) { vm.setAllow(allow.copy(sound = it)) }
                RowDivider()
                AllowRow(EventType.Voice, Icons.Rounded.Mic, "صدای ضبط‌شده‌ش رو پخش کنه", allow.voice, !busy) { vm.setAllow(allow.copy(voice = it)) }
                RowDivider()
                AllowRow(EventType.Image, Icons.Rounded.Image, "عکس تمام‌صفحه نشون بده", allow.image, !busy) { vm.setAllow(allow.copy(image = it)) }
                RowDivider()
                AllowRow(EventType.Message, Icons.Rounded.Mail, "پیام عاشقانه بفرسته", allow.message, !busy) { vm.setAllow(allow.copy(message = it)) }
                RowDivider()
                AllowRow(EventType.Question, Icons.Rounded.QuestionAnswer, "سؤال بپرسه", allow.question, !busy) { vm.setAllow(allow.copy(question = it)) }
            }
        }

        SectionHeader("وقتی پینگ می‌گیرم")
        PinglyCard(padding = 6.dp) {
            SwitchRow(
                Icons.Rounded.Fullscreen, "باز شدن تمام‌صفحه", reactions.fullscreen,
                onCheckedChange = { on -> vm.updateReactions { it.copy(fullscreen = on) } },
                subtitle = "عکس‌ها، پیام‌ها و سؤال‌ها خودشون روی صفحه باز بشن",
                iconTint = PinglyColors.Mint, iconBackground = PinglyColors.MintTint,
            )
            RowDivider()
            SwitchRow(
                Icons.Rounded.NotificationsOff, "پخش حتی در حالت بی‌صدا", reactions.ringThroughSilent,
                onCheckedChange = { on -> vm.updateReactions { it.copy(ringThroughSilent = on) } },
                subtitle = "صداها و لرزش‌ها از کانال هشدار پخش می‌شن",
                iconTint = PinglyColors.Butter, iconBackground = PinglyColors.ButterTint,
            )
            RowDivider()
            Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.AutoMirrored.Rounded.VolumeUp, PinglyColors.Peach, PinglyColors.PeachTint, size = 42.dp, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Persian.digits("بلندی صدا: ${(reactions.volume * 100).toInt()}٪"), style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink)
                    Slider(
                        value = reactions.volume,
                        onValueChange = { v -> vm.updateReactions { it.copy(volume = v) } },
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(thumbColor = PinglyColors.Ink, activeTrackColor = PinglyColors.Ink, inactiveTrackColor = PinglyColors.SurfaceMuted),
                    )
                }
                Spacer(Modifier.width(8.dp))
                CircleIconButton(Icons.Rounded.PlayArrow, "امتحان صدا", vm::testSound, size = 40.dp, background = PinglyColors.SurfaceMuted, bordered = false)
            }
        }

        SectionHeader("اتصال و پایداری")
        PinglyCard(padding = 6.dp) {
            ListRow(
                Icons.Rounded.Favorite, "مدیریت اتصال",
                subtitle = if (couple.isPaired) "اتصال با ${couple.partnerName}، کد امنیتی، قطع اتصال" else "هنوز به کسی وصل نیستی",
                iconTint = PinglyColors.Pink, iconBackground = PinglyColors.PinkTint, onClick = onConnection,
            )
            RowDivider()
            ListRow(
                Icons.Rounded.Shield, "پایداری در پس‌زمینه", subtitle = "اعلان، باتری، تمام‌صفحه و اجرای خودکار",
                iconTint = PinglyColors.Lilac, iconBackground = PinglyColors.LilacTint, onClick = onReliability,
            )
        }

        SectionHeader("حساب")
        PinglyCard(padding = 6.dp) {
            ListRow(Icons.Rounded.Dns, "آدرس سرور", subtitle = vm.serverUrl, iconTint = PinglyColors.Sky, iconBackground = PinglyColors.SkyTint, onClick = { editServer = true })
            RowDivider()
            ListRow(
                Icons.AutoMirrored.Rounded.Logout, "خروج از حساب", subtitle = "اعلان‌ها و سرویس پس‌زمینه هم متوقف می‌شن",
                iconTint = PinglyColors.Danger, iconBackground = PinglyColors.DangerTint, titleColor = PinglyColors.Danger, onClick = { confirmLogout = true },
            )
            RowDivider()
            ListRow(
                Icons.Rounded.DeleteForever, "حذف همیشگی حساب", subtitle = "پروفایل، اتصال، تاریخچه، عکس‌ها و صداها پاک می‌شن",
                iconTint = PinglyColors.Danger, iconBackground = PinglyColors.DangerTint, titleColor = PinglyColors.Danger, onClick = { confirmDelete = true },
            )
        }

        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(32.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                Persian.digits("پینگلی · نسخه‌ی ${BuildConfig.VERSION_NAME}"),
                style = MaterialTheme.typography.bodySmall,
                color = PinglyColors.InkFaint,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            icon = Icons.AutoMirrored.Rounded.Logout,
            title = "از پینگلی خارج بشی؟",
            text = "تا دوباره وارد نشی، ${couple.partnerName} نمی‌تونه بهت پینگ بده. کلیدهای رمزنگاری این گوشی هم پاک می‌شن.",
            confirm = "خروج",
            destructive = true,
            onConfirm = {
                confirmLogout = false
                vm.logout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            icon = Icons.Rounded.DeleteForever,
            title = "حسابت برای همیشه پاک بشه؟",
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
    if (editServer) ServerDialog(vm.serverUrl, onSave = { vm.setServerUrl(it); editServer = false }, onReset = { vm.setServerUrl(BuildConfig.DEFAULT_SERVER_URL); editServer = false }, onDismiss = { editServer = false })
}

@Composable
private fun AllowRow(type: EventType, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val visual = type.visual
    SwitchRow(icon, title, checked, onChange, iconTint = visual.tint, iconBackground = visual.background, enabled = enabled)
}

@Composable
private fun ServerDialog(current: String, onSave: (String) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(PinglyColors.Surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(Icons.Rounded.Dns, PinglyColors.Sky, PinglyColors.SkyTint, size = 56.dp)
            Spacer(Modifier.height(14.dp))
            Text("آدرس سرور", style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink)
            Text("فقط اگه سرور اختصاصی پینگلی داری عوضش کن.", style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            PinglyTextField(url, { url = it }, placeholder = "https://…", icon = Icons.Rounded.Dns, keyboardType = KeyboardType.Uri, ltr = true)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("ذخیره", onClick = { onSave(url) })
            LinkButton("برگشت به پیش‌فرض", onClick = onReset, color = PinglyColors.InkSoft)
        }
    }
}
