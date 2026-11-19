package ir.byalireza.pingly.data

import android.content.Context
import ir.byalireza.pingly.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How this phone reacts when the partner reaches it. Local to the device. */
data class ReactionSettings(
    /** Open photos, love notes and questions full-screen (otherwise just a notification). */
    val fullscreen: Boolean = true,
    /** Play sounds on the alarm stream, so they are heard even in silent mode. */
    val ringThroughSilent: Boolean = true,
    /** 0.1 – 1.0 */
    val volume: Float = 0.9f,
)

/**
 * Non-secret app state, kept in SharedPreferences so broadcast receivers and the service can
 * read it synchronously (e.g. on boot, before anything else has started).
 */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("pingly_prefs", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(value) = prefs.edit().putBoolean("setup_done", value).apply()

    /** The user opened their phone's OEM autostart screen (we can't read that setting back). */
    var autostartConfirmed: Boolean
        get() = prefs.getBoolean("autostart_confirmed", false)
        set(value) = prefs.edit().putBoolean("autostart_confirmed", value).apply()

    var serverUrl: String
        get() = prefs.getString("server_url", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString("server_url", value.trim().trimEnd('/')).apply()

    var userId: String?
        get() = prefs.getString("user_id", null)
        set(value) = prefs.edit().putString("user_id", value).apply()

    /** Last `/v1/me` response, so notifications can name the partner even while offline. */
    var cachedMe: String?
        get() = prefs.getString("cached_me", null)
        set(value) = prefs.edit().putString("cached_me", value).apply()

    private val _paused = MutableStateFlow(prefs.getBoolean("paused", false))

    /** The device owner's explicit Pause switch. Nothing else ever turns Pingly off. */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun setPaused(value: Boolean) {
        prefs.edit().putBoolean("paused", value).commit()
        _paused.value = value
    }

    private val _reactions = MutableStateFlow(
        ReactionSettings(
            fullscreen = prefs.getBoolean("fullscreen", true),
            ringThroughSilent = prefs.getBoolean("ring_through_silent", true),
            volume = prefs.getFloat("volume", 0.9f),
        ),
    )
    val reactions: StateFlow<ReactionSettings> = _reactions.asStateFlow()

    fun updateReactions(transform: (ReactionSettings) -> ReactionSettings) {
        val next = transform(_reactions.value).let { it.copy(volume = it.volume.coerceIn(0.1f, 1f)) }
        prefs.edit()
            .putBoolean("fullscreen", next.fullscreen)
            .putBoolean("ring_through_silent", next.ringThroughSilent)
            .putFloat("volume", next.volume)
            .apply()
        _reactions.value = next
    }

    /** Wipes account data on logout; keeps device-level choices like onboarding and server URL. */
    fun clearAccount() {
        prefs.edit()
            .remove("user_id")
            .remove("cached_me")
            .remove("setup_done")
            .putBoolean("paused", false)
            .commit()
        _paused.value = false
    }
}
