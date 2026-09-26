package pl.czytnik.core.text

import pl.czytnik.core.model.SpeechChunk
import java.text.BreakIterator
import java.util.Locale

/**
 * Dzieli akapity na fragmenty dla TTS (docs/TECH-SPEC.md, rozdz. 4.5): zdania łączone do [maxChars] znaków,
 * zbyt długie zdanie dzielone po przecinku lub spacji, pauza [paragraphPauseMs] po każdym akapicie (poza ostatnim).
 */
class TextChunker(private val maxChars: Int, private val paragraphPauseMs: Long) {

    fun chunk(paragraphs: List<String>, locale: Locale): List<SpeechChunk> {
        val nonBlank = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        return nonBlank.flatMapIndexed { index, paragraph ->
            val pieces = splitParagraph(paragraph, locale)
            val pause = if (index < nonBlank.lastIndex) paragraphPauseMs else 0
            pieces.mapIndexed { i, piece -> SpeechChunk(piece, if (i == pieces.lastIndex) pause else 0) }
        }
    }

    private fun splitParagraph(paragraph: String, locale: Locale): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences(paragraph, locale).flatMap(::splitLongSentence)) {
            if (current.isNotEmpty() && current.length + 1 + sentence.length > maxChars) {
                result += current.toString()
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun sentences(text: String, locale: Locale): List<String> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
            start = end
            end = iterator.next()
        }
        return result
    }

    private fun splitLongSentence(sentence: String): List<String> {
        if (sentence.length <= maxChars) return listOf(sentence)
        val result = mutableListOf<String>()
        var rest = sentence
        while (rest.length > maxChars) {
            val window = rest.substring(0, maxChars + 1)
            val cut = window.lastIndexOf(", ").takeIf { it > maxChars / 2 }?.plus(1)
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: maxChars
            result += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) result += rest
        return result
    }
}
