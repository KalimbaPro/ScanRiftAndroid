package com.scanrift.android.service.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import timber.log.Timber

/**
 * Haptic and sound feedback service for the scanner.
 */
class FeedbackService(private val context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // Sound IDs will be loaded when available
    private var scanSuccessSound: Int = 0
    private var errorSound: Int = 0

    /**
     * Trigger haptic feedback for a successful scan.
     */
    fun scanSuccess(hapticEnabled: Boolean, soundEnabled: Boolean) {
        if (hapticEnabled) {
            vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (soundEnabled && scanSuccessSound != 0) {
            soundPool.play(scanSuccessSound, 0.5f, 0.5f, 1, 0, 1f)
        }
    }

    /**
     * Trigger haptic feedback for an error.
     */
    fun error(hapticEnabled: Boolean, soundEnabled: Boolean) {
        if (hapticEnabled) {
            vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (soundEnabled && errorSound != 0) {
            soundPool.play(errorSound, 0.3f, 0.3f, 1, 0, 1f)
        }
    }

    /**
     * Light haptic tick (e.g., when motion is detected).
     */
    fun tick(hapticEnabled: Boolean) {
        if (hapticEnabled) {
            vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun vibrate(effect: VibrationEffect) {
        try {
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Timber.w(e, "Vibration failed")
        }
    }

    fun release() {
        soundPool.release()
    }
}
