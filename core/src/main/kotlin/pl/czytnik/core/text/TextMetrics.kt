package pl.czytnik.core.text

/**
 * Miary jakości OCR (docs/TECH-SPEC.md, rozdz. 6.3). PRD: poprawność ≥ 95% znaków, czyli CER ≤ 0,05.
 */
object TextMetrics {

    /**
     * Character Error Rate: odległość Levenshteina / długość wzorca, po ujednoliceniu białych znaków
     * (podział na linie przez OCR nie jest błędem). 0 = bez błędów; może przekroczyć 1.
     */
    fun characterErrorRate(expected: String, actual: String): Double {
        val e = normalizeWhitespace(expected)
        val a = normalizeWhitespace(actual)
        if (e.isEmpty()) return if (a.isEmpty()) 0.0 else 1.0
        return levenshtein(e, a).toDouble() / e.length
    }

    /** Odsetek poprawnych znaków (1 − CER, nie mniej niż 0). */
    fun characterAccuracy(expected: String, actual: String): Double =
        (1.0 - characterErrorRate(expected, actual)).coerceAtLeast(0.0)

    private fun normalizeWhitespace(text: String) = text.trim().replace(Regex("\\s+"), " ")

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
