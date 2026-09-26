package pl.czytnik.app.main

import android.app.Application
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.czytnik.app.camera.CameraSession
import pl.czytnik.app.camera.TextFrameAnalyzer
import pl.czytnik.app.diagnostics.Diagnostics
import pl.czytnik.app.feedback.Haptics
import pl.czytnik.app.feedback.StartSignal
import pl.czytnik.app.language.MlKitLanguageIdentifier
import pl.czytnik.app.settings.Settings
import pl.czytnik.app.speech.AndroidSpeechOutput
import pl.czytnik.app.translate.MlKitTranslation
import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.Box
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.Message
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.pipeline.ReadPipeline
import pl.czytnik.core.state.ErrorKind
import pl.czytnik.core.state.MainController
import pl.czytnik.core.state.MainEffect
import pl.czytnik.core.state.MainEvent
import pl.czytnik.core.state.MainState
import pl.czytnik.core.state.Screen
import java.util.Locale

data class MainUiState(
    val screen: Screen = Screen.Starting,
    val translate: Boolean = true,
    val targetLanguage: LanguageTag = LanguageTag.POLISH,
    val autoRead: Boolean = true,
    val autoTorch: Boolean = true,
    val speechRate: Double = 1.0,
    val torchOn: Boolean = false,
    val hasFlash: Boolean = false,
    val lastSpokenText: String? = null,
    /** Ramki bloków z ostatniej klatki (0..1) – rysowane na podglądzie. */
    val blocks: List<Box> = emptyList(),
    /** Ostatni komunikat – przy włączonym TalkBack wypowiada go TalkBack (liveRegion) zamiast naszego TTS. */
    val message: String = "",
    val cameraGeneration: Int = 0,
)

sealed interface UiCommand {
    data object RequestCameraPermission : UiCommand
    data object OpenAppSettings : UiCommand
}

/**
 * Łączy [MainController] (logika z modułu `core`) z Androidem: przekazuje zdarzenia i klatki, wykonuje efekty.
 * Wszystko na wątku głównym.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = Settings(application)
    var config: AutoReadConfig = settings.config()
        private set
    private val translation = MlKitTranslation()
    private var controller = MainController(config, settings.initialState(defaultTargetLanguage()))

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val languageIdentifier = MlKitLanguageIdentifier()
    private val speech = AndroidSpeechOutput(application, ::onSpeechReady)
    private val haptics = Haptics(application)
    private val startSignal = StartSignal()
    private val accessibility = application.getSystemService(AccessibilityManager::class.java)
    private var pipeline = newPipeline()

    val camera = CameraSession(application)
    val diagnostics = Diagnostics(SystemClock.elapsedRealtime())

    @Volatile
    private var analysisPaused = true
    private var cameraReady = false
    private var speechReady: Boolean? = null
    private var preparation: Job? = null
    private var preparationId: Long? = null
    private val downloadingModels = mutableSetOf<String>()
    private val modelFailedAtMs = mutableMapOf<String, Long>()
    private var stillWorkingHint: Job? = null
    private var lastSaved: MainState? = null

    val analyzer = TextFrameAnalyzer(
        recognizer = recognizer,
        intervalMs = { config.analysisIntervalMs },
        isPaused = { analysisPaused },
        onFrame = ::onFrame,
    )

    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private val _commands = Channel<UiCommand>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    init {
        syncUi()
    }

    // --- Cykl życia i uprawnienia ---

    fun onStart(hasCameraPermission: Boolean) {
        val screen = controller.state.screen
        when {
            !hasCameraPermission && screen == Screen.Starting -> dispatch(MainEvent.PermissionMissing)
            hasCameraPermission && (screen == Screen.NeedsPermission || screen == Screen.PermissionDenied) ->
                dispatch(MainEvent.PermissionGranted)
            else -> maybeCompleteStartup()
        }
    }

    fun onStop() = dispatch(MainEvent.AppStopped)

    fun onPermissionResult(granted: Boolean) =
        dispatch(if (granted) MainEvent.PermissionGranted else MainEvent.PermissionRefused)

    fun onCameraBound(success: Boolean) {
        cameraReady = success
        _ui.update { it.copy(hasFlash = camera.hasFlash) }
        if (success) maybeCompleteStartup() else dispatch(MainEvent.StartupFailed(ErrorKind.CAMERA_UNAVAILABLE))
    }

    fun onCameraUnbound() {
        cameraReady = false
        _ui.update { it.copy(blocks = emptyList()) }
    }

    private fun onSpeechReady(ready: Boolean) {
        speechReady = ready
        if (ready) maybeCompleteStartup() else dispatch(MainEvent.StartupFailed(ErrorKind.NO_SPEECH_ENGINE))
    }

    private fun maybeCompleteStartup() {
        if (controller.state.screen == Screen.Starting && cameraReady && speechReady == true) {
            dispatch(MainEvent.StartupComplete)
        }
    }

    // --- Działania użytkownika ---

    fun onScreenTapped() = execute(controller.onScreenTapped(now()))
    fun onRepeat() = dispatch(MainEvent.RepeatPressed)
    fun onTranslateToggled() = dispatch(MainEvent.TranslateToggled)
    fun onSlower() = dispatch(MainEvent.SlowerPressed)
    fun onFaster() = dispatch(MainEvent.FasterPressed)
    fun onTorchToggled() = dispatch(MainEvent.TorchToggled)
    fun onRetry() = dispatch(MainEvent.RetryPressed)
    fun setAutoRead(enabled: Boolean) = dispatch(MainEvent.AutoReadChanged(enabled))
    fun setAutoTorch(enabled: Boolean) = dispatch(MainEvent.AutoTorchChanged(enabled))
    fun setTargetLanguage(language: LanguageTag) = dispatch(MainEvent.TargetLanguageChanged(language))

    // --- Modele tłumaczeń (ekran Ustawienia) ---

    private val _downloadedModels = MutableStateFlow<List<LanguageTag>>(emptyList())
    val downloadedModels: StateFlow<List<LanguageTag>> = _downloadedModels.asStateFlow()

    fun supportedLanguages(): List<LanguageTag> = translation.supportedLanguages()

    fun refreshDownloadedModels() {
        viewModelScope.launch {
            _downloadedModels.value = try {
                translation.downloadedLanguages()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cannot list models", e)
                emptyList()
            }
        }
    }

    fun deleteModel(language: LanguageTag) {
        viewModelScope.launch {
            try {
                translation.delete(language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cannot delete model $language", e)
            }
            refreshDownloadedModels()
        }
    }

    /** Zmiana parametrów auto-odczytu z ekranu Diagnostyka; stan ekranu zostaje, pamięć przeczytanych – nie. */
    fun updateConfig(newConfig: AutoReadConfig) {
        config = newConfig
        settings.saveConfig(newConfig)
        controller = MainController(newConfig, controller.state)
        pipeline = newPipeline()
    }

    // --- Zdarzenia i efekty ---

    private fun onFrame(frame: OcrFrame) {
        if (analysisPaused) return
        diagnostics.frame(now())
        _ui.update { it.copy(blocks = frame.blocks.map { block -> block.box }) }
        execute(controller.onFrame(frame, now()))
    }

    private fun dispatch(event: MainEvent) = execute(controller.dispatch(event, now()))

    private fun execute(effects: List<MainEffect>) {
        var announcedInBatch = false
        for (effect in effects) {
            when (effect) {
                MainEffect.RequestCameraPermission -> _commands.trySend(UiCommand.RequestCameraPermission)
                MainEffect.OpenAppSettings -> _commands.trySend(UiCommand.OpenAppSettings)
                MainEffect.RestartCamera -> _ui.update { it.copy(cameraGeneration = it.cameraGeneration + 1) }
                is MainEffect.SetAnalysisPaused -> {
                    analysisPaused = effect.paused
                    if (effect.paused) _ui.update { it.copy(blocks = emptyList()) }
                }
                is MainEffect.PrepareReading -> prepare(effect)
                is MainEffect.CancelPreparation -> if (preparationId == effect.requestId) {
                    preparation?.cancel()
                    stillWorkingHint?.cancel()
                    diagnostics.failed(effect.requestId, "anulowano")
                }
                is MainEffect.Speak -> speech.speak(
                    requestId = effect.requestId,
                    chunks = effect.chunks,
                    language = effect.language,
                    flush = !announcedInBatch,
                    onStart = { diagnostics.speechStarted(effect.requestId, now()) },
                    onDone = { dispatch(MainEvent.SpeechFinished(effect.requestId)) },
                )
                MainEffect.StopSpeech -> speech.stop()
                is MainEffect.SetSpeechRate -> speech.rate = effect.rate
                is MainEffect.Announce -> {
                    announce(getApplication<Application>().messageText(effect.message))
                    announcedInBatch = true
                }
                is MainEffect.Vibrate -> haptics.vibrate(effect.kind)
                MainEffect.PlayStartSignal -> startSignal.play()
                is MainEffect.SetTorch -> camera.setTorch(effect.on)
                is MainEffect.EnsureTranslationModel -> ensureModel(effect.language)
                is MainEffect.MarkRead -> Unit // obsługuje MainController
            }
        }
        syncUi()
    }

    private fun prepare(effect: MainEffect.PrepareReading) {
        preparation?.cancel()
        val id = effect.requestId
        preparationId = id
        diagnostics.started(id, effect.request.mode, now())
        stillWorkingHint?.cancel()
        stillWorkingHint = viewModelScope.launch {
            delay(config.stillWorkingHintMs)
            if (preparationId == id && preparation?.isActive == true) announce(getApplication<Application>().messageText(Message.StillWorking))
        }
        preparation = viewModelScope.launch {
            try {
                val reading = pipeline.prepare(
                    request = effect.request,
                    capturePhoto = { camera.takePhotoFrame(recognizer).also { diagnostics.photoDone(id, now()) } },
                    selectFromPhoto = { photo, forced -> controller.selectFromPhoto(photo, forced) },
                )
                stillWorkingHint?.cancel()
                diagnostics.prepared(id, reading, now())
                dispatch(if (reading != null) MainEvent.ReadingPrepared(id, reading) else MainEvent.NothingRecognized(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Preparing reading failed", e)
                diagnostics.failed(id, e.javaClass.simpleName)
                dispatch(MainEvent.PreparationFailed(id))
            }
        }
    }

    private fun ensureModel(language: LanguageTag) {
        val code = language.primary.code
        val lastFailure = modelFailedAtMs[code]
        if (lastFailure != null && now() - lastFailure < config.modelRetryMs) return
        if (!translation.isSupported(language) || !downloadingModels.add(code)) return
        viewModelScope.launch {
            try {
                if (!translation.isDownloaded(language)) {
                    dispatch(MainEvent.TranslationModelDownloadStarted(language))
                    translation.download(language)
                    dispatch(MainEvent.TranslationModelReady(language))
                    refreshDownloadedModels()
                }
                modelFailedAtMs.remove(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Model download failed: $language", e)
                modelFailedAtMs[code] = now()
                dispatch(MainEvent.TranslationModelFailed(language))
            } finally {
                downloadingModels.remove(code)
            }
        }
    }

    /** Przy włączonym TalkBack komunikat wypowiada TalkBack (liveRegion), inaczej nasz TTS – bez podwójnej mowy. */
    private fun announce(text: String) {
        _ui.update { it.copy(message = text) }
        if (accessibility?.isTouchExplorationEnabled != true) speech.announce(text)
    }

    private fun syncUi() {
        val state = controller.state
        _ui.update {
            it.copy(
                screen = state.screen,
                translate = state.translate,
                targetLanguage = state.targetLanguage,
                autoRead = state.autoRead,
                autoTorch = state.autoTorch,
                speechRate = state.speechRate,
                torchOn = state.torchOn,
                lastSpokenText = state.lastSpokenText,
            )
        }
        saveIfChanged(state)
    }

    private fun saveIfChanged(state: MainState) {
        val previous = lastSaved
        if (previous != null && previous.translate == state.translate && previous.speechRate == state.speechRate &&
            previous.targetLanguage == state.targetLanguage &&
            previous.autoRead == state.autoRead && previous.autoTorch == state.autoTorch &&
            previous.firstLaunch == state.firstLaunch
        ) {
            return
        }
        settings.save(state)
        lastSaved = state
    }

    private fun newPipeline() = ReadPipeline(
        config = config,
        languageIdentifier = languageIdentifier,
        translator = translation,
        models = translation,
        voices = speech,
        deviceLanguage = LanguageTag(Locale.getDefault().language),
    )

    private fun now() = SystemClock.elapsedRealtime()

    /** F10: domyślnie język telefonu, jeśli ML Kit umie na niego tłumaczyć; inaczej angielski. */
    private fun defaultTargetLanguage(): LanguageTag {
        val device = LanguageTag(Locale.getDefault().language)
        return if (translation.isSupported(device)) device.primary else LanguageTag.ENGLISH
    }

    override fun onCleared() {
        preparation?.cancel()
        speech.shutdown()
        startSignal.release()
        recognizer.close()
        languageIdentifier.close()
        translation.close()
        camera.shutdown()
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
