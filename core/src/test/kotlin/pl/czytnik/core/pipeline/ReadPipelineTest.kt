package pl.czytnik.core.pipeline

import kotlinx.coroutines.test.runTest
import pl.czytnik.core.FakeLanguageIdentifier
import pl.czytnik.core.FakeTranslationModels
import pl.czytnik.core.FakeTranslator
import pl.czytnik.core.FakeVoices
import pl.czytnik.core.block
import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.Message
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.ports.LanguageGuess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadPipelineTest {

    private val languageId = FakeLanguageIdentifier()
    private val translator = FakeTranslator()
    private val models = FakeTranslationModels()
    private var voices = FakeVoices()

    private fun pipeline(config: AutoReadConfig = AutoReadConfig()) =
        ReadPipeline(config, languageId, translator, models, voices, deviceLanguage = LanguageTag.POLISH)

    private val bubble = block("I CAN'T DO THIS\nALONE, CAP!")

    @Test
    fun `comic bubble is normalized and translated to Polish`() = runTest {
        val result = pipeline().prepare(ReadRequest(ReadMode.AUTO, blocks = listOf(bubble), translateTo = LanguageTag.POLISH))!!
        assertEquals("I CAN'T DO THIS ALONE, CAP!", result.originalText)
        assertEquals(listOf("I can't do this alone, cap!"), translator.calls)
        assertEquals("[pl] I can't do this alone, cap!", result.spokenText)
        assertEquals(LanguageTag.POLISH, result.speechLanguage)
        assertTrue(result.translated)
        assertEquals(listOf(bubble), result.readBlocks)
    }

    @Test
    fun `translation off reads the original in its language`() = runTest {
        val result = pipeline().prepare(ReadRequest(ReadMode.AUTO, blocks = listOf(bubble), translateTo = null))!!
        assertEquals(LanguageTag.ENGLISH, result.speechLanguage)
        assertEquals("I can't do this alone, cap!", result.spokenText)
        assertTrue(translator.calls.isEmpty())
    }

    @Test
    fun `all caps normalization can be disabled`() = runTest {
        val result = pipeline(AutoReadConfig(normalizeAllCaps = false))
            .prepare(ReadRequest(ReadMode.AUTO, blocks = listOf(bubble)))!!
        assertEquals("I CAN'T DO THIS ALONE, CAP!", result.spokenText)
    }

    @Test
    fun `photo blocks are preferred over analysis blocks`() = runTest {
        val photoBubble = block("I CAN'T DO THIS ALONE, CAP! AND MORE")
        val result = pipeline().prepare(
            ReadRequest(ReadMode.AUTO, blocks = listOf(bubble), captureHighRes = true),
            capturePhoto = { OcrFrame(listOf(photoBubble)) },
            selectFromPhoto = { photo, forced ->
                assertEquals(false, forced)
                photo.blocks
            },
        )!!
        assertEquals(listOf(photoBubble), result.readBlocks)
    }

    @Test
    fun `failed or empty photo falls back to analysis blocks`() = runTest {
        val failing = pipeline().prepare(
            ReadRequest(ReadMode.AUTO, blocks = listOf(bubble), captureHighRes = true),
            capturePhoto = { error("camera closed") },
        )!!
        assertEquals(listOf(bubble), failing.readBlocks)
        val empty = pipeline().prepare(
            ReadRequest(ReadMode.AUTO, blocks = listOf(bubble), captureHighRes = true),
            capturePhoto = { OcrFrame(emptyList()) },
            selectFromPhoto = { photo, _ -> photo.blocks },
        )!!
        assertEquals(listOf(bubble), empty.readBlocks)
    }

    @Test
    fun `nothing to read`() = runTest {
        assertNull(pipeline().prepare(ReadRequest(ReadMode.FORCED, captureHighRes = true), capturePhoto = { null }))
    }

    @Test
    fun `repeat reads the stored source`() = runTest {
        val result = pipeline().prepare(ReadRequest(ReadMode.REPEAT, source = ReadSource(listOf("HELLO THERE, FRIEND.")), translateTo = LanguageTag.POLISH))!!
        assertEquals("[pl] Hello there, friend.", result.spokenText)
        assertTrue(result.readBlocks.isEmpty())
    }

    @Test
    fun `missing model reads original and asks for download once`() = runTest {
        languageId.result = LanguageGuess(LanguageTag("de"), 0.9)
        val p = pipeline()
        val first = p.prepare(ReadRequest(ReadMode.AUTO, blocks = listOf(block("Guten Morgen, wie geht es?")), translateTo = LanguageTag.POLISH))!!
        assertEquals(LanguageTag("de"), first.speechLanguage)
        assertEquals(listOf(LanguageTag("de")), first.missingModels)
        assertEquals(listOf<Message>(Message.ReadingOriginalWhileDownloading(LanguageTag("de")), Message.NoVoice(LanguageTag("de"))), first.notices)
        val second = p.prepare(ReadRequest(ReadMode.AUTO, blocks = listOf(block("Danke schön, bis bald!")), translateTo = LanguageTag.POLISH))!!
        assertTrue(second.notices.isEmpty(), "notices are given once per session")
        assertEquals(listOf(LanguageTag("de")), second.missingModels)
    }

    @Test
    fun `translation error reads the original`() = runTest {
        translator.failing = true
        val result = pipeline().prepare(ReadRequest(ReadMode.AUTO, blocks = listOf(bubble), translateTo = LanguageTag.POLISH))!!
        assertEquals(LanguageTag.ENGLISH, result.speechLanguage)
        assertEquals(listOf<Message>(Message.TranslationFailedReadingOriginal), result.notices)
    }

    @Test
    fun `language identification error falls back to device language without translation`() = runTest {
        val p = ReadPipeline(AutoReadConfig(), { error("boom") }, translator, models, voices, LanguageTag.POLISH)
        val result = p.prepare(ReadRequest(ReadMode.AUTO, blocks = listOf(block("Tekst po polsku.")), translateTo = LanguageTag.POLISH))!!
        assertEquals(LanguageTag.POLISH, result.speechLanguage)
        assertTrue(translator.calls.isEmpty())
    }

    @Test
    fun `long text is chunked`() = runTest {
        val longText = (1..30).joinToString(" ") { "Sentence number $it is here." }
        val result = pipeline().prepare(ReadRequest(ReadMode.AUTO, blocks = listOf(block(longText))))!!
        assertTrue(result.chunks.size > 1)
        assertTrue(result.chunks.all { it.text.length <= 500 })
    }
}
