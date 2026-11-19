package ir.byalireza.pingly.data.realtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import ir.byalireza.pingly.data.api.EventDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.min
import kotlin.random.Random

/** Messages pushed by the server over the realtime socket. */
sealed interface ServerMessage {
    data class Event(val event: EventDto) : ServerMessage
    data class Status(val id: String, val status: String, val at: Long) : ServerMessage
    data class Presence(val online: Boolean, val paused: Boolean, val lastSeenAt: Long?) : ServerMessage
    data class Sync(val reason: String) : ServerMessage
    data object SessionRevoked : ServerMessage
    data object Hello : ServerMessage
}

enum class ConnectionState { Idle, Connecting, Connected, Reconnecting }

/**
 * Keeps one authenticated WebSocket to the server alive for as long as it is wanted.
 *
 * Dead connections are detected by WebSocket pings, and every drop is retried with exponential
 * backoff (1 s → 60 s, with jitter). A network change triggers an immediate retry, so a phone
 * that comes back from a tunnel or switches Wi-Fi ↔ mobile is reachable again within seconds.
 */
class RealtimeClient(
    context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: () -> String,
    private val token: () -> String?,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _state = MutableStateFlow(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 128)
    val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    /** Emitted when the server refuses our token (logged in elsewhere / revoked). */
    var onUnauthorized: (() -> Unit)? = null

    private var socket: WebSocket? = null
    private var wanted = false
    private var attempt = 0
    private var reconnectJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        synchronized(lock) {
            if (wanted) {
                if (socket == null && reconnectJob?.isActive != true) connectLocked()
                return
            }
            wanted = true
            attempt = 0
            registerNetworkCallback()
            connectLocked()
        }
    }

    fun stop() {
        synchronized(lock) {
            wanted = false
            reconnectJob?.cancel()
            reconnectJob = null
            socket?.close(1000, "bye")
            socket = null
            _state.value = ConnectionState.Idle
            unregisterNetworkCallback()
        }
    }

    /** Reconnects right away if we're between attempts (e.g. app opened, network came back). */
    fun nudge() {
        synchronized(lock) {
            if (!wanted || socket != null) return
            reconnectJob?.cancel()
            reconnectJob = null
            connectLocked()
        }
    }

    fun send(obj: JsonObject): Boolean = socket?.send(json.encodeToString(JsonObject.serializer(), obj)) ?: false

    private fun connectLocked() {
        val bearer = token()
        if (!wanted || bearer == null) {
            _state.value = ConnectionState.Idle
            return
        }
        _state.value = if (attempt == 0) ConnectionState.Connecting else ConnectionState.Reconnecting
        val wsUrl = baseUrl().trimEnd('/').replaceFirst(Regex("^http"), "ws") + "/v1/ws"
        val request = Request.Builder().url(wsUrl).header("Authorization", "Bearer $bearer").build()
        socket = http.newWebSocket(request, Listener())
    }

    private fun scheduleReconnectLocked() {
        if (!wanted || reconnectJob?.isActive == true) return
        val base = min(60_000L, 1_000L shl min(attempt, 6))
        val wait = base / 2 + Random.nextLong(base / 2 + 1)
        attempt++
        _state.value = ConnectionState.Reconnecting
        reconnectJob = scope.launch {
            delay(wait)
            synchronized(lock) {
                reconnectJob = null
                if (socket == null) connectLocked()
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (webSocket != socket) return
                attempt = 0
                _state.value = ConnectionState.Connected
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = parse(text) ?: return
            if (message == ServerMessage.SessionRevoked) onUnauthorized?.invoke()
            _messages.tryEmit(message)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket, null)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "socket failure: ${t.message} (http ${response?.code})")
            dropped(webSocket, response?.code)
        }

        private fun dropped(webSocket: WebSocket, httpCode: Int?) {
            val unauthorized = httpCode == 401
            synchronized(lock) {
                if (webSocket != socket) return
                socket = null
                if (unauthorized) {
                    wanted = false
                    _state.value = ConnectionState.Idle
                } else {
                    scheduleReconnectLocked()
                }
            }
            if (unauthorized) onUnauthorized?.invoke()
        }
    }

    private fun parse(text: String): ServerMessage? = try {
        val obj = json.parseToJsonElement(text).jsonObject
        when (obj["t"]?.jsonPrimitive?.contentOrNull) {
            "event" -> ServerMessage.Event(json.decodeFromJsonElement(EventDto.serializer(), obj["event"]!!))
            "status" -> ServerMessage.Status(
                id = obj["id"]!!.jsonPrimitive.content,
                status = obj["status"]!!.jsonPrimitive.content,
                at = obj["at"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis(),
            )
            "presence" -> ServerMessage.Presence(
                online = obj["online"]?.jsonPrimitive?.booleanOrNull ?: false,
                paused = obj["paused"]?.jsonPrimitive?.booleanOrNull ?: false,
                lastSeenAt = obj["lastSeenAt"]?.jsonPrimitive?.longOrNull,
            )
            "sync" -> ServerMessage.Sync(obj["reason"]?.jsonPrimitive?.contentOrNull ?: "")
            "session_revoked" -> ServerMessage.SessionRevoked
            "hello" -> ServerMessage.Hello
            else -> null
        }
    } catch (e: Exception) {
        Log.w(TAG, "unparseable message", e)
        null
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = nudge()
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        runCatching { cm.registerNetworkCallback(request, callback) }.onSuccess { networkCallback = callback }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { appContext.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    private companion object {
        const val TAG = "PinglyRealtime"
    }
}
