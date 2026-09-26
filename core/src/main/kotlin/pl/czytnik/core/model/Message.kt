package pl.czytnik.core.model

/**
 * Komunikaty głosowe/stanu (docs/TECH-SPEC.md, rozdz. 4.8). Treść w wybranym języku interfejsu dostarcza moduł `app`.
 */
sealed interface Message {
    data object WelcomeFirstLaunch : Message
    data object Welcome : Message
    data object PermissionExplanation : Message
    data object PermissionDenied : Message
    data object NoText : Message
    data object HoldStill : Message
    data object MoveAway : Message
    data object Stopped : Message
    data object NothingReadYet : Message
    data object ReadingFailed : Message

    /** Przygotowanie tekstu trwa dłużej niż zwykle (zdjęcie, OCR, tłumaczenie). */
    data object StillWorking : Message
    data object TranslationOn : Message
    data object TranslationOff : Message
    data class DownloadingTranslation(val language: LanguageTag) : Message
    data object TranslationReady : Message
    data object NoInternetReadingOriginal : Message
    data object TranslationFailedReadingOriginal : Message
    data class ReadingOriginalWhileDownloading(val language: LanguageTag) : Message
    data class NoVoice(val language: LanguageTag) : Message
    data object TorchOn : Message
    data object TorchOff : Message
    data object AutoTorchOn : Message
    data class SpeechRate(val rate: Double) : Message
    data object CameraUnavailable : Message
    data object NoSpeechEngine : Message
}
