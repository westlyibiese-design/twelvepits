package com.westly.twelvepits.core.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/** Every kind of vibration the game uses. Each has its own pattern (see [HapticPatterns]). */
enum class HapticEffect { PIT_TAP, CAPTURE, TURN_SKIPPED, INVALID_MOVE, VICTORY, DEFEAT }

/** The patterns, in milliseconds: off, on, off, on and so on. The first number is the wait before the first pulse. */
object HapticPatterns {
    fun timings(effect: HapticEffect): LongArray = when (effect) {
        HapticEffect.PIT_TAP -> longArrayOf(0, 10)
        HapticEffect.CAPTURE -> longArrayOf(0, 35)
        HapticEffect.TURN_SKIPPED -> longArrayOf(0, 20, 60, 20)
        HapticEffect.INVALID_MOVE -> longArrayOf(0, 15)
        HapticEffect.VICTORY -> longArrayOf(0, 20, 40, 35, 40, 60)
        HapticEffect.DEFEAT -> longArrayOf(0, 80)
    }

    /** Strength of each step (0 to 255) for phones that can vary it. Null means the normal strength. */
    fun amplitudes(effect: HapticEffect): IntArray? = when (effect) {
        HapticEffect.VICTORY -> intArrayOf(0, 90, 0, 170, 0, 255)
        else -> null
    }
}

/**
 * Vibrates the phone. It needs only the normal VIBRATE permission (no question is ever asked) and does
 * nothing on a phone without a vibrator. A failing call is logged and ignored. Main thread only.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = findVibrator(context.applicationContext)

    fun play(effect: HapticEffect) {
        val device = vibrator ?: return
        try {
            val timings = HapticPatterns.timings(effect)
            val amplitudes = HapticPatterns.amplitudes(effect)
            val wave = if (amplitudes != null && device.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, NO_REPEAT)
            } else {
                VibrationEffect.createWaveform(timings, NO_REPEAT)
            }
            device.vibrate(wave)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Vibration failed: ${e.javaClass.simpleName}")
        }
    }

    private fun findVibrator(context: Context): Vibrator? {
        val found: Vibrator? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: RuntimeException) {
            null
        }
        return found?.takeIf { it.hasVibrator() }
    }

    private companion object {
        const val TAG = "TwelvePits"
        const val NO_REPEAT = -1
    }
}
