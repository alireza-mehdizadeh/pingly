package ir.byalireza.pingly.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.ui.components.AnimatedMascot
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.TextLink
import ir.byalireza.pingly.ui.theme.PinglyColors
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()

    PinglyBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            AnimatedMascot(size = 150.dp)
            Spacer(Modifier.height(20.dp))
            AnimatedContent(
                targetState = state.step,
                transitionSpec = { (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut()) },
                label = "auth-step",
            ) { step ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (step) {
                        AuthUiState.Step.Phone -> PhoneStep(state, busy, vm)
                        AuthUiState.Step.Code -> CodeStep(state, busy, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneStep(state: AuthUiState, busy: Boolean, vm: AuthViewModel) {
    Text("شماره موبایلت رو وارد کن", style = MaterialTheme.typography.headlineSmall, color = PinglyColors.Ink)
    Spacer(Modifier.height(8.dp))
    Text(
        "یه کد ۶ رقمی برات پیامک می‌کنیم تا مطمئن بشیم خودتی 💌",
        style = MaterialTheme.typography.bodyMedium,
        color = PinglyColors.InkSoft,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    PinglyTextField(
        value = state.phone,
        onValueChange = vm::onPhoneChange,
        label = "شماره موبایل",
        placeholder = "۰۹۱۲۱۲۳۴۵۶۷",
        keyboardType = KeyboardType.Phone,
        ltr = true,
    )
    ErrorText(state.error)
    Spacer(Modifier.height(24.dp))
    PrimaryButton("ارسال کد تأیید", onClick = vm::requestCode, loading = busy, enabled = state.phone.length >= 10)
    Spacer(Modifier.height(16.dp))
    Text(
        "با ورود، قبول می‌کنی که پینگلی فقط با رضایت هر دو نفر کار می‌کنه.",
        style = MaterialTheme.typography.bodySmall,
        color = PinglyColors.InkFaint,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CodeStep(state: AuthUiState, busy: Boolean, vm: AuthViewModel) {
    Text("کد تأیید رو وارد کن", style = MaterialTheme.typography.headlineSmall, color = PinglyColors.Ink)
    Spacer(Modifier.height(8.dp))
    Text(
        "کد به شماره‌ی ${Persian.prettyPhone(state.phone)} فرستاده شد",
        style = MaterialTheme.typography.bodyMedium,
        color = PinglyColors.InkSoft,
        textAlign = TextAlign.Center,
    )
    TextLink("ویرایش شماره ✏️", onClick = vm::editPhone)
    Spacer(Modifier.height(20.dp))
    OtpBoxes(state.code, vm::onCodeChange)
    ErrorText(state.error)
    Spacer(Modifier.height(24.dp))
    PrimaryButton("ورود به پینگلی", onClick = vm::verify, loading = busy, enabled = state.code.length == 6)
    Spacer(Modifier.height(12.dp))
    if (state.resendIn > 0) {
        Text(
            "ارسال دوباره تا ${Persian.duration(state.resendIn * 1000L)}",
            style = MaterialTheme.typography.bodyMedium,
            color = PinglyColors.InkFaint,
        )
    } else {
        TextLink("کد رو نگرفتی؟ دوباره بفرست", onClick = vm::requestCode)
    }
}

/** Six cute boxes over a hidden text field; digits always run left-to-right. */
@Composable
private fun OtpBoxes(code: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(
            value = code,
            onValueChange = onChange,
            modifier = Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { i ->
                        val char = code.getOrNull(i)
                        val active = i == code.length
                        Box(
                            Modifier
                                .size(width = 48.dp, height = 58.dp)
                                .shadow(if (active) 8.dp else 2.dp, RoundedCornerShape(18.dp), spotColor = PinglyColors.Pink)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .border(
                                    width = 2.dp,
                                    color = if (active) PinglyColors.Pink else if (char != null) PinglyColors.PinkSoft else PinglyColors.PinkMist,
                                    shape = RoundedCornerShape(18.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(char?.let { Persian.digits(it.toString()) } ?: "", style = MaterialTheme.typography.headlineSmall, color = PinglyColors.Ink)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(10.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = PinglyColors.Danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
