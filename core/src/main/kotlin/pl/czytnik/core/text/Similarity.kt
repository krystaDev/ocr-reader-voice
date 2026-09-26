package pl.czytnik.core.text

/**
 * Odcisk tekstu do porównań odpornych na drobne błędy OCR: znormalizowany tekst i zbiór trigramów znakowych.
 */
class TextFingerprint private constructor(val normalized: String, val trigrams: Set<String>) {

    val isEmpty: Boolean get() = normalized.isEmpty()

    /** Współczynnik Dice’a na trigramach (0..1). */
    fun dice(other: TextFingerprint): Double {
        if (isEmpty && other.isEmpty) return 1.0
        if (isEmpty || other.isEmpty) return 0.0
        val common = trigrams.count { it in other.trigrams }
        return 2.0 * common / (trigrams.size + other.trigrams.size)
    }

    /** Jaka część trigramów tego tekstu występuje w [other] (0..1). */
    fun containmentIn(other: TextFingerprint): Double = containmentIn(other.trigrams)

    fun containmentIn(otherTrigrams: Set<String>): Double {
        if (isEmpty) return 1.0
        return trigrams.count { it in otherTrigrams }.toDouble() / trigrams.size
    }

    override fun toString(): String = "TextFingerprint($normalized)"

    companion object {
        fun of(text: String): TextFingerprint {
            val normalized = normalizeForComparison(text)
            return TextFingerprint(normalized, trigramsOf(normalized))
        }

        /** Małe litery, tylko litery i cyfry, pojedyncze spacje. */
        fun normalizeForComparison(text: String): String {
            val sb = StringBuilder(text.length)
            var pendingSpace = false
            for (ch in text) {
                if (ch.isLetterOrDigit()) {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    pendingSpace = false
                    sb.append(ch.lowercaseChar())
                } else {
                    pendingSpace = true
                }
            }
            return sb.toString()
        }

        private fun trigramsOf(normalized: String): Set<String> {
            if (normalized.isEmpty()) return emptySet()
            val padded = " $normalized "
            if (padded.length < 3) return setOf(padded)
            return (0..padded.length - 3).mapTo(HashSet()) { padded.substring(it, it + 3) }
        }
    }
}
