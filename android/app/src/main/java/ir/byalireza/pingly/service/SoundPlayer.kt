package ir.byalireza.pingly.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import ir.byalireza.pingly.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Plays Pingly sounds and voice notes. Sounds from the partner honour the user's volume and
 * "ring through silent" choice (alarm stream); previews inside the app use the media stream.
 * Other audio (music, podcasts) is ducked briefly instead of being interrupted.
 */
class SoundPlayer(private val context: Context, private val prefs: AppPrefs) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    /** Id of whatever is playing (sound id or media id), for play/stop toggles in the UI. */
    private val _nowPlaying = MutableStateFlow<String?>(null)
    val nowPlaying: StateFlow<String?> = _nowPlaying.asStateFlow()

    fun playRaw(@RawRes res: Int, tag: String, fromPartner: Boolean, onDone: () -> Unit = {}) {
        play(tag, fromPartner, speech = false, onDone) { mp ->
            context.resources.openRawResourceFd(res).use { afd -> mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
        }
    }

    fun playFile(file: File, tag: String, fromPartner: Boolean, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        play(tag, fromPartner, speech = true, onDone, onStart) { mp -> mp.setDataSource(file.absolutePath) }
    }

    @Synchronized
    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        focus?.let { audioManager?.abandonAudioFocusRequest(it) }
        focus = null
        _nowPlaying.value = null
    }

    @Synchronized
    private fun play(
        tag: String,
        fromPartner: Boolean,
        speech: Boolean,
        onDone: () -> Unit,
        onStart: () -> Unit = {},
        source: (MediaPlayer) -> Unit,
    ) {
        stop()
        val settings = prefs.reactions.value
        val usage = when {
            !fromPartner -> AudioAttributes.USAGE_MEDIA
            settings.ringThroughSilent -> AudioAttributes.USAGE_ALARM
            else -> AudioAttributes.USAGE_NOTIFICATION_EVENT
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(if (speech) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val volume = if (fromPartner) settings.volume else 1f
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            source(mp)
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener {
                requestFocus(attributes)
                it.start()
                onStart()
            }
            mp.setOnCompletionListener {
                synchronized(this) { if (player === it) stop() }
                onDone()
            }
            mp.setOnErrorListener { it, what, extra ->
                Log.w(TAG, "playback error $what/$extra")
                synchronized(this) { if (player === it) stop() }
                onDone()
                true
            }
            player = mp
            _nowPlaying.value = tag
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "could not play $tag", e)
            mp.release()
            player = null
            _nowPlaying.value = null
            onDone()
        }
    }

    private fun requestFocus(attributes: AudioAttributes) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        audioManager?.requestAudioFocus(request)
        focus = request
    }

    private companion object {
        const val TAG = "PinglySound"
    }
}
