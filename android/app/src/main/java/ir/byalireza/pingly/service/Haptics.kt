package ir.byalireza.pingly.service

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import ir.byalireza.pingly.data.AppPrefs
import ir.byalireza.pingly.domain.VibrationPattern

/** Plays Pingly's vibration patterns, with or without amplitude control. */
class Haptics(context: Context, private val prefs: AppPrefs) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    val available: Boolean get() = vibrator?.hasVibrator() == true

    /**
     * `fromPartner` vibrations use the alarm usage when "ring through silent" is on, so they are
     * felt even with the phone in silent mode; previews in the UI use touch feedback usage.
     */
    fun play(pattern: VibrationPattern, fromPartner: Boolean = true) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(onOffTimings(pattern), -1)
        }
        val throughSilent = fromPartner && prefs.reactions.value.ringThroughSilent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val usage = when {
                throughSilent -> VibrationAttributes.USAGE_ALARM
                fromPartner -> VibrationAttributes.USAGE_NOTIFICATION
                else -> VibrationAttributes.USAGE_TOUCH
            }
            v.vibrate(effect, VibrationAttributes.createForUsage(usage))
        } else {
            val usage = if (throughSilent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
    }

    fun tap() {
        val v = vibrator ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun cancel() = vibrator?.cancel()

    companion object {
        /** Folds an amplitude waveform into plain off/on timings for vibrators without amplitude control. */
        fun onOffTimings(pattern: VibrationPattern): LongArray {
            val out = mutableListOf(0L)
            var on = false
            pattern.timings.forEachIndexed { i, duration ->
                val segmentOn = pattern.amplitudes[i] > 60
                if (segmentOn == on) {
                    out[out.lastIndex] = out.last() + duration
                } else {
                    out += duration
                    on = segmentOn
                }
            }
            return out.toLongArray()
        }
    }
}
