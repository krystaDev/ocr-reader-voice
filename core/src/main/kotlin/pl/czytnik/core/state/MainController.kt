package pl.czytnik.core.state

import pl.czytnik.core.autoread.AutoReadEngine
import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.model.TextBlock

/**
 * Łączy maszynę stanów z auto-odczytem. Platforma (ViewModel) przekazuje tu zdarzenia i klatki, a wykonuje
 * zwrócone efekty. Nie jest wątkobezpieczny – wywoływać z jednego wątku.
 */
class MainController(
    private val config: AutoReadConfig = AutoReadConfig(),
    initialState: MainState = MainState(),
) {
    private val reducer = MainReducer(config)
    private val engine = AutoReadEngine(config)
    private var stoppedAtMs: Long? = null

    var state: MainState = initialState
        private set

    fun dispatch(event: MainEvent, nowMs: Long): List<MainEffect> {
        when (event) {
            MainEvent.AppStopped -> stoppedAtMs = nowMs
            MainEvent.StartupComplete -> {
                val stoppedAt = stoppedAtMs
                if (stoppedAt != null && nowMs - stoppedAt >= config.backgroundForgetMs) engine.forgetReadTexts()
                stoppedAtMs = null
            }
            else -> Unit
        }
        val transition = reducer.reduce(state, event)
        state = transition.state
        return transition.effects.filter { effect -> !handleInternally(effect, nowMs) }
    }

    /** Klatka analizy; ignorowana poza stanem skanowania (analiza wstrzymana). */
    fun onFrame(frame: OcrFrame, nowMs: Long): List<MainEffect> {
        if (state.screen != Screen.Scanning) return emptyList()
        val decision = engine.onFrame(frame, nowMs, state.autoRead)
        val effects = mutableListOf<MainEffect>()
        decision.guidance.forEach { effects += dispatch(MainEvent.GuidanceIssued(it), nowMs) }
        decision.readBlocks?.let { effects += dispatch(MainEvent.TextStable(it), nowMs) }
        return effects
    }

    fun onScreenTapped(nowMs: Long): List<MainEffect> =
        dispatch(MainEvent.ScreenTapped(engine.forcedSelection()), nowMs)

    /** Wybór bloków ze zdjęcia wysokiej rozdzielczości – przekazywany do ReadPipeline. */
    fun selectFromPhoto(photo: OcrFrame, forced: Boolean): List<TextBlock> = engine.selectFromPhoto(photo, forced)

    private fun handleInternally(effect: MainEffect, nowMs: Long): Boolean {
        when (effect) {
            is MainEffect.MarkRead -> {
                engine.markRead(effect.blocks, nowMs)
                return true
            }
            is MainEffect.SetAnalysisPaused -> if (!effect.paused) engine.resume(nowMs)
            else -> Unit
        }
        return false
    }
}
