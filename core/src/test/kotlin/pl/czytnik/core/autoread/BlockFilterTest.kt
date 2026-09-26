package pl.czytnik.core.autoread

import pl.czytnik.core.block
import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.OcrFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockFilterTest {

    private val filter = BlockFilter(AutoReadConfig())

    @Test
    fun `block touching the frame edge is cut`() {
        val result = filter.filter(
            OcrFrame(listOf(block("FULL BUBBLE"), block("HALF A BUBB", left = 0.0, right = 0.3))),
        )
        assertEquals(listOf("FULL BUBBLE"), result.full.map { it.rawText })
        assertEquals(listOf("HALF A BUBB"), result.cut.map { it.rawText })
        assertTrue(!result.allCut)
    }

    @Test
    fun `only cut blocks`() {
        val result = filter.filter(OcrFrame(listOf(block("EDGE TEXT", right = 1.0))))
        assertTrue(result.allCut)
    }

    @Test
    fun `too short blocks and uncertain lines are dropped`() {
        val result = filter.filter(
            OcrFrame(
                listOf(
                    block("NO!"),
                    block("#~ %%", top = 0.6, bottom = 0.7),
                    block("GOOD LINE\nnoise", top = 0.1, bottom = 0.2).let { b ->
                        b.copy(lines = listOf(b.lines[0].copy(confidence = 0.9), b.lines[1].copy(confidence = 0.2)))
                    },
                ),
            ),
        )
        assertEquals(listOf("GOOD LINE"), result.all.map { it.rawText })
    }

    @Test
    fun `forced selection accepts short text`() {
        val result = filter.filter(OcrFrame(listOf(block("NO!"))), minLetters = 1)
        assertEquals(listOf("NO!"), result.full.map { it.rawText })
    }
}
