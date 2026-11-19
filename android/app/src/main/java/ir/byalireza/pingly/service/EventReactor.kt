package ir.byalireza.pingly.service

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import ir.byalireza.pingly.data.repo.AccountRepository
import ir.byalireza.pingly.data.repo.EventRepository
import ir.byalireza.pingly.data.repo.MediaRepository
import ir.byalireza.pingly.data.AppPrefs
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.domain.PinglySound
import ir.byalireza.pingly.domain.VibrationPattern
import ir.byalireza.pingly.ui.fullscreen.FullscreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * What this phone does when the partner reaches it: vibrate, play a sound or their voice, open a
 * photo / love note / question full-screen, and always leave a notification behind.
 */
class EventReactor(
    private val context: Context,
    private val prefs: AppPrefs,
    private val account: AccountRepository,
    private val events: EventRepository,
    private val media: MediaRepository,
    private val haptics: Haptics,
    private val sound: SoundPlayer,
    private val scope: CoroutineScope,
    private val markOpened: (String) -> Unit,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun react(event: PinglyEvent) {
        if (event.mine) return
        // Queued while the phone was unreachable: still notify, but don't buzz about old news.
        val fresh = System.currentTimeMillis() - event.createdAt < FRESH_MS
        val partner = account.state.value.partner
        val partnerName = account.state.value.partnerName
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pingly:react")?.apply { acquire(30_000) }

        try {
            when (event.type) {
                EventType.Ping -> if (fresh) {
                    haptics.play(VibrationPattern.Heartbeat)
                    sound.playRaw(PinglySound.Ping.res, "event:${event.id}", fromPartner = true)
                }
                EventType.Vibrate -> if (fresh) haptics.play(VibrationPattern.fromId(event.metaString("pattern")))
                EventType.Sound -> if (fresh) {
                    sound.playRaw(PinglySound.fromId(event.metaString("sound")).res, "event:${event.id}", fromPartner = true)
                    markOpened(event.id)
                }
                EventType.Voice -> if (fresh) playVoice(event)
                EventType.Image -> {
                    // Fetch and decrypt ahead of time so the photo opens instantly.
                    event.mediaId?.let { id -> scope.launch { runCatching { media.file(id) } } }
                    haptics.play(VibrationPattern.Butterfly)
                }
                EventType.Message, EventType.Question -> if (fresh) haptics.play(VibrationPattern.Butterfly)
                EventType.Answer -> haptics.play(VibrationPattern.Knock)
            }
        } catch (e: Exception) {
            Log.w(TAG, "reaction failed for ${event.type}", e)
        }

        val wantsFullscreen = event.type in setOf(EventType.Image, EventType.Message, EventType.Question)
        val fullscreen = wantsFullscreen && prefs.reactions.value.fullscreen && fresh
        Notifications.partnerEvent(context, event, partnerName, partner?.avatar, fullScreen = fullscreen)
        if (fullscreen) launchFullscreen(event)

        if (wakeLock?.isHeld == true && event.type != EventType.Voice) wakeLock.release()
    }

    private fun playVoice(event: PinglyEvent) {
        val id = event.mediaId ?: return
        scope.launch {
            try {
                val file = media.file(id)
                withContext(Dispatchers.Main) {
                    sound.playFile(file, "event:${event.id}", fromPartner = true, onStart = { markOpened(event.id) })
                }
            } catch (e: Exception) {
                Log.w(TAG, "voice playback failed", e)
                sound.playRaw(PinglySound.Ping.res, "event:${event.id}", fromPartner = true)
            }
        }
    }

    /**
     * Opens the full-screen view right away when Android allows it: while Pingly is on screen,
     * or when the user granted "display over other apps". Otherwise the full-screen intent on the
     * notification takes over (it opens immediately on a locked or sleeping phone).
     */
    private fun launchFullscreen(event: PinglyEvent) {
        val inForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!inForeground && !Settings.canDrawOverlays(context)) return
        runCatching {
            context.startActivity(FullscreenActivity.intent(context, event.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "direct full-screen launch refused", it) }
    }

    private companion object {
        const val TAG = "PinglyReactor"
        const val FRESH_MS = 10 * 60_000L
    }
}
