package ir.byalireza.pingly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.byalireza.pingly.AppContainer
import ir.byalireza.pingly.PinglyApp
import ir.byalireza.pingly.data.api.toPersianMessage
import ir.byalireza.pingly.ui.components.ToastKind
import ir.byalireza.pingly.ui.components.ToastMessage
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

    private val _toasts = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 8)
    val toasts: SharedFlow<ToastMessage> = _toasts.asSharedFlow()

    protected fun toast(message: String, kind: ToastKind = ToastKind.Info) {
        _toasts.tryEmit(ToastMessage(message, kind))
    }

    /** Runs `block` with the busy flag set; failures become a friendly Persian toast. */
    protected fun action(success: String? = null, onError: (Throwable) -> Unit = {}, block: suspend () -> Unit): Job =
        viewModelScope.launch {
            if (_busy.value) return@launch
            _busy.value = true
            try {
                block()
                success?.let { toast(it, ToastKind.Success) }
            } catch (e: Exception) {
                onError(e)
                toast(e.toPersianMessage(), ToastKind.Error)
            } finally {
                _busy.value = false
            }
        }
}
