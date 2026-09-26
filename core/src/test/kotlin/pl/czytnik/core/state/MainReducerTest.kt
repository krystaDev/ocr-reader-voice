package pl.czytnik.core.state

import pl.czytnik.core.autoread.Guidance
import pl.czytnik.core.block
import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.HapticKind
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.Message
import pl.czytnik.core.model.SpeechChunk
import pl.czytnik.core.pipeline.PreparedReading
import pl.czytnik.core.pipeline.ReadMode
import pl.czytnik.core.pipeline.ReadRequest
import pl.czytnik.core.pipeline.ReadSource
import pl.czytnik.core.state.MainEffect.Announce
import pl.czytnik.core.state.MainEvent.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MainReducerTest {

    private val reducer = MainReducer(AutoReadConfig())
    private val bubble = block("WHO ARE YOU?")
    private val scanning = MainState(screen = Screen.Scanning, firstLaunch = false)
    private val source = ReadSource(listOf("WHO ARE YOU?"))

    private fun MainState.on(event: MainEvent) = reducer.reduce(this, event)

    private fun processing(mode: ReadMode = ReadMode.AUTO, id: Long = 7) = scanning.copy(
        screen = Screen.Processing(id, ReadRequest(mode, blocks = listOf(bubble), translateTo = LanguageTag.POLISH)),
        nextRequestId = id + 1,
    )

    private fun speaking(id: Long = 7) = scanning.copy(screen = Screen.Speaking(id), lastSource = source, nextRequestId = id + 1)

    private fun reading(spoken: String = "[pl] Who are you?") = PreparedReading(
        source = source,
        sourceLanguage = LanguageTag.ENGLISH,
        speechLanguage = LanguageTag.POLISH,
        originalText = "WHO ARE YOU?",
        spokenText = spoken,
        chunks = listOf(SpeechChunk(spoken)),
    )

    // --- Start i uprawnienia ---

    @Test
    fun `missing permission is explained and requested`() {
        val t = MainState().on(PermissionMissing)
        assertEquals(Screen.NeedsPermission, t.state.screen)
        assertEquals(listOf(Announce(Message.PermissionExplanation), MainEffect.RequestCameraPermission), t.effects)
    }

    @Test
    fun `refused permission shows the settings button, tap opens settings`() {
        val denied = MainState(screen = Screen.NeedsPermission).on(PermissionRefused).state
        assertEquals(Screen.PermissionDenied, denied.screen)
        assertEquals(listOf(MainEffect.OpenAppSettings), denied.on(ScreenTapped(emptyList())).effects)
        assertEquals(Screen.Starting, denied.on(PermissionGranted).state.screen)
    }

    @Test
    fun `first startup welcomes and downloads the translation model`() {
        val t = MainState().on(StartupComplete)
        assertEquals(Screen.Scanning, t.state.screen)
        assertTrue(Announce(Message.WelcomeFirstLaunch) in t.effects)
        assertTrue(MainEffect.EnsureTranslationModel(LanguageTag.POLISH) in t.effects)
        assertTrue(MainEffect.SetAnalysisPaused(false) in t.effects)
        assertEquals(false, t.state.firstLaunch)
        assertTrue(Announce(Message.Welcome) in t.state.copy(screen = Screen.Starting).on(StartupComplete).effects)
    }

    @Test
    fun `startup failure shows an error, tap retries`() {
        val error = MainState().on(StartupFailed(ErrorKind.CAMERA_UNAVAILABLE))
        assertEquals(Screen.Error(ErrorKind.CAMERA_UNAVAILABLE), error.state.screen)
        assertTrue(MainEffect.Vibrate(HapticKind.ERROR) in error.effects)
        val retry = error.state.on(ScreenTapped(emptyList()))
        assertEquals(Screen.Starting, retry.state.screen)
        assertEquals(listOf(MainEffect.RestartCamera), retry.effects)
    }

    // --- Skanowanie ---

    @Test
    fun `stable text starts reading with signal and paused analysis`() {
        val t = scanning.on(TextStable(listOf(bubble)))
        val screen = assertIs<Screen.Processing>(t.state.screen)
        assertEquals(ReadMode.AUTO, screen.request.mode)
        assertEquals(LanguageTag.POLISH, screen.request.translateTo)
        assertEquals(
            listOf(
                MainEffect.Vibrate(HapticKind.DOUBLE),
                MainEffect.PlayStartSignal,
                MainEffect.SetAnalysisPaused(true),
                MainEffect.PrepareReading(screen.requestId, screen.request),
            ),
            t.effects,
        )
    }

    @Test
    fun `stable text is ignored in manual mode and outside scanning`() {
        assertEquals(Transition(scanning.copy(autoRead = false)), scanning.copy(autoRead = false).on(TextStable(listOf(bubble))))
        assertEquals(Transition(speaking()), speaking().on(TextStable(listOf(bubble))))
    }

    @Test
    fun `tap while scanning forces reading`() {
        val t = scanning.on(ScreenTapped(listOf(bubble)))
        val screen = assertIs<Screen.Processing>(t.state.screen)
        assertEquals(ReadMode.FORCED, screen.request.mode)
        assertEquals(true, screen.request.captureHighRes)
    }

    @Test
    fun `repeat without text says nothing was read`() {
        val t = scanning.on(RepeatPressed)
        assertEquals(Screen.Scanning, t.state.screen)
        assertEquals(listOf<MainEffect>(Announce(Message.NothingReadYet)), t.effects)
    }

    @Test
    fun `repeat reads the last text again`() {
        val t = scanning.copy(lastSource = source).on(RepeatPressed)
        val screen = assertIs<Screen.Processing>(t.state.screen)
        assertEquals(ReadRequest(ReadMode.REPEAT, source = source, translateTo = LanguageTag.POLISH), screen.request)
    }

    @Test
    fun `translate toggle without text announces the new state`() {
        val off = scanning.on(TranslateToggled)
        assertEquals(false, off.state.translate)
        assertEquals(listOf<MainEffect>(Announce(Message.TranslationOff)), off.effects)
        val on = off.state.on(TranslateToggled)
        assertEquals(listOf(MainEffect.EnsureTranslationModel(LanguageTag.POLISH), Announce(Message.TranslationOn)), on.effects)
    }

    @Test
    fun `translate toggle after reading re-reads the same text in the new mode`() {
        val t = scanning.copy(lastSource = source).on(TranslateToggled)
        val screen = assertIs<Screen.Processing>(t.state.screen)
        assertEquals(ReadRequest(ReadMode.REPEAT, source = source, translateTo = null), screen.request)
    }

    @Test
    fun `speech rate changes in steps within limits and plays a sample only when idle`() {
        val faster = scanning.on(FasterPressed)
        assertEquals(1.25, faster.state.speechRate)
        assertEquals(listOf(MainEffect.SetSpeechRate(1.25), Announce(Message.SpeechRate(1.25))), faster.effects)
        var state = scanning
        repeat(10) { state = state.on(SlowerPressed).state }
        assertEquals(0.5, state.speechRate)
        assertEquals(listOf<MainEffect>(MainEffect.SetSpeechRate(1.25)), speaking().on(FasterPressed).effects)
    }

    @Test
    fun `manual torch off blocks automatic torch for the session`() {
        val on = scanning.on(TorchToggled)
        assertEquals(listOf(MainEffect.SetTorch(true), Announce(Message.TorchOn)), on.effects)
        val off = on.state.on(TorchToggled).state
        assertEquals(true, off.autoTorchBlocked)
        assertEquals(Transition(off), off.on(GuidanceIssued(Guidance.LOW_LIGHT)))
    }

    @Test
    fun `low light turns the torch on automatically unless disabled`() {
        val t = scanning.on(GuidanceIssued(Guidance.LOW_LIGHT))
        assertEquals(true, t.state.torchOn)
        assertEquals(listOf(MainEffect.SetTorch(true), Announce(Message.AutoTorchOn)), t.effects)
        assertEquals(Transition(scanning.copy(autoTorch = false)), scanning.copy(autoTorch = false).on(GuidanceIssued(Guidance.LOW_LIGHT)))
    }

    @Test
    fun `guidance is voiced only while scanning`() {
        assertEquals(listOf<MainEffect>(Announce(Message.MoveAway)), scanning.on(GuidanceIssued(Guidance.MOVE_AWAY)).effects)
        assertEquals(listOf<MainEffect>(MainEffect.Vibrate(HapticKind.TICK)), scanning.on(GuidanceIssued(Guidance.TEXT_APPEARED)).effects)
        assertTrue(speaking().on(GuidanceIssued(Guidance.NO_TEXT)).effects.isEmpty())
    }

    // --- Przetwarzanie ---

    @Test
    fun `prepared reading is spoken and remembered`() {
        val t = processing().on(ReadingPrepared(7, reading().copy(notices = listOf(Message.NoVoice(LanguageTag.POLISH)))))
        assertEquals(Screen.Speaking(7), t.state.screen)
        assertEquals(source, t.state.lastSource)
        assertEquals("[pl] Who are you?", t.state.lastSpokenText)
        assertEquals(
            listOf(
                MainEffect.MarkRead(listOf(bubble)),
                Announce(Message.NoVoice(LanguageTag.POLISH)),
                MainEffect.Speak(7, listOf(SpeechChunk("[pl] Who are you?")), LanguageTag.POLISH, 1.0),
            ),
            t.effects,
        )
    }

    @Test
    fun `missing models from the pipeline are downloaded`() {
        val t = processing().on(ReadingPrepared(7, reading().copy(missingModels = listOf(LanguageTag("de")))))
        assertTrue(MainEffect.EnsureTranslationModel(LanguageTag("de")) in t.effects)
    }

    @Test
    fun `stale results are ignored`() {
        assertEquals(Transition(processing()), processing().on(ReadingPrepared(3, reading())))
        assertEquals(Transition(speaking()), speaking().on(SpeechFinished(3)))
    }

    @Test
    fun `tap while processing cancels and marks the text as read`() {
        val t = processing().on(ScreenTapped(emptyList()))
        assertEquals(Screen.Scanning, t.state.screen)
        assertEquals(
            listOf(
                MainEffect.CancelPreparation(7),
                MainEffect.MarkRead(listOf(bubble)),
                Announce(Message.Stopped),
                MainEffect.SetAnalysisPaused(false),
            ),
            t.effects,
        )
    }

    @Test
    fun `nothing recognized is silent for auto reading and announced for forced reading`() {
        assertEquals(
            listOf(MainEffect.MarkRead(listOf(bubble)), MainEffect.SetAnalysisPaused(false)),
            processing(ReadMode.AUTO).on(NothingRecognized(7)).effects,
        )
        assertTrue(Announce(Message.NoText) in processing(ReadMode.FORCED).on(NothingRecognized(7)).effects)
        assertTrue(Announce(Message.ReadingFailed) in processing(ReadMode.AUTO).on(PreparationFailed(7)).effects)
    }

    @Test
    fun `translate toggle while processing restarts the request with the new setting`() {
        val t = processing().on(TranslateToggled)
        val screen = assertIs<Screen.Processing>(t.state.screen)
        assertEquals(8, screen.requestId)
        assertEquals(null, screen.request.translateTo)
        assertEquals(listOf(bubble), screen.request.blocks)
        assertEquals(MainEffect.CancelPreparation(7), t.effects.first())
    }

    // --- Czytanie ---

    @Test
    fun `tap while speaking stops and resumes scanning`() {
        val t = speaking().on(ScreenTapped(listOf(bubble)))
        assertEquals(Screen.Scanning, t.state.screen)
        assertEquals(listOf(MainEffect.StopSpeech, MainEffect.SetAnalysisPaused(false)), t.effects)
    }

    @Test
    fun `speech finished resumes scanning`() {
        val t = speaking().on(SpeechFinished(7))
        assertEquals(Screen.Scanning, t.state.screen)
        assertEquals(listOf<MainEffect>(MainEffect.SetAnalysisPaused(false)), t.effects)
    }

    @Test
    fun `repeat while speaking restarts the last text`() {
        val t = speaking().on(RepeatPressed)
        assertEquals(MainEffect.StopSpeech, t.effects.first())
        assertEquals(ReadMode.REPEAT, assertIs<Screen.Processing>(t.state.screen).request.mode)
    }

    @Test
    fun `translate toggle while speaking re-reads in the new mode`() {
        val t = speaking().on(TranslateToggled)
        assertEquals(MainEffect.StopSpeech, t.effects.first())
        assertEquals(null, assertIs<Screen.Processing>(t.state.screen).request.translateTo)
    }

    // --- Cykl życia ---

    @Test
    fun `going to background stops everything`() {
        val t = speaking().copy(torchOn = true).on(AppStopped)
        assertEquals(Screen.Starting, t.state.screen)
        assertEquals(false, t.state.torchOn)
        assertEquals(listOf(MainEffect.StopSpeech, MainEffect.SetAnalysisPaused(true)), t.effects)
        assertEquals(MainEffect.CancelPreparation(7), processing().on(AppStopped).effects.first())
    }
}
