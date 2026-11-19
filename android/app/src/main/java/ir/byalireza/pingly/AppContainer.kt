package ir.byalireza.pingly

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ir.byalireza.pingly.data.AppPrefs
import ir.byalireza.pingly.data.SecureStore
import ir.byalireza.pingly.data.api.ApiClient
import ir.byalireza.pingly.data.realtime.RealtimeClient
import ir.byalireza.pingly.data.realtime.ServerMessage
import ir.byalireza.pingly.data.repo.AccountRepository
import ir.byalireza.pingly.data.repo.EventRepository
import ir.byalireza.pingly.data.repo.MediaKind
import ir.byalireza.pingly.data.repo.MediaRepository
import ir.byalireza.pingly.service.EventReactor
import ir.byalireza.pingly.service.Haptics
import ir.byalireza.pingly.service.Notifications
import ir.byalireza.pingly.service.ServiceController
import ir.byalireza.pingly.service.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency container: one instance per process, owned by [PinglyApp]. */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    val prefs = AppPrefs(context)
    val secure = SecureStore(context)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Same pool, but with WebSocket pings to detect half-open connections quickly. */
    private val socketHttp = http.newBuilder()
        .pingInterval(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    val api = ApiClient(http, json, baseUrl = { prefs.serverUrl }, token = { secure.token })
    val realtime = RealtimeClient(context, socketHttp, json, baseUrl = { prefs.serverUrl }, token = { secure.token })

    val account = AccountRepository(api, prefs, secure, json)
    val events = EventRepository(api, account, json)
    val media = MediaRepository(context, api, account)

    val haptics = Haptics(context, prefs)
    val sound = SoundPlayer(context, prefs)

    private val reactor = EventReactor(context, prefs, account, events, media, haptics, sound, scope, markOpened = ::markOpened)

    init {
        api.onUnauthorized = { onSessionLost(revokedElsewhere = false) }
        realtime.onUnauthorized = { onSessionLost(revokedElsewhere = true) }
        scope.launch { realtime.messages.collect(::onServerMessage) }
    }

    private fun onServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Event -> {
                // Confirm receipt first, so the partner sees ✓✓ even if a reaction fails.
                realtime.send(buildJsonObject { put("t", "ack"); put("id", message.event.id) })
                reactor.react(events.onIncoming(message.event))
            }
            is ServerMessage.Status -> events.onStatus(message.id, message.status)
            is ServerMessage.Presence -> account.applyPresence(message)
            is ServerMessage.Sync -> scope.launch {
                val wasPaired = account.state.value.isPaired
                val before = account.state.value.invites.incoming.map { it.id }.toSet()
                runCatching { account.refresh() }
                val state = account.state.value
                if (message.reason == "pair") {
                    events.clear()
                    media.clear()
                    if (state.isPaired && !wasPaired) {
                        Notifications.update(context, "اتصال برقرار شد 💞", "حالا تو و ${state.partnerName} به هم وصلید.", TAG_PAIR)
                    } else if (!state.isPaired && wasPaired) {
                        Notifications.update(context, "اتصال قطع شد", "اتصال پینگلی بین شما قطع شد.", TAG_PAIR)
                    }
                    if (state.isPaired) runCatching { events.refresh() }
                }
                state.invites.incoming.firstOrNull { it.id !in before }?.let { invite ->
                    val name = invite.from.displayName.ifBlank { invite.from.phone }
                    Notifications.update(context, "یه دعوت عاشقانه داری 💌", "$name می‌خواد تو پینگلی به تو وصل بشه.", TAG_INVITE)
                }
                if (message.reason == "partner") runCatching { media.refresh(MediaKind.Voice) }
            }
            ServerMessage.SessionRevoked -> Unit // handled through onUnauthorized
            ServerMessage.Hello -> Unit
        }
    }

    fun markOpened(eventId: String) {
        val sent = realtime.send(buildJsonObject { put("t", "opened"); put("id", eventId) })
        if (!sent) scope.launch { runCatching { api.markOpened(eventId) } }
    }

    private fun onSessionLost(revokedElsewhere: Boolean) {
        if (!account.loggedIn.value) return
        scope.launch(Dispatchers.Main) {
            account.clearLocal()
            events.clear()
            media.clear()
            realtime.stop()
            ServiceController.stop(context)
            ServiceController.cancelWatchdogs(context)
            if (revokedElsewhere) Notifications.loggedOutElsewhere(context)
        }
    }

    suspend fun logout() = signOut { account.logout(callServer = true) }

    suspend fun deleteAccount() = signOut { account.deleteAccount() }

    private suspend fun signOut(block: suspend () -> Unit) {
        block()
        events.clear()
        media.clear()
        realtime.stop()
        ServiceController.stop(context)
        ServiceController.cancelWatchdogs(context)
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG_PAIR = 10
        const val TAG_INVITE = 11
    }
}
