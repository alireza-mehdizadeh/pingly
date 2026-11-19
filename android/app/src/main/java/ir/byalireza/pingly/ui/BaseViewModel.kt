package ir.byalireza.pingly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.byalireza.pingly.AppContainer
import ir.byalireza.pingly.PinglyApp
import ir.byalireza.pingly.data.api.toPersianMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared plumbing: access to the container, a busy flag and Persian error toasts. */
abstract class BaseViewModel(app: Application) : AndroidViewModel(app) {
    protected val container: AppContainer = (app as PinglyApp).container

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    protected fun toast(message: String) {
        _toasts.tryEmit(message)
    }

    /** Runs `block` with the busy flag set; failures become a friendly Persian toast. */
    protected fun action(success: String? = null, onError: (Throwable) -> Unit = {}, block: suspend () -> Unit): Job =
        viewModelScope.launch {
            if (_busy.value) return@launch
            _busy.value = true
            try {
                block()
                success?.let(::toast)
            } catch (e: Exception) {
                onError(e)
                toast(e.toPersianMessage())
            } finally {
                _busy.value = false
            }
        }
}
