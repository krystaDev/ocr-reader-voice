package pl.czytnik.core.recording

import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.pipeline.PreparedReading
import pl.czytnik.core.state.MainController
import pl.czytnik.core.state.MainEffect
import pl.czytnik.core.state.MainEvent
import pl.czytnik.core.state.MainState
import pl.czytnik.core.text.Paragraphs

/**
 * Odtwarza nagrane klatki przez [MainController] i zwraca teksty, które zostałyby przeczytane automatycznie.
 *
 * W nagraniu z aplikacji nie ma klatek z czasu czytania (analiza jest wtedy wstrzymana), więc odczyt „trwa” do
 * następnej nagranej klatki. Teksty pochodzą z bloków klatki analizy (bez zdjęcia HD i tłumaczenia).
 */
object Replay {

    fun autoReads(frames: List<RecordedFrame>, config: AutoReadConfig = AutoReadConfig()): List<String> {
        val controller = MainController(config, MainState(translate = false, firstLaunch = false))
        val reads = mutableListOf<String>()
        val start = frames.firstOrNull()?.timeMs ?: return reads
        controller.dispatch(MainEvent.StartupComplete, start)
        for (recorded in frames) {
            val effects = controller.onFrame(recorded.frame, recorded.timeMs)
            val prepare = effects.filterIsInstance<MainEffect.PrepareReading>().firstOrNull() ?: continue
            val paragraphs = Paragraphs.from(prepare.request.blocks)
            reads += paragraphs.joinToString("\n")
            val reading = PreparedReading(
                source = pl.czytnik.core.pipeline.ReadSource(paragraphs),
                sourceLanguage = LanguageTag.UNDETERMINED,
                speechLanguage = LanguageTag.UNDETERMINED,
                originalText = paragraphs.joinToString("\n"),
                spokenText = paragraphs.joinToString("\n"),
                chunks = emptyList(),
            )
            controller.dispatch(MainEvent.ReadingPrepared(prepare.requestId, reading), recorded.timeMs)
            controller.dispatch(MainEvent.SpeechFinished(prepare.requestId), recorded.timeMs)
        }
        return reads
    }
}
