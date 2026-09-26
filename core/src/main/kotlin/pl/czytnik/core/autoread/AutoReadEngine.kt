package pl.czytnik.core.autoread

import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.model.TextBlock

/** Wynik analizy jednej klatki: ewentualny start odczytu i podpowiedzi. */
data class FrameDecision(val readBlocks: List<TextBlock>?, val guidance: List<Guidance>) {
    val shouldRead: Boolean get() = readBlocks != null
}

/**
 * Auto-odczyt (docs/TECH-SPEC.md, rozdz. 3): z klatek analizy wybiera moment i bloki do przeczytania.
 * Stanowy, nie wątkobezpieczny – wywoływany z jednego wątku.
 */
class AutoReadEngine(private val config: AutoReadConfig) {

    private val filter = BlockFilter(config)
    private val stability = StabilityDetector(config)
    private val memory = ReadMemory(config)
    private val guidance = GuidanceAdvisor(config)

    private var lastFrame: OcrFrame? = null

    fun onFrame(frame: OcrFrame, nowMs: Long, autoReadEnabled: Boolean): FrameDecision {
        lastFrame = frame
        val filtered = filter.filter(frame)
        memory.observe(filtered.full, nowMs, cut = filtered.cut)
        val unread = filtered.full.filterNot(memory::isRead)
        val stable = stability.update(filtered.full, nowMs)
        val shouldRead = autoReadEnabled && stable && unread.isNotEmpty()
        val hints = guidance.update(
            frame = filtered,
            waitingForStability = autoReadEnabled && unread.isNotEmpty() && !stable,
            meanLuma = frame.meanLuma,
            nowMs = nowMs,
        )
        if (shouldRead) stability.reset()
        return FrameDecision(readBlocks = unread.takeIf { shouldRead }, guidance = hints)
    }

    /** Bloki do wymuszonego odczytu (dotknięcie): pełne bloki ostatniej klatki, a gdy ich brak – także ucięte. */
    fun forcedSelection(): List<TextBlock> = selectForced(lastFrame ?: return emptyList())

    /**
     * Wybiera bloki ze zdjęcia wysokiej rozdzielczości. Dla auto-odczytu – tylko nieprzeczytane pełne bloki;
     * dla wymuszonego – wszystkie pełne (lub ucięte, gdy pełnych brak).
     */
    fun selectFromPhoto(photo: OcrFrame, forced: Boolean): List<TextBlock> =
        if (forced) selectForced(photo) else filter.filter(photo).full.filterNot(memory::isRead)

    fun markRead(blocks: List<TextBlock>, nowMs: Long) = memory.remember(blocks, nowMs)

    /** Wywoływane po wznowieniu analizy (koniec mowy/przetwarzania). */
    fun resume(nowMs: Long) {
        memory.resume(nowMs)
        stability.reset()
        guidance.resume()
    }

    /** Po dłuższym pobycie aplikacji w tle zapominamy przeczytane teksty. */
    fun forgetReadTexts() = memory.clear()

    private fun selectForced(frame: OcrFrame): List<TextBlock> {
        val filtered = filter.filter(frame, minLetters = 1)
        return filtered.full.ifEmpty { filtered.cut }
    }
}
