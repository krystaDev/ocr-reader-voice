package pl.czytnik.core.text

import pl.czytnik.core.model.SpeechChunk
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkerTest {

    private val polish = Locale.forLanguageTag("pl")

    @Test
    fun `short paragraphs become one chunk each with pause between`() {
        val chunks = TextChunker(maxChars = 500, paragraphPauseMs = 350).chunk(listOf("Pierwszy.", "  ", "Drugi."), polish)
        assertEquals(listOf(SpeechChunk("Pierwszy.", 350), SpeechChunk("Drugi.", 0)), chunks)
    }

    @Test
    fun `sentences are merged up to the limit`() {
        val text = "Zdanie pierwsze jest tutaj. Zdanie drugie jest tutaj. Zdanie trzecie jest tutaj."
        val chunks = TextChunker(maxChars = 60, paragraphPauseMs = 0).chunk(listOf(text), polish)
        assertEquals(listOf("Zdanie pierwsze jest tutaj. Zdanie drugie jest tutaj.", "Zdanie trzecie jest tutaj."), chunks.map { it.text })
    }

    @Test
    fun `abbreviations inside a short text do not split it`() {
        val text = "Przyjmować np. rano i wieczorem, ok. 2 tabl. dziennie."
        val chunks = TextChunker(maxChars = 500, paragraphPauseMs = 0).chunk(listOf(text), polish)
        assertEquals(listOf(text), chunks.map { it.text })
    }

    @Test
    fun `too long sentence is split at a comma or a space`() {
        val sentence = (1..40).joinToString(", ") { "słowo$it" } + "."
        val chunks = TextChunker(maxChars = 80, paragraphPauseMs = 0).chunk(listOf(sentence), polish)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 80 }, chunks.toString())
        assertEquals(
            TextFingerprint.normalizeForComparison(sentence),
            TextFingerprint.normalizeForComparison(chunks.joinToString(" ") { it.text }),
        )
    }

    @Test
    fun `pause only after the last chunk of a paragraph`() {
        val long = "Pierwsze zdanie akapitu. Drugie zdanie akapitu."
        val chunks = TextChunker(maxChars = 30, paragraphPauseMs = 350).chunk(listOf(long, "Koniec."), polish)
        assertEquals(listOf(0L, 350L, 0L), chunks.map { it.pauseAfterMs })
    }
}
