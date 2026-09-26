package pl.czytnik.core.pipeline

import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.Message
import pl.czytnik.core.model.SpeechChunk
import pl.czytnik.core.model.TextBlock

enum class ReadMode {
    /** Auto-odczyt po ustabilizowaniu kadru. */
    AUTO,

    /** Dotknięcie ekranu – czytaj teraz, bez warunku stabilności i pamięci przeczytanych. */
    FORCED,

    /** Powtórz ostatni tekst lub przeczytaj go ponownie po zmianie ustawienia tłumaczenia. */
    REPEAT,
}

/** Tekst źródłowy (przed tłumaczeniem) jako akapity w kolejności czytania. */
data class ReadSource(val paragraphs: List<String>)

/**
 * Zlecenie odczytu.
 * @param blocks bloki z klatki analizy (AUTO/FORCED)
 * @param source tekst do ponownego odczytu (REPEAT)
 * @param translateTo język docelowy albo `null`, gdy tłumaczenie wyłączone
 */
data class ReadRequest(
    val mode: ReadMode,
    val blocks: List<TextBlock> = emptyList(),
    val source: ReadSource? = null,
    val captureHighRes: Boolean = false,
    val translateTo: LanguageTag? = null,
)

/** Tekst gotowy do przeczytania. */
data class PreparedReading(
    val source: ReadSource,
    val sourceLanguage: LanguageTag,
    val speechLanguage: LanguageTag,
    val originalText: String,
    val spokenText: String,
    val chunks: List<SpeechChunk>,
    /** Komunikaty do wypowiedzenia przed tekstem (brak głosu, czytam oryginał…). */
    val notices: List<Message> = emptyList(),
    /** Modele tłumaczeń do pobrania. */
    val missingModels: List<LanguageTag> = emptyList(),
    /** Bloki (np. ze zdjęcia), które należy zapamiętać jako przeczytane. */
    val readBlocks: List<TextBlock> = emptyList(),
) {
    val translated: Boolean get() = !sourceLanguage.sameLanguageAs(speechLanguage)
}
