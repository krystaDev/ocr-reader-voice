package pl.czytnik.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextMetricsTest {

    @Test
    fun `identical text has no errors`() {
        assertEquals(0.0, TextMetrics.characterErrorRate("Paracetamol 500 mg", "Paracetamol 500 mg"))
    }

    @Test
    fun `line breaks and extra spaces are not errors`() {
        assertEquals(0.0, TextMetrics.characterErrorRate("Weź jedną tabletkę", "Weź  jedną\ntabletkę "))
    }

    @Test
    fun `substitution insertion and deletion`() {
        // 1 zamiana (O→0) na 10 znaków
        assertEquals(0.1, TextMetrics.characterErrorRate("HELLO WORLD".take(10), "HELL0 WORL"))
        assertEquals(0.25, TextMetrics.characterErrorRate("abcd", "abcde"))
        assertEquals(0.25, TextMetrics.characterErrorRate("abcd", "abd"))
    }

    @Test
    fun `accuracy is clamped at zero`() {
        assertEquals(0.0, TextMetrics.characterAccuracy("ab", "completely different"))
        assertEquals(0.95, TextMetrics.characterAccuracy("a".repeat(100), "a".repeat(95) + "b".repeat(5)), 1e-9)
    }

    @Test
    fun `empty expected text`() {
        assertEquals(0.0, TextMetrics.characterErrorRate("", " "))
        assertEquals(1.0, TextMetrics.characterErrorRate("", "noise"))
    }
}
