package pl.czytnik.core.ports

import pl.czytnik.core.model.LanguageTag

/** Wynik rozpoznania języka; [confidence] 0..1. */
data class LanguageGuess(val language: LanguageTag, val confidence: Double)

/** Rozpoznawanie języka tekstu (w aplikacji: ML Kit Language Identification). */
fun interface LanguageIdentifier {
    suspend fun identify(text: String): LanguageGuess
}

/** Tłumaczenie offline (w aplikacji: ML Kit Translation). */
fun interface Translator {
    suspend fun translate(text: String, from: LanguageTag, to: LanguageTag): String
}

/** Modele tłumaczeń na urządzeniu. */
interface TranslationModels {
    fun isSupported(language: LanguageTag): Boolean
    suspend fun isDownloaded(language: LanguageTag): Boolean
}

/** Dostępne głosy syntezatora mowy. */
fun interface VoiceCatalog {
    fun hasVoice(language: LanguageTag): Boolean
}
