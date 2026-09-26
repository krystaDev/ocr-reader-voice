package pl.czytnik.core.state

import pl.czytnik.core.autoread.Guidance
import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.HapticKind
import pl.czytnik.core.model.Message
import pl.czytnik.core.pipeline.ReadMode
import pl.czytnik.core.pipeline.ReadRequest
import pl.czytnik.core.state.MainEffect.Announce
import pl.czytnik.core.state.MainEvent.*
import kotlin.math.roundToLong

data class Transition(val state: MainState, val effects: List<MainEffect> = emptyList())

/**
 * Maszyna stanów ekranu głównego (docs/TECH-SPEC.md, rozdz. 2): czysta funkcja `(stan, zdarzenie) → (stan, efekty)`.
 */
class MainReducer(private val config: AutoReadConfig) {

    fun reduce(state: MainState, event: MainEvent): Transition = when (event) {
        PermissionMissing -> Transition(
            state.copy(screen = Screen.NeedsPermission),
            listOf(Announce(Message.PermissionExplanation), MainEffect.RequestCameraPermission),
        )
        PermissionGranted -> when (state.screen) {
            Screen.NeedsPermission, Screen.PermissionDenied ->
                Transition(state.copy(screen = Screen.Starting), listOf(MainEffect.RestartCamera))
            else -> Transition(state)
        }
        PermissionRefused -> Transition(
            state.copy(screen = Screen.PermissionDenied),
            listOf(Announce(Message.PermissionDenied)),
        )
        StartupComplete -> onStartupComplete(state)
        is StartupFailed -> Transition(
            state.copy(screen = Screen.Error(event.kind)),
            listOf(Announce(event.kind.message()), MainEffect.Vibrate(HapticKind.ERROR)),
        )
        AppStopped -> onAppStopped(state)
        RetryPressed -> when (state.screen) {
            is Screen.Error -> Transition(state.copy(screen = Screen.Starting), listOf(MainEffect.RestartCamera))
            Screen.PermissionDenied -> Transition(state, listOf(MainEffect.OpenAppSettings))
            Screen.NeedsPermission -> Transition(state, listOf(MainEffect.RequestCameraPermission))
            else -> Transition(state)
        }

        is TextStable -> if (state.screen == Screen.Scanning && state.autoRead && event.blocks.isNotEmpty()) {
            startReading(
                state,
                ReadRequest(
                    mode = ReadMode.AUTO,
                    blocks = event.blocks,
                    captureHighRes = config.useHighResCapture,
                    translateTo = state.translateTo,
                ),
                listOf(MainEffect.Vibrate(HapticKind.DOUBLE), MainEffect.PlayStartSignal),
            )
        } else {
            Transition(state)
        }
        is GuidanceIssued -> if (state.screen == Screen.Scanning) onGuidance(state, event.guidance) else Transition(state)

        is ScreenTapped -> onScreenTapped(state, event)
        RepeatPressed -> onRepeat(state)
        TranslateToggled -> onTranslateToggled(state)
        SlowerPressed -> changeRate(state, -config.speechRateStep)
        FasterPressed -> changeRate(state, config.speechRateStep)
        TorchToggled -> onTorchToggled(state)
        is AutoReadChanged -> Transition(state.copy(autoRead = event.enabled))
        is AutoTorchChanged -> Transition(state.copy(autoTorch = event.enabled))
        is TargetLanguageChanged -> Transition(
            state.copy(targetLanguage = event.language),
            if (state.translate) listOf(MainEffect.EnsureTranslationModel(event.language)) else emptyList(),
        )

        is ReadingPrepared -> onReadingPrepared(state, event)
        is NothingRecognized -> onPreparationEnded(state, event.requestId, Message.NoText, onlyWhenForced = true)
        is PreparationFailed -> onPreparationEnded(state, event.requestId, Message.ReadingFailed, onlyWhenForced = false)
        is SpeechFinished -> {
            val screen = state.screen
            if (screen is Screen.Speaking && screen.requestId == event.requestId) {
                Transition(state.copy(screen = Screen.Scanning), listOf(MainEffect.SetAnalysisPaused(false)))
            } else {
                Transition(state)
            }
        }
        is TranslationModelDownloadStarted -> Transition(state, listOf(Announce(Message.DownloadingTranslation(event.language))))
        is TranslationModelReady -> Transition(state, listOf(Announce(Message.TranslationReady)))
        is TranslationModelFailed -> Transition(state, listOf(Announce(Message.NoInternetReadingOriginal)))
    }

    private fun onStartupComplete(state: MainState): Transition {
        if (state.screen != Screen.Starting) return Transition(state)
        val effects = buildList {
            add(MainEffect.SetAnalysisPaused(false))
            add(MainEffect.SetSpeechRate(state.speechRate))
            add(Announce(if (state.firstLaunch) Message.WelcomeFirstLaunch else Message.Welcome))
            if (state.translate) add(MainEffect.EnsureTranslationModel(state.targetLanguage))
        }
        return Transition(state.copy(screen = Screen.Scanning, firstLaunch = false), effects)
    }

    private fun onAppStopped(state: MainState): Transition {
        val effects = buildList {
            when (val screen = state.screen) {
                is Screen.Processing -> add(MainEffect.CancelPreparation(screen.requestId))
                is Screen.Speaking -> add(MainEffect.StopSpeech)
                else -> Unit
            }
            add(MainEffect.SetAnalysisPaused(true))
        }
        val screen = when (state.screen) {
            Screen.NeedsPermission, Screen.PermissionDenied -> state.screen
            else -> Screen.Starting
        }
        // Po odpięciu aparatu latarka gaśnie sama.
        return Transition(state.copy(screen = screen, torchOn = false), effects)
    }

    private fun onGuidance(state: MainState, guidance: Guidance): Transition = when (guidance) {
        Guidance.TEXT_APPEARED -> Transition(state, listOf(MainEffect.Vibrate(HapticKind.TICK)))
        Guidance.NO_TEXT -> Transition(state, listOf(Announce(Message.NoText)))
        Guidance.HOLD_STILL -> Transition(state, listOf(Announce(Message.HoldStill)))
        Guidance.MOVE_AWAY -> Transition(state, listOf(Announce(Message.MoveAway)))
        Guidance.LOW_LIGHT -> if (state.autoTorch && !state.torchOn && !state.autoTorchBlocked) {
            Transition(state.copy(torchOn = true), listOf(MainEffect.SetTorch(true), Announce(Message.AutoTorchOn)))
        } else {
            Transition(state)
        }
    }

    private fun onScreenTapped(state: MainState, event: ScreenTapped): Transition = when (val screen = state.screen) {
        Screen.Scanning -> startReading(
            state,
            ReadRequest(
                mode = ReadMode.FORCED,
                blocks = event.visibleBlocks,
                captureHighRes = true,
                translateTo = state.translateTo,
            ),
            listOf(MainEffect.PlayStartSignal),
        )
        is Screen.Processing -> Transition(
            state.copy(screen = Screen.Scanning),
            listOf(
                MainEffect.CancelPreparation(screen.requestId),
                MainEffect.MarkRead(screen.request.blocks),
                Announce(Message.Stopped),
                MainEffect.SetAnalysisPaused(false),
            ),
        )
        is Screen.Speaking -> Transition(
            state.copy(screen = Screen.Scanning),
            listOf(MainEffect.StopSpeech, MainEffect.SetAnalysisPaused(false)),
        )
        Screen.NeedsPermission -> Transition(state, listOf(MainEffect.RequestCameraPermission))
        Screen.PermissionDenied -> Transition(state, listOf(MainEffect.OpenAppSettings))
        is Screen.Error -> Transition(state.copy(screen = Screen.Starting), listOf(MainEffect.RestartCamera))
        Screen.Starting -> Transition(state)
    }

    private fun onRepeat(state: MainState): Transition {
        if (!state.screen.isActive()) return Transition(state)
        val source = state.lastSource
        val interrupt = interruptEffects(state)
        if (source == null) {
            return Transition(
                state.copy(screen = Screen.Scanning),
                interrupt + Announce(Message.NothingReadYet) + resumeIfWasBusy(state),
            )
        }
        return startReading(
            state,
            ReadRequest(mode = ReadMode.REPEAT, source = source, translateTo = state.translateTo),
            interrupt,
        )
    }

    private fun onTranslateToggled(state: MainState): Transition {
        val toggled = state.copy(translate = !state.translate)
        val ensureModel = if (toggled.translate) listOf(MainEffect.EnsureTranslationModel(state.targetLanguage)) else emptyList()
        if (!state.screen.isActive()) return Transition(toggled, ensureModel)
        val screen = state.screen
        return when {
            // Zmiana w trakcie przetwarzania: zaczynamy to samo zlecenie od nowa z nowym ustawieniem.
            screen is Screen.Processing -> startReading(
                toggled,
                screen.request.copy(translateTo = toggled.translateTo),
                listOf(MainEffect.CancelPreparation(screen.requestId)) + ensureModel,
            )
            state.lastSource != null -> startReading(
                toggled,
                ReadRequest(mode = ReadMode.REPEAT, source = state.lastSource, translateTo = toggled.translateTo),
                interruptEffects(state) + ensureModel,
            )
            else -> Transition(
                toggled,
                ensureModel + Announce(if (toggled.translate) Message.TranslationOn else Message.TranslationOff),
            )
        }
    }

    private fun changeRate(state: MainState, delta: Double): Transition {
        val rate = ((state.speechRate + delta).coerceIn(config.minSpeechRate, config.maxSpeechRate) * 100)
            .roundToLong() / 100.0
        val effects = buildList {
            add(MainEffect.SetSpeechRate(rate))
            // Próbka tylko, gdy nic nie jest czytane – nie przerywamy tekstu.
            if (state.screen == Screen.Scanning || state.screen is Screen.Error) add(Announce(Message.SpeechRate(rate)))
        }
        return Transition(state.copy(speechRate = rate), effects)
    }

    private fun onTorchToggled(state: MainState): Transition {
        if (!state.screen.isActive()) return Transition(state)
        val on = !state.torchOn
        val effects = buildList {
            add(MainEffect.SetTorch(on))
            if (state.screen == Screen.Scanning) add(Announce(if (on) Message.TorchOn else Message.TorchOff))
        }
        return Transition(state.copy(torchOn = on, autoTorchBlocked = state.autoTorchBlocked || !on), effects)
    }

    private fun onReadingPrepared(state: MainState, event: ReadingPrepared): Transition {
        val screen = state.screen
        if (screen !is Screen.Processing || screen.requestId != event.requestId) return Transition(state)
        val reading = event.reading
        val effects = buildList {
            add(MainEffect.MarkRead(screen.request.blocks + reading.readBlocks))
            reading.missingModels.forEach { add(MainEffect.EnsureTranslationModel(it)) }
            reading.notices.forEach { add(Announce(it)) }
            add(MainEffect.Speak(event.requestId, reading.chunks, reading.speechLanguage, state.speechRate))
        }
        return Transition(
            state.copy(
                screen = Screen.Speaking(event.requestId),
                lastSource = reading.source,
                lastSpokenText = reading.spokenText,
            ),
            effects,
        )
    }

    private fun onPreparationEnded(
        state: MainState,
        requestId: Long,
        message: Message,
        onlyWhenForced: Boolean,
    ): Transition {
        val screen = state.screen
        if (screen !is Screen.Processing || screen.requestId != requestId) return Transition(state)
        val effects = buildList {
            // Nieudany auto-odczyt nie może startować w kółko na tym samym kadrze.
            add(MainEffect.MarkRead(screen.request.blocks))
            if (!onlyWhenForced || screen.request.mode == ReadMode.FORCED) add(Announce(message))
            add(MainEffect.SetAnalysisPaused(false))
        }
        return Transition(state.copy(screen = Screen.Scanning), effects)
    }

    private fun startReading(state: MainState, request: ReadRequest, before: List<MainEffect>): Transition {
        val id = state.nextRequestId
        val pause = if (state.screen == Screen.Scanning) listOf(MainEffect.SetAnalysisPaused(true)) else emptyList()
        return Transition(
            state.copy(screen = Screen.Processing(id, request), nextRequestId = id + 1),
            before + pause + MainEffect.PrepareReading(id, request),
        )
    }

    /** Przerywa bieżące przetwarzanie lub mowę. */
    private fun interruptEffects(state: MainState): List<MainEffect> = when (val screen = state.screen) {
        is Screen.Processing -> listOf(MainEffect.CancelPreparation(screen.requestId))
        is Screen.Speaking -> listOf(MainEffect.StopSpeech)
        else -> emptyList()
    }

    private fun resumeIfWasBusy(state: MainState): List<MainEffect> =
        if (state.screen is Screen.Processing || state.screen is Screen.Speaking) {
            listOf(MainEffect.SetAnalysisPaused(false))
        } else {
            emptyList()
        }

    private fun Screen.isActive(): Boolean =
        this == Screen.Scanning || this is Screen.Processing || this is Screen.Speaking

    private fun ErrorKind.message(): Message = when (this) {
        ErrorKind.CAMERA_UNAVAILABLE -> Message.CameraUnavailable
        ErrorKind.NO_SPEECH_ENGINE -> Message.NoSpeechEngine
    }
}
