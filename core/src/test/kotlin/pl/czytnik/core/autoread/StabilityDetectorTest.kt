package pl.czytnik.core.autoread

import pl.czytnik.core.block
import pl.czytnik.core.config.AutoReadConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class StabilityDetectorTest {

    private val detector = StabilityDetector(AutoReadConfig())
    private val bubble = listOf(block("WHO ARE YOU REALLY?"))

    private fun run(frames: List<Pair<Long, List<pl.czytnik.core.model.TextBlock>>>) =
        frames.map { (t, blocks) -> detector.update(blocks, t) }

    @Test
    fun `same text for half a second is stable`() {
        val results = run((0..2).map { it * 250L to bubble })
        assertEquals(listOf(false, false, true), results)
    }

    @Test
    fun `text change restarts the window`() {
        val other = listOf(block("SOMETHING COMPLETELY DIFFERENT"))
        val results = run(listOf(0L to bubble, 250L to bubble, 500L to other, 750L to other, 1000L to other))
        assertEquals(listOf(false, false, false, false, true), results)
    }

    @Test
    fun `moving text is not stable`() {
        val results = run(
            (0..6).map { i ->
                val shift = i * 0.06
                i * 350L to listOf(block("WHO ARE YOU REALLY?", left = 0.1 + shift, right = 0.3 + shift))
            },
        )
        assertEquals(List(7) { false }, results)
    }

    @Test
    fun `small OCR noise does not break stability`() {
        val noisy = listOf(block("WH0 ARE YOU REALLY?"))
        val results = run(listOf(0L to bubble, 350L to noisy, 700L to bubble, 1050L to noisy))
        assertEquals(true, results.last())
    }

    @Test
    fun `no text resets`() {
        val results = run(listOf(0L to bubble, 350L to bubble, 700L to emptyList(), 1050L to bubble, 1400L to bubble))
        assertEquals(List(5) { false }, results)
    }
}
