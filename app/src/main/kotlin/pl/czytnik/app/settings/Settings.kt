package pl.czytnik.app.settings

import android.content.Context
import androidx.core.content.edit
import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.state.MainState

/** Ustawienia zapamiętywane między uruchomieniami. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun initialState(): MainState {
        val defaults = MainState()
        return defaults.copy(
            translate = prefs.getBoolean(TRANSLATE, defaults.translate),
            speechRate = prefs.getFloat(SPEECH_RATE, defaults.speechRate.toFloat()).toDouble(),
            autoRead = prefs.getBoolean(AUTO_READ, defaults.autoRead),
            autoTorch = prefs.getBoolean(AUTO_TORCH, defaults.autoTorch),
            firstLaunch = prefs.getBoolean(FIRST_LAUNCH, defaults.firstLaunch),
        )
    }

    fun save(state: MainState) = prefs.edit {
        putBoolean(TRANSLATE, state.translate)
        putFloat(SPEECH_RATE, state.speechRate.toFloat())
        putBoolean(AUTO_READ, state.autoRead)
        putBoolean(AUTO_TORCH, state.autoTorch)
        putBoolean(FIRST_LAUNCH, state.firstLaunch)
    }

    /** Parametry auto-odczytu zmieniane na ekranie Diagnostyka (strojenie bez nowego APK). */
    fun config(): AutoReadConfig {
        val d = AutoReadConfig()
        return try {
            d.copy(
                stableWindowMs = prefs.getLong(STABLE_WINDOW_MS, d.stableWindowMs),
                stableSimilarity = prefs.getFloat(STABLE_SIMILARITY, d.stableSimilarity.toFloat()).toDouble(),
                edgeMargin = prefs.getFloat(EDGE_MARGIN, d.edgeMargin.toFloat()).toDouble(),
                leaveMs = prefs.getLong(LEAVE_MS, d.leaveMs),
                minLetters = prefs.getInt(MIN_LETTERS, d.minLetters),
                useHighResCapture = prefs.getBoolean(HIGH_RES, d.useHighResCapture),
                normalizeAllCaps = prefs.getBoolean(NORMALIZE_CAPS, d.normalizeAllCaps),
            )
        } catch (_: IllegalArgumentException) {
            d
        }
    }

    fun saveConfig(config: AutoReadConfig) = prefs.edit {
        putLong(STABLE_WINDOW_MS, config.stableWindowMs)
        putFloat(STABLE_SIMILARITY, config.stableSimilarity.toFloat())
        putFloat(EDGE_MARGIN, config.edgeMargin.toFloat())
        putLong(LEAVE_MS, config.leaveMs)
        putInt(MIN_LETTERS, config.minLetters)
        putBoolean(HIGH_RES, config.useHighResCapture)
        putBoolean(NORMALIZE_CAPS, config.normalizeAllCaps)
    }

    private companion object {
        const val TRANSLATE = "translate"
        const val SPEECH_RATE = "speech_rate"
        const val AUTO_READ = "auto_read"
        const val AUTO_TORCH = "auto_torch"
        const val FIRST_LAUNCH = "first_launch"
        const val STABLE_WINDOW_MS = "cfg_stable_window_ms"
        const val STABLE_SIMILARITY = "cfg_stable_similarity"
        const val EDGE_MARGIN = "cfg_edge_margin"
        const val LEAVE_MS = "cfg_leave_ms"
        const val MIN_LETTERS = "cfg_min_letters"
        const val HIGH_RES = "cfg_high_res"
        const val NORMALIZE_CAPS = "cfg_normalize_caps"
    }
}
