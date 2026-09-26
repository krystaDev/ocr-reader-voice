package pl.czytnik.core.text

import pl.czytnik.core.block
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingOrderTest {

    private fun texts(vararg blocks: pl.czytnik.core.model.TextBlock) = ReadingOrder.order(blocks.toList().shuffled(kotlin.random.Random(1))).map { it.rawText }

    @Test
    fun `single block`() {
        assertEquals(listOf("A"), texts(block("A")))
    }

    @Test
    fun `blocks above each other are read top to bottom`() {
        assertEquals(
            listOf("TOP", "MIDDLE", "BOTTOM"),
            texts(block("BOTTOM", top = 0.7, bottom = 0.8), block("TOP", top = 0.1, bottom = 0.2), block("MIDDLE", top = 0.4, bottom = 0.5)),
        )
    }

    @Test
    fun `two columns are read column by column`() {
        // Kolumny o różnej wysokości akapitów – rzuty na oś Y nachodzą na siebie, więc dzielimy po X.
        val result = texts(
            block("L1", left = 0.05, right = 0.45, top = 0.10, bottom = 0.40),
            block("L2", left = 0.05, right = 0.45, top = 0.42, bottom = 0.90),
            block("R1", left = 0.55, right = 0.95, top = 0.10, bottom = 0.60),
            block("R2", left = 0.55, right = 0.95, top = 0.62, bottom = 0.90),
        )
        assertEquals(listOf("L1", "L2", "R1", "R2"), result)
    }

    @Test
    fun `comic grid 2x2 is read row by row, left to right`() {
        val result = texts(
            block("P1", left = 0.05, right = 0.45, top = 0.05, bottom = 0.40),
            block("P2", left = 0.55, right = 0.95, top = 0.10, bottom = 0.35),
            block("P3", left = 0.05, right = 0.45, top = 0.55, bottom = 0.90),
            block("P4", left = 0.55, right = 0.95, top = 0.50, bottom = 0.95),
        )
        assertEquals(listOf("P1", "P2", "P3", "P4"), result)
    }

    @Test
    fun `paragraphs join lines in reading order`() {
        val paragraphs = Paragraphs.from(
            listOf(
                block("SECOND\nBUBBLE", top = 0.6, bottom = 0.8),
                block("FIRST SPIDER-\nMAN BUBBLE", top = 0.1, bottom = 0.3),
            ),
        )
        assertEquals(listOf("FIRST SPIDER-MAN BUBBLE", "SECOND BUBBLE"), paragraphs)
    }
}
