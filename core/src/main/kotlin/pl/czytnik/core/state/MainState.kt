package pl.czytnik.core.state

import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.pipeline.ReadRequest
import pl.czytnik.core.pipeline.ReadSource

/** Ekran główny (docs/TECH-SPEC.md, rozdz. 2.1). */
sealed interface Screen {
    data object NeedsPermission : Screen
    data object PermissionDenied : Screen
    data object Starting : Screen
    data object Scanning : Screen
    data class Processing(val requestId: Long, val request: ReadRequest) : Screen
    data class Speaking(val requestId: Long) : Screen
    data class Error(val kind: ErrorKind) : Screen
}

enum class ErrorKind { CAMERA_UNAVAILABLE, NO_SPEECH_ENGINE }

data class MainState(
    val screen: Screen = Screen.Starting,
    /** Ostatnio przeczytany tekst (przed tłumaczeniem) – dla „Powtórz” i zmiany „Tłumacz”. */
    val lastSource: ReadSource? = null,
    /** Tekst pokazywany na ekranie (to, co zostało przeczytane). */
    val lastSpokenText: String? = null,
    val translate: Boolean = true,
    val targetLanguage: LanguageTag = LanguageTag.POLISH,
    val speechRate: Double = 1.0,
    val autoRead: Boolean = true,
    val autoTorch: Boolean = true,
    val torchOn: Boolean = false,
    /** Użytkownik wyłączył latarkę ręcznie – do końca sesji nie włączamy jej automatycznie (odblask na szkle). */
    val autoTorchBlocked: Boolean = false,
    val firstLaunch: Boolean = true,
    val nextRequestId: Long = 1,
) {
    val translateTo: LanguageTag? get() = targetLanguage.takeIf { translate }
}
