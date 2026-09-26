package pl.czytnik.core.autoread

import pl.czytnik.core.block
import pl.czytnik.core.config.AutoReadConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuidanceAdvisorTest {

    private val advisor = GuidanceAdvisor(AutoReadConfig())
    private val withText = FilteredFrame(listOf(block("SOME TEXT")), emptyList())
    private val cutOnly = FilteredFrame(emptyList(), listOf(block("EDGE", right = 1.0)))

    private fun collect(
        frames: Int,
        frame: FilteredFrame,
        startMs: Long = 0,
        waiting: Boolean = false,
        luma: Int? = 120,
    ): List<Pair<Long, Guidance>> =
        (0 until frames).flatMap { i ->
            val t = startMs + i * 350L
            advisor.update(frame, waiting, luma, t).map { t to it }
        }

    @Test
    fun `tick when text appears, rate limited`() {
        val events = collect(2, withText) + collect(1, FilteredFrame.EMPTY, 700) + collect(1, withText, 1050) +
            collect(1, FilteredFrame.EMPTY, 1400) + collect(1, withText, 2100)
        assertEquals(listOf(0L, 2100L), events.filter { it.second == Guidance.TEXT_APPEARED }.map { it.first })
    }

    @Test
    fun `no text hint after 10 s, repeated every 20 s, at most 3 times`() {
        val events = collect(300, FilteredFrame.EMPTY).filter { it.second == Guidance.NO_TEXT }.map { it.first }
        assertEquals(3, events.size)
        assertEquals(10_150L, events[0])
        assertTrue(events[1] - events[0] in 20_000L until 20_350L)
    }

    @Test
    fun `move away when everything is cut for 2 s, once`() {
        val events = collect(20, cutOnly).filter { it.second == Guidance.MOVE_AWAY }
        assertEquals(listOf(2100L), events.map { it.first })
    }

    @Test
    fun `hold still after 4 s of unstable text`() {
        val events = collect(20, withText, waiting = true).filter { it.second == Guidance.HOLD_STILL }
        assertEquals(listOf(4200L), events.map { it.first })
    }

    @Test
    fun `low light after 2 s of darkness`() {
        val events = collect(10, withText, luma = 20).filter { it.second == Guidance.LOW_LIGHT }
        assertEquals(listOf(2100L), events.map { it.first })
    }
}
