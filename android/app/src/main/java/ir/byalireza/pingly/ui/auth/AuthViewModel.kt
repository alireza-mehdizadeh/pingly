package ir.byalireza.pingly.ui.auth

import android.app.Application
import androidx.lifecycle.viewModelScope
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.api.ApiException
import ir.byalireza.pingly.data.api.toPersianMessage
import ir.byalireza.pingly.ui.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val step: Step = Step.Phone,
    val phone: String = "",
    val code: String = "",
    val error: String? = null,
    val resendIn: Int = 0,
) {
    enum class Step { Phone, Code }
}

class AuthViewModel(app: Application) : BaseViewModel(app) {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    private var timer: Job? = null

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value.filter { c -> c.isDigit() || c == '+' }.take(14), error = null) }

    fun onCodeChange(value: String) {
        val digits = Persian.toAsciiDigits(value).filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = digits, error = null) }
        if (digits.length == 6) verify()
    }

    fun requestCode() {
        val phone = Persian.normalizePhone(_state.value.phone)
        if (phone == null) {
            _state.update { it.copy(error = ApiException.messageFor("invalid_phone")) }
            return
        }
        action(onError = { e -> _state.update { it.copy(error = e.toPersianMessage()) } }) {
            val result = container.account.requestOtp(phone)
            _state.update { it.copy(step = AuthUiState.Step.Code, phone = phone, code = "", error = null) }
            startTimer(result.retryAfter)
        }
    }

    fun verify() {
        val s = _state.value
        if (s.code.length != 6) return
        action(onError = { e -> _state.update { it.copy(error = e.toPersianMessage(), code = "") } }) {
            container.account.verifyOtp(s.phone, s.code)
        }
    }

    fun editPhone() {
        timer?.cancel()
        _state.update { it.copy(step = AuthUiState.Step.Phone, code = "", error = null, resendIn = 0) }
    }

    private fun startTimer(seconds: Int) {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (left in seconds downTo 0) {
                _state.update { it.copy(resendIn = left) }
                delay(1_000)
            }
        }
    }
}
