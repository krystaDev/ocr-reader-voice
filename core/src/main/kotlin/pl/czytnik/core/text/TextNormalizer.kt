package pl.czytnik.core.text

import pl.czytnik.core.model.LanguageTag

/**
 * Przygotowanie tekstu z OCR do tłumaczenia i mowy (docs/TECH-SPEC.md, rozdz. 4.2).
 */
object TextNormalizer {

    private val HYPHENS = setOf('-', '‐', '‑', '­')

    /**
     * Łączy linie bloku w jeden akapit.
     * - „wyra-” + „zu” → „wyrazu” (przeniesienie, następna linia od małej litery),
     * - „SPIDER-” + „MAN” → „SPIDER-MAN” (WIELKIE LITERY: łącznik zostaje, bez spacji),
     * - w pozostałych przypadkach linie łączone spacją.
     */
    fun joinLines(lines: List<String>): String {
        val sb = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (sb.isEmpty()) {
                sb.append(line)
                continue
            }
            val last = sb.last()
            val beforeLast = if (sb.length >= 2) sb[sb.length - 2] else ' '
            val first = line.first()
            if (last in HYPHENS && beforeLast.isLetter() && first.isLetter()) {
                if (first.isLowerCase()) {
                    sb.setLength(sb.length - 1)
                } else if (last == '­') {
                    sb.setLength(sb.length - 1)
                }
                sb.append(line)
            } else {
                sb.append(' ').append(line)
            }
        }
        return sb.toString()
    }

    /** Czy tekst jest pisany głównie WIELKIMI LITERAMI (typowe dla komiksów). */
    fun isMostlyUpperCase(text: String, threshold: Double = 0.7, minLetters: Int = 4): Boolean {
        val letters = text.filter(Char::isLetter)
        if (letters.length < minLetters) return false
        return letters.count(Char::isUpperCase).toDouble() / letters.length >= threshold
    }

    /**
     * Zamienia tekst pisany WIELKIMI LITERAMI na zdania: „I CAN’T DO THIS! WHO ARE YOU?” → „I can’t do this! Who are you?”.
     * Dla angielskiego zaimek „I” (także „I’m”, „I’ll”…) zostaje wielką literą. Tekst pisany normalnie nie jest zmieniany.
     */
    fun normalizeAllCaps(text: String, language: LanguageTag): String {
        if (!isMostlyUpperCase(text)) return text
        val lower = text.lowercase()
        val sb = StringBuilder(lower.length)
        var capitalizeNext = true
        for (ch in lower) {
            if (capitalizeNext && ch.isLetter()) {
                sb.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(ch)
            }
            if (ch == '.' || ch == '!' || ch == '?' || ch == '…') capitalizeNext = true
        }
        return if (language == LanguageTag.ENGLISH) capitalizeEnglishI(sb.toString()) else sb.toString()
    }

    private val ENGLISH_I = Regex("(?<![\\p{L}\\p{N}])i(?=$|[^\\p{L}\\p{N}])")

    private fun capitalizeEnglishI(text: String): String = text.replace(ENGLISH_I, "I")
}
