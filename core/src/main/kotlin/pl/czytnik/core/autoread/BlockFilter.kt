package pl.czytnik.core.autoread

import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.model.TextBlock

/**
 * Bloki klatki podzielone na pełne i ucięte krawędzią kadru (docs/TECH-SPEC.md, rozdz. 3.1).
 */
data class FilteredFrame(val full: List<TextBlock>, val cut: List<TextBlock>) {
    val hasText: Boolean get() = full.isNotEmpty() || cut.isNotEmpty()
    val allCut: Boolean get() = full.isEmpty() && cut.isNotEmpty()
    val all: List<TextBlock> get() = full + cut

    companion object {
        val EMPTY = FilteredFrame(emptyList(), emptyList())
    }
}

class BlockFilter(private val config: AutoReadConfig) {

    /**
     * Odrzuca linie o niskiej pewności i bloki z mniej niż [minLetters] literami, a pozostałe dzieli na pełne i ucięte.
     */
    fun filter(frame: OcrFrame, minLetters: Int = config.minLetters): FilteredFrame {
        val blocks = frame.blocks.mapNotNull(::dropUncertainLines).filter { it.letterCount >= minLetters }
        val (cut, full) = blocks.partition { it.box.touchesFrameEdge(config.edgeMargin) }
        return FilteredFrame(full = full, cut = cut)
    }

    private fun dropUncertainLines(block: TextBlock): TextBlock? {
        val lines = block.lines.filter { line ->
            line.text.isNotBlank() && (line.confidence ?: 1.0) >= config.lineMinConfidence
        }
        return when {
            lines.isEmpty() -> null
            lines.size == block.lines.size -> block
            else -> TextBlock.of(lines)
        }
    }
}
