package ir.byalireza.pingly.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.ui.components.AnimatedMascot
import ir.byalireza.pingly.ui.components.CircleIconButton
import ir.byalireza.pingly.ui.components.InfoLine
import ir.byalireza.pingly.ui.components.LinkButton
import ir.byalireza.pingly.ui.components.PinglyScreen
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.theme.PinglyColors

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()

    AnimatedContent(state.step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "auth-step") { step ->
        when (step) {
            AuthUiState.Step.Phone -> PhoneStep(state, busy, vm)
            AuthUiState.Step.Code -> CodeStep(state, busy, vm)
        }
    }
}

@Composable
private fun PhoneStep(state: AuthUiState, busy: Boolean, vm: AuthViewModel) {
    PinglyScreen(
        background = PinglyColors.Surface,
        bottom = {
            PrimaryButton(
                "دریافت کد تأیید", onClick = vm::requestCode, icon = Icons.AutoMirrored.Rounded.ArrowForward,
                loading = busy, enabled = state.phone.length >= 10,
            )
        },
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(150.dp).clip(CircleShape).background(PinglyColors.PinkTint),
            contentAlignment = Alignment.Center,
        ) { AnimatedMascot(size = 118.dp) }
        Spacer(Modifier.height(32.dp))
        Text("ورود به پینگلی", style = MaterialTheme.typography.headlineLarge, color = PinglyColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "شماره موبایلت رو وارد کن تا یه کد تأیید برات پیامک کنیم.",
            style = MaterialTheme.typography.bodyLarge,
            color = PinglyColors.InkSoft,
        )
        Spacer(Modifier.height(28.dp))
        PinglyTextField(
            value = state.phone,
            onValueChange = vm::onPhoneChange,
            label = "شماره موبایل",
            placeholder = "۰۹۱۲ ۳۴۵ ۶۷۸۹",
            icon = Icons.Rounded.Smartphone,
            keyboardType = KeyboardType.Phone,
            ltr = true,
        )
        ErrorLine(state.error)
        Spacer(Modifier.height(16.dp))
        InfoLine(Icons.Rounded.Lock, "شماره‌ت فقط برای ورود و پیدا کردن پارتنرت استفاده می‌شه و به هیچ‌کس نشون داده نمی‌شه.")
    }
}

@Composable
private fun CodeStep(state: AuthUiState, busy: Boolean, vm: AuthViewModel) {
    PinglyScreen(
        title = "تأیید شماره",
        onBack = vm::editPhone,
        background = PinglyColors.Surface,
        bottom = {
            PrimaryButton("تأیید و ورود", onClick = vm::verify, icon = Icons.Rounded.Check, loading = busy, enabled = state.code.length == 6)
        },
    ) {
        Spacer(Modifier.height(24.dp))
        Text("کد تأیید رو وارد کن", style = MaterialTheme.typography.headlineLarge, color = PinglyColors.Ink)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "کد ۶ رقمی به ${Persian.prettyPhone(state.phone)} فرستاده شد",
                style = MaterialTheme.typography.bodyLarge,
                color = PinglyColors.InkSoft,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(Icons.Rounded.Edit, "ویرایش شماره", vm::editPhone, size = 36.dp, background = PinglyColors.LilacTint, tint = PinglyColors.Lilac, bordered = false)
        }
        Spacer(Modifier.height(32.dp))
        OtpBoxes(state.code, vm::onCodeChange)
        ErrorLine(state.error)
        Spacer(Modifier.height(20.dp))
        if (state.resendIn > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = PinglyColors.Lilac, modifier = Modifier.size(18.dp))
                Text(
                    "ارسال دوباره تا ${Persian.duration(state.resendIn * 1000L)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = PinglyColors.Lilac,
                )
            }
        } else {
            LinkButton("ارسال دوباره‌ی کد", onClick = vm::requestCode, icon = Icons.Rounded.Refresh)
        }
    }
}

/** Six soft boxes over a hidden field; digits always run left-to-right. */
@Composable
private fun OtpBoxes(code: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(
            value = code,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            decorationBox = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { i ->
                        val char = code.getOrNull(i)
                        val active = i == code.length
                        Box(
                            Modifier
                                .weight(1f)
                                .height(62.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) PinglyColors.Surface else PinglyColors.SurfaceMuted)
                                .border(1.5.dp, if (active) PinglyColors.Ink else Color.Transparent, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                char?.let { Persian.digits(it.toString()) } ?: "",
                                style = MaterialTheme.typography.headlineMedium,
                                color = PinglyColors.Ink,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ErrorLine(error: String?) {
    if (error == null) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = PinglyColors.Danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = PinglyColors.Danger)
    }
}
