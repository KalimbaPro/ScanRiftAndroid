package com.scanrift.android.service.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.scanrift.android.core.log.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Haptics and sound for the scanner.
 *
 * Sound uses [ToneGenerator] rather than a SoundPool. The previous version wired up a
 * SoundPool but never loaded any samples — the ids stayed 0 — so the Settings toggle
 * did nothing at all. There are no sound assets in the project and iOS falls back to
 * system sounds anyway, so short tones are the honest equivalent.
 */
@Singleton
class FeedbackService @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private var toneGenerator: ToneGenerator? = null

    private fun tones(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
            }.onFailure { Log.general.w(it, "Could not create the tone generator") }.getOrNull()
        }
        return toneGenerator
    }

    fun scanSuccess(haptics: Boolean, sound: Boolean) {
        if (haptics) vibrate(SUCCESS_MS)
        if (sound) tones()?.startTone(ToneGenerator.TONE_PROP_BEEP, SHORT_TONE_MS)
    }

    fun error(haptics: Boolean, sound: Boolean) {
        if (haptics) vibrate(ERROR_MS)
        if (sound) tones()?.startTone(ToneGenerator.TONE_PROP_NACK, LONG_TONE_MS)
    }

    fun sessionComplete(haptics: Boolean, sound: Boolean) {
        if (haptics) vibrate(COMPLETE_MS)
        if (sound) tones()?.startTone(ToneGenerator.TONE_PROP_ACK, LONG_TONE_MS)
    }

    /** Light tick for steppers and selection changes. */
    fun tick(haptics: Boolean) {
        if (haptics) vibrate(TICK_MS)
    }

    private fun vibrate(durationMs: Long) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        device.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }

    private companion object {
        const val TONE_VOLUME = 70
        const val SHORT_TONE_MS = 120
        const val LONG_TONE_MS = 200
        const val SUCCESS_MS = 50L
        const val ERROR_MS = 100L
        const val COMPLETE_MS = 150L
        const val TICK_MS = 10L
    }
}
