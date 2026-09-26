package pl.czytnik.core.autoread

import pl.czytnik.core.block
import pl.czytnik.core.config.AutoReadConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadMemoryTest {

    private val memory = ReadMemory(AutoReadConfig())
    private val bubbleA = block("I THOUGHT YOU WERE DEAD, LOGAN!")
    private val bubbleB = block("TAKES MORE THAN THAT TO KILL ME, BUB.")

    @Test
    fun `read block stays read while visible`() {
        memory.remember(listOf(bubbleA), 0)
        for (t in 350L..10_000L step 350) {
            memory.observe(listOf(bubbleA), t)
            assertTrue(memory.isRead(bubbleA))
        }
    }

    @Test
    fun `new block is not read`() {
        memory.remember(listOf(bubbleA), 0)
        memory.observe(listOf(bubbleA, bubbleB), 350)
        assertFalse(memory.isRead(bubbleB))
    }

    @Test
    fun `block is re-armed after leaving the frame`() {
        memory.remember(listOf(bubbleA), 0)
        memory.observe(listOf(bubbleB), 1000)
        assertTrue(memory.isRead(bubbleA), "still within leaveMs")
        memory.observe(listOf(bubbleB), 2100)
        assertFalse(memory.isRead(bubbleA))
    }

    @Test
    fun `partially visible bubble keeps the block (hysteresis)`() {
        memory.remember(listOf(bubbleA), 0)
        val cutA = block("I THOUGHT YOU WE", right = 1.0)
        for (t in 350L..5000L step 350) memory.observe(listOf(bubbleB), t, cut = listOf(cutA))
        assertTrue(memory.isRead(bubbleA))
    }

    @Test
    fun `bubble split by OCR into two blocks is still read`() {
        memory.remember(listOf(bubbleA), 0)
        val part1 = block("I THOUGHT YOU")
        val part2 = block("WERE DEAD, LOGAN!")
        memory.observe(listOf(part1, part2), 350)
        assertTrue(memory.isRead(part1))
        assertTrue(memory.isRead(part2))
    }

    @Test
    fun `two read bubbles merged by OCR are still read`() {
        memory.remember(listOf(bubbleA, bubbleB), 0)
        val merged = block(bubbleA.rawText + "\n" + bubbleB.rawText)
        memory.observe(listOf(merged), 350)
        assertTrue(memory.isRead(merged))
    }

    @Test
    fun `short new bubble with words from an old one is not read`() {
        memory.remember(listOf(block("WAIT FOR ME HERE, I WILL BE BACK")), 0)
        val shortNew = block("WAIT!")
        memory.observe(listOf(shortNew), 350) // stary dymek zniknął z kadru
        assertFalse(memory.isRead(shortNew))
    }

    @Test
    fun `resume after speaking prevents expiry of text still in front of the camera`() {
        memory.remember(listOf(bubbleA), 0)
        // Czytanie trwało 8 s, analiza była wstrzymana.
        memory.resume(8000)
        memory.observe(listOf(bubbleA), 8350)
        assertTrue(memory.isRead(bubbleA))
    }

    @Test
    fun `memory is bounded`() {
        val config = AutoReadConfig(memoryMaxEntries = 3)
        val small = ReadMemory(config)
        small.remember(listOf("ONE RING TO RULE", "HULK SMASH PUNY GOD", "AVENGERS ASSEMBLE", "WITH GREAT POWER", "I AM GROOT").map { block(it) }, 0)
        assertEquals(3, small.size)
    }
}
