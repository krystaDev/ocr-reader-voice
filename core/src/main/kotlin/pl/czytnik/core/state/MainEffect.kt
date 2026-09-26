package pl.czytnik.core.state

import pl.czytnik.core.model.HapticKind
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.Message
import pl.czytnik.core.model.SpeechChunk
import pl.czytnik.core.model.TextBlock
import pl.czytnik.core.pipeline.ReadRequest

/** Działania do wykonania przez platformę (moduł `app`) po przejściu stanu. */
sealed interface MainEffect {
    data object RequestCameraPermission : MainEffect
    data object OpenAppSettings : MainEffect
    data object RestartCamera : MainEffect

    /** Wstrzymanie/wznowienie analizy klatek (PRD: analiza wstrzymana podczas czytania). */
    data class SetAnalysisPaused(val paused: Boolean) : MainEffect

    /** Zrób zdjęcie (jeśli trzeba), OCR i przygotuj tekst; wynik wraca jako ReadingPrepared/NothingRecognized. */
    data class PrepareReading(val requestId: Long, val request: ReadRequest) : MainEffect
    data class CancelPreparation(val requestId: Long) : MainEffect

    data class Speak(
        val requestId: Long,
        val chunks: List<SpeechChunk>,
        val language: LanguageTag,
        val rate: Double,
    ) : MainEffect

    data object StopSpeech : MainEffect
    data class SetSpeechRate(val rate: Double) : MainEffect
    data class Announce(val message: Message) : MainEffect
    data class Vibrate(val kind: HapticKind) : MainEffect
    data object PlayStartSignal : MainEffect
    data class SetTorch(val on: Boolean) : MainEffect
    data class EnsureTranslationModel(val language: LanguageTag) : MainEffect

    /** Zapamiętaj bloki jako przeczytane (obsługiwane wewnątrz MainController, nie trafia do platformy). */
    data class MarkRead(val blocks: List<TextBlock>) : MainEffect
}
