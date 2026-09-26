package pl.czytnik.core.state

import pl.czytnik.core.autoread.Guidance
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.TextBlock
import pl.czytnik.core.pipeline.PreparedReading

sealed interface MainEvent {
    // Uprawnienia i start
    data object PermissionMissing : MainEvent
    data object PermissionGranted : MainEvent
    data object PermissionRefused : MainEvent
    data object StartupComplete : MainEvent
    data class StartupFailed(val kind: ErrorKind) : MainEvent
    data object AppStopped : MainEvent

    // Auto-odczyt (z AutoReadEngine)
    data class TextStable(val blocks: List<TextBlock>) : MainEvent
    data class GuidanceIssued(val guidance: Guidance) : MainEvent

    // Użytkownik
    /** Dotknięcie podglądu; [visibleBlocks] – bloki do wymuszonego odczytu z bieżącego kadru. */
    data class ScreenTapped(val visibleBlocks: List<TextBlock>) : MainEvent
    data object RepeatPressed : MainEvent
    data object TranslateToggled : MainEvent
    data object SlowerPressed : MainEvent
    data object FasterPressed : MainEvent
    data object TorchToggled : MainEvent
    data object RetryPressed : MainEvent
    data class AutoReadChanged(val enabled: Boolean) : MainEvent
    data class AutoTorchChanged(val enabled: Boolean) : MainEvent
    data class TargetLanguageChanged(val language: LanguageTag) : MainEvent

    // Wyniki pracy w tle
    data class ReadingPrepared(val requestId: Long, val reading: PreparedReading) : MainEvent
    data class NothingRecognized(val requestId: Long) : MainEvent
    data class PreparationFailed(val requestId: Long) : MainEvent
    data class SpeechFinished(val requestId: Long) : MainEvent
    data class TranslationModelDownloadStarted(val language: LanguageTag) : MainEvent
    data class TranslationModelReady(val language: LanguageTag) : MainEvent
    data class TranslationModelFailed(val language: LanguageTag) : MainEvent
}
