package pl.czytnik.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AutoReadConfigTest {

    @Test
    fun `defaults match the tech spec`() {
        val config = AutoReadConfig()
        assertEquals(350, config.analysisIntervalMs)
        assertEquals(1000, config.stableWindowMs)
        assertEquals(2000, config.leaveMs)
    }

    @Test
    fun `similarity thresholds outside 0 to 1 are rejected`() {
        assertFailsWith<IllegalArgumentException> { AutoReadConfig(stableSimilarity = 1.5) }
    }

    @Test
    fun `presence threshold above same-block threshold breaks hysteresis`() {
        assertFailsWith<IllegalArgumentException> {
            AutoReadConfig(sameBlockSimilarity = 0.5, presentSimilarity = 0.7)
        }
    }
}
