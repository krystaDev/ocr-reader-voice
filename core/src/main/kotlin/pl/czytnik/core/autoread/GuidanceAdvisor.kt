package pl.czytnik.core.autoread

import pl.czytnik.core.config.AutoReadConfig

/** Podpowiedź dla użytkownika nakierowującego aparat (docs/TECH-SPEC.md, rozdz. 3.4). */
enum class Guidance { TEXT_APPEARED, NO_TEXT, HOLD_STILL, MOVE_AWAY, LOW_LIGHT }

/**
 * Decyduje, kiedy zawibrować i kiedy podpowiedzieć głosem – rzadko i bez powtarzania tego samego w kółko.
 */
class GuidanceAdvisor(private val config: AutoReadConfig) {

    private var hadText = false
    private var lastHapticMs: Long? = null
    private var noTextSinceMs: Long? = null
    private var lastNoTextHintMs: Long? = null
    private var noTextHints = 0
    private var allCutSinceMs: Long? = null
    private var moveAwayGiven = false
    private var waitingSinceMs: Long? = null
    private var holdStillGiven = false
    private var darkSinceMs: Long? = null
    private var lowLightGiven = false

    /**
     * @param waitingForStability w kadrze jest nieprzeczytany tekst, ale nie jest jeszcze stabilny
     */
    fun update(
        frame: FilteredFrame,
        waitingForStability: Boolean,
        meanLuma: Int?,
        nowMs: Long,
    ): List<Guidance> {
        val result = mutableListOf<Guidance>()

        if (frame.hasText) {
            if (!hadText && lastHapticMs.let { it == null || nowMs - it >= config.hapticMinIntervalMs }) {
                result += Guidance.TEXT_APPEARED
                lastHapticMs = nowMs
            }
            noTextSinceMs = null
            noTextHints = 0
            lastNoTextHintMs = null
        } else {
            val since = noTextSinceMs ?: nowMs.also { noTextSinceMs = it }
            val repeatDue = lastNoTextHintMs.let { it == null || nowMs - it >= config.noTextRepeatMs }
            if (nowMs - since >= config.noTextHintMs && noTextHints < config.noTextMaxHints && repeatDue) {
                result += Guidance.NO_TEXT
                noTextHints++
                lastNoTextHintMs = nowMs
            }
        }
        hadText = frame.hasText

        if (frame.allCut) {
            val since = allCutSinceMs ?: nowMs.also { allCutSinceMs = it }
            if (!moveAwayGiven && nowMs - since >= config.edgeHintMs) {
                result += Guidance.MOVE_AWAY
                moveAwayGiven = true
            }
        } else {
            allCutSinceMs = null
            moveAwayGiven = false
        }

        if (waitingForStability) {
            val since = waitingSinceMs ?: nowMs.also { waitingSinceMs = it }
            if (!holdStillGiven && nowMs - since >= config.unstableHintMs) {
                result += Guidance.HOLD_STILL
                holdStillGiven = true
            }
        } else {
            waitingSinceMs = null
            holdStillGiven = false
        }

        if (meanLuma != null && meanLuma < config.lowLightLuma) {
            val since = darkSinceMs ?: nowMs.also { darkSinceMs = it }
            if (!lowLightGiven && nowMs - since >= config.lowLightMs) {
                result += Guidance.LOW_LIGHT
                lowLightGiven = true
            }
        } else if (meanLuma != null) {
            darkSinceMs = null
            lowLightGiven = false
        }

        // Jedna podpowiedź głosowa naraz; wibracja i latarka mogą jej towarzyszyć.
        val spoken = result.filter { it in SPOKEN }
        return if (spoken.size <= 1) result else result - spoken.drop(1).toSet()
    }

    /** Po przerwie w analizie liczniki czasu startują od nowa. */
    fun resume() {
        noTextSinceMs = null
        allCutSinceMs = null
        waitingSinceMs = null
        darkSinceMs = null
    }

    private companion object {
        val SPOKEN = setOf(Guidance.NO_TEXT, Guidance.HOLD_STILL, Guidance.MOVE_AWAY)
    }
}
