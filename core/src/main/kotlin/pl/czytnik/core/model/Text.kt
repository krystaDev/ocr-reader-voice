package pl.czytnik.core.model

/** Linia tekstu z OCR. [confidence] jest `null`, gdy silnik OCR jej nie podaje. */
data class TextLine(val text: String, val box: Box, val confidence: Double? = null)

/** Blok tekstu z OCR (akapit, dymek komiksu). */
data class TextBlock(val lines: List<TextLine>, val box: Box) {
    val rawText: String get() = lines.joinToString("\n") { it.text }

    val letterCount: Int get() = lines.sumOf { line -> line.text.count(Char::isLetter) }

    companion object {
        fun of(lines: List<TextLine>): TextBlock {
            val box = requireNotNull(Box.unionOf(lines.map { it.box })) { "Block needs at least one line" }
            return TextBlock(lines, box)
        }
    }
}

/** Wynik OCR jednej klatki (analizy lub zdjęcia). [meanLuma] – średnia jasność 0..255, jeśli znana. */
data class OcrFrame(val blocks: List<TextBlock>, val meanLuma: Int? = null)

/** Tag języka BCP-47, np. `en`, `pl`; [UNDETERMINED] gdy nie udało się rozpoznać. */
@JvmInline
value class LanguageTag(val code: String) {
    val isDetermined: Boolean get() = code != UNDETERMINED.code

    /** Podstawowy podtag języka: `en-US` → `en`. */
    val primary: LanguageTag get() = LanguageTag(code.substringBefore('-').lowercase())

    fun sameLanguageAs(other: LanguageTag): Boolean = primary.code == other.primary.code

    override fun toString(): String = code

    companion object {
        val UNDETERMINED = LanguageTag("und")
        val ENGLISH = LanguageTag("en")
        val POLISH = LanguageTag("pl")
    }
}
