package pl.czytnik.app.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import pl.czytnik.core.model.HapticKind

/** Wibracje (docs/TECH-SPEC.md, rozdz. 4.7). */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun vibrate(kind: HapticKind) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (kind) {
            HapticKind.TICK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            HapticKind.DOUBLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 30, 80, 30), -1)
            }
            HapticKind.ERROR -> VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        v.vibrate(effect)
    }
}

/** Krótki sygnał dźwiękowy przed odczytem (bez plików w APK). */
class StartSignal {

    private var tone: ToneGenerator? = null

    fun play() {
        try {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME).also { tone = it }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, DURATION_MS)
        } catch (e: RuntimeException) {
            Log.w("StartSignal", "Tone unavailable", e)
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }

    private companion object {
        const val VOLUME = 70
        const val DURATION_MS = 120
    }
}
