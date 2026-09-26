package pl.czytnik.core.autoread

import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.Box
import pl.czytnik.core.model.TextBlock
import pl.czytnik.core.text.TextFingerprint

/**
 * Stwierdza, że tekst w kadrze jest stabilny (docs/TECH-SPEC.md, rozdz. 3.2): przez [AutoReadConfig.stableWindowMs]
 * i co najmniej [AutoReadConfig.stableMinFrames] klatek tekst jest podobny, a jego położenie prawie się nie zmienia.
 */
class StabilityDetector(private val config: AutoReadConfig) {

    private var windowStartMs: Long? = null
    private var frames = 0
    private var previousText: TextFingerprint? = null
    private var anchorBox: Box? = null

    /** Momentu rozpoczęcia bieżącej serii podobnych klatek; `null`, gdy brak tekstu. */
    val stableSinceMs: Long? get() = windowStartMs

    fun update(fullBlocks: List<TextBlock>, nowMs: Long): Boolean {
        val box = Box.unionOf(fullBlocks.map { it.box })
        if (box == null) {
            reset()
            return false
        }
        val text = TextFingerprint.of(fullBlocks.joinToString(" ") { it.rawText })
        val prevText = previousText
        val anchor = anchorBox
        // Tekst porównujemy z poprzednią klatką (szum OCR), a położenie z początkiem okna – powolne przesuwanie
        // telefonu też przerywa stabilność.
        val continues = prevText != null && anchor != null &&
            text.dice(prevText) >= config.stableSimilarity &&
            box.centerDistance(anchor) <= config.maxCenterShift
        if (continues) {
            frames++
        } else {
            windowStartMs = nowMs
            frames = 1
            anchorBox = box
        }
        previousText = text
        val start = windowStartMs ?: nowMs
        return frames >= config.stableMinFrames && nowMs - start >= config.stableWindowMs
    }

    fun reset() {
        windowStartMs = null
        frames = 0
        previousText = null
        anchorBox = null
    }
}
