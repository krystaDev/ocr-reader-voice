package pl.czytnik.core.pipeline

import kotlinx.coroutines.CancellationException
import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.language.LanguageResolver
import pl.czytnik.core.language.TranslationDecision
import pl.czytnik.core.language.TranslationPolicy
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.Message
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.model.TextBlock
import pl.czytnik.core.ports.LanguageGuess
import pl.czytnik.core.ports.LanguageIdentifier
import pl.czytnik.core.ports.TranslationModels
import pl.czytnik.core.ports.Translator
import pl.czytnik.core.ports.VoiceCatalog
import pl.czytnik.core.text.Paragraphs
import pl.czytnik.core.text.TextChunker
import pl.czytnik.core.text.TextNormalizer
import java.util.Locale

/**
 * Przygotowanie odczytu (docs/TECH-SPEC.md, rozdz. 1.3): zdjęcie → bloki → akapity → język → (tłumaczenie) → fragmenty.
 * Jedna instancja na sesję (pamięta ostatni pewny język i komunikaty, które już padły).
 */
class ReadPipeline(
    private val config: AutoReadConfig,
    private val languageIdentifier: LanguageIdentifier,
    private val translator: Translator,
    private val models: TranslationModels,
    private val voices: VoiceCatalog,
    deviceLanguage: LanguageTag,
) {
    private val resolver = LanguageResolver(config.languageMinConfidence, deviceLanguage)
    private val chunker = TextChunker(config.speechChunkMaxChars, config.paragraphPauseMs)
    private val noticesGiven = mutableSetOf<Message>()

    /**
     * @param capturePhoto robi zdjęcie i OCR; `null`, gdy się nie udało
     * @param selectFromPhoto wybiera bloki do przeczytania ze zdjęcia (`forced` = odczyt wymuszony)
     * @return `null`, gdy nie ma czego czytać
     */
    suspend fun prepare(
        request: ReadRequest,
        capturePhoto: suspend () -> OcrFrame? = { null },
        selectFromPhoto: (photo: OcrFrame, forced: Boolean) -> List<TextBlock> = { _, _ -> emptyList() },
    ): PreparedReading? {
        val readBlocks: List<TextBlock>
        val paragraphs: List<String>
        if (request.source != null) {
            readBlocks = emptyList()
            paragraphs = request.source.paragraphs.filter { it.isNotBlank() }
        } else {
            val fromPhoto = if (request.captureHighRes) {
                capturePhotoSafely(capturePhoto)?.let { selectFromPhoto(it, request.mode == ReadMode.FORCED) }.orEmpty()
            } else {
                emptyList()
            }
            readBlocks = fromPhoto.ifEmpty { request.blocks }
            paragraphs = Paragraphs.from(readBlocks)
        }
        if (paragraphs.isEmpty()) return null

        val original = paragraphs.joinToString("\n")
        val sourceLanguage = resolver.resolve(identifySafely(original))
        val normalized = if (config.normalizeAllCaps) {
            paragraphs.map { TextNormalizer.normalizeAllCaps(it, sourceLanguage) }
        } else {
            paragraphs
        }

        val notices = mutableListOf<Message>()
        var missingModels = emptyList<LanguageTag>()
        var spokenParagraphs = normalized
        var speechLanguage = sourceLanguage
        when (val decision = TranslationPolicy.decide(request.translateTo, sourceLanguage, models)) {
            TranslationDecision.NotNeeded -> Unit
            is TranslationDecision.ModelsMissing -> {
                missingModels = decision.missing
                noticeOnce(Message.ReadingOriginalWhileDownloading(decision.missing.first()), notices)
            }
            is TranslationDecision.Translate -> {
                val translated = translateSafely(normalized, decision)
                if (translated != null) {
                    spokenParagraphs = translated
                    speechLanguage = decision.to
                } else {
                    notices += Message.TranslationFailedReadingOriginal
                }
            }
        }

        if (!voices.hasVoice(speechLanguage)) noticeOnce(Message.NoVoice(speechLanguage), notices)

        return PreparedReading(
            source = ReadSource(paragraphs),
            sourceLanguage = sourceLanguage,
            speechLanguage = speechLanguage,
            originalText = original,
            spokenText = spokenParagraphs.joinToString("\n"),
            chunks = chunker.chunk(spokenParagraphs, Locale.forLanguageTag(speechLanguage.code)),
            notices = notices,
            missingModels = missingModels,
            readBlocks = readBlocks,
        )
    }

    private fun noticeOnce(message: Message, into: MutableList<Message>) {
        if (noticesGiven.add(message)) into += message
    }

    private suspend fun capturePhotoSafely(capture: suspend () -> OcrFrame?): OcrFrame? =
        try {
            capture()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun identifySafely(text: String): LanguageGuess =
        try {
            languageIdentifier.identify(text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LanguageGuess(LanguageTag.UNDETERMINED, 0.0)
        }

    private suspend fun translateSafely(
        paragraphs: List<String>,
        decision: TranslationDecision.Translate,
    ): List<String>? =
        try {
            paragraphs.map { translator.translate(it, decision.from, decision.to) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
