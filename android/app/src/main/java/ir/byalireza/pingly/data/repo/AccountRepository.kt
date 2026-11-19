package ir.byalireza.pingly.data.repo

import android.os.Build
import ir.byalireza.pingly.data.AppPrefs
import ir.byalireza.pingly.data.SecureStore
import ir.byalireza.pingly.data.api.AllowDto
import ir.byalireza.pingly.data.api.ApiClient
import ir.byalireza.pingly.data.api.MeDto
import ir.byalireza.pingly.data.api.OtpRequestResponse
import ir.byalireza.pingly.data.api.VerifyResponse
import ir.byalireza.pingly.data.crypto.E2ee
import ir.byalireza.pingly.data.realtime.ServerMessage
import ir.byalireza.pingly.domain.CoupleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The signed-in user, their partner and pending invites — plus the device's end-to-end keys.
 * The server is the source of truth; a cached copy keeps names available offline.
 */
class AccountRepository(
    private val api: ApiClient,
    private val prefs: AppPrefs,
    private val secure: SecureStore,
    private val json: Json,
) {
    private val _state = MutableStateFlow(CoupleState())
    val state: StateFlow<CoupleState> = _state.asStateFlow()

    private val _loggedIn = MutableStateFlow(secure.token != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val refreshMutex = Mutex()
    private var cachedKey: Pair<String, ByteArray>? = null

    val myId: String? get() = state.value.me?.id ?: prefs.userId

    init {
        prefs.cachedMe?.let { cached ->
            runCatching { json.decodeFromString(MeDto.serializer(), cached) }.getOrNull()?.let { apply(it, loaded = false) }
        }
    }

    // ── Auth ────────────────────────────────────────────────────────────────
    suspend fun requestOtp(phone: String): OtpRequestResponse = api.requestOtp(phone)

    suspend fun verifyOtp(phone: String, code: String): VerifyResponse {
        val result = api.verifyOtp(phone, code, "${Build.MANUFACTURER} ${Build.MODEL}".take(80))
        secure.token = result.token
        prefs.userId = result.user.id
        _loggedIn.value = true
        ensureKeys()
        refresh()
        return result
    }

    /** Creates this phone's ECDH key pair once, and makes sure the server has the public half. */
    private suspend fun ensureKeys() {
        if (secure.privateKey == null || secure.publicKey == null) {
            val keys = E2ee.generateKeyPair()
            secure.privateKey = keys.privateKeyPkcs8
            secure.publicKey = keys.publicKeyBase64
        }
        val publicKey = secure.publicKey ?: return
        if (state.value.me?.publicKey != publicKey) {
            val user = api.updateMe(buildJsonObject { put("publicKey", publicKey) })
            _state.update { it.copy(me = user) }
        }
    }

    suspend fun refresh(): CoupleState = refreshMutex.withLock {
        val me = api.me()
        apply(me, loaded = true)
        if (secure.publicKey == null || me.user.publicKey != secure.publicKey) ensureKeys()
        // The server is authoritative for Pause (e.g. after reinstalling the app).
        if (prefs.paused.value != me.user.paused) prefs.setPaused(me.user.paused)
        state.value
    }

    fun apply(me: MeDto, loaded: Boolean = true) {
        _state.value = CoupleState(loaded = loaded, me = me.user, pair = me.pair, partner = me.partner, invites = me.invites)
        prefs.cachedMe = json.encodeToString(MeDto.serializer(), me)
    }

    fun applyPresence(presence: ServerMessage.Presence) {
        _state.update { s ->
            s.copy(partner = s.partner?.copy(online = presence.online, paused = presence.paused, lastSeenAt = presence.lastSeenAt))
        }
    }

    // ── Profile & consent ───────────────────────────────────────────────────
    suspend fun updateProfile(name: String? = null, avatar: String? = null) {
        val user = api.updateMe(
            buildJsonObject {
                if (name != null) put("displayName", name.trim())
                if (avatar != null) put("avatar", avatar)
            },
        )
        _state.update { it.copy(me = user) }
    }

    /** What the partner is allowed to do on this phone. Enforced by the server too. */
    suspend fun updateAllow(allow: AllowDto) {
        val user = api.updateMe(
            buildJsonObject {
                putJsonObject("settings") {
                    putJsonObject("allow") {
                        put("vibrate", allow.vibrate)
                        put("sound", allow.sound)
                        put("voice", allow.voice)
                        put("image", allow.image)
                        put("message", allow.message)
                        put("question", allow.question)
                    }
                }
            },
        )
        _state.update { it.copy(me = user) }
    }

    /** The explicit Pause button. Tells the server first, so the partner sees it immediately. */
    suspend fun setPaused(paused: Boolean) {
        val user = api.setPaused(paused)
        prefs.setPaused(user.paused)
        _state.update { it.copy(me = user) }
    }

    // ── Pairing ─────────────────────────────────────────────────────────────
    suspend fun invite(phone: String) {
        val invites = api.invite(phone)
        _state.update { it.copy(invites = invites) }
    }

    suspend fun acceptInvite(id: String) = apply(api.acceptInvite(id))

    suspend fun declineInvite(id: String) {
        val invites = api.declineInvite(id)
        _state.update { it.copy(invites = invites) }
    }

    suspend fun cancelInvite(id: String) {
        val invites = api.cancelInvite(id)
        _state.update { it.copy(invites = invites) }
    }

    suspend fun unpair() = apply(api.unpair())

    // ── End-to-end keys ─────────────────────────────────────────────────────
    /** The couple's shared AES key, or null until both phones have published keys. */
    fun sharedKey(): ByteArray? {
        val partnerKey = state.value.partner?.publicKey ?: return null
        cachedKey?.let { (forKey, key) -> if (forKey == partnerKey) return key }
        val privateKey = secure.privateKey ?: return null
        val publicKey = secure.publicKey ?: return null
        return runCatching { E2ee.deriveSharedKey(privateKey, publicKey, partnerKey) }.getOrNull()?.also {
            cachedKey = partnerKey to it
        }
    }

    fun safetyCode(): String? {
        val mine = secure.publicKey ?: return null
        val theirs = state.value.partner?.publicKey ?: return null
        return E2ee.safetyCode(mine, theirs)
    }

    // ── Sign out ────────────────────────────────────────────────────────────
    suspend fun logout(callServer: Boolean) {
        if (callServer) runCatching { api.logout() }
        clearLocal()
    }

    fun clearLocal() {
        secure.clear()
        prefs.clearAccount()
        cachedKey = null
        _state.value = CoupleState()
        _loggedIn.value = false
    }
}
