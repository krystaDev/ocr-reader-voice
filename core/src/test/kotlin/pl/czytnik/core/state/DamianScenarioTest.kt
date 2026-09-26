package pl.czytnik.core.state

import kotlinx.coroutines.test.runTest
import pl.czytnik.core.FakeLanguageIdentifier
import pl.czytnik.core.FakeTranslationModels
import pl.czytnik.core.FakeTranslator
import pl.czytnik.core.FakeVoices
import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.Box
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.model.TextBlock
import pl.czytnik.core.model.TextLine
import pl.czytnik.core.pipeline.ReadPipeline
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Symulacja scenariusza Damiana (docs/TECH-SPEC.md, rozdz. 6.1 i 6.4): telefon przesuwany po stronie komiksu
 * na ekranie tabletu, jeden dymek w kadrze. Każdy dymek ma być przeczytany dokładnie raz, w kolejności.
 */
class DamianScenarioTest {

    /** Dymek na stronie komiksu (współrzędne strony). */
    private data class Bubble(val lines: List<String>, val box: Box) {
        val text: String get() = lines.joinToString(" ")
    }

    private val page = listOf(
        Bubble(listOf("I THOUGHT YOU", "WERE DEAD,", "LOGAN!"), Box(0.05, 0.05, 0.45, 0.25)),
        Bubble(listOf("TAKES MORE THAN", "THAT TO KILL ME,", "BUB."), Box(0.55, 0.05, 0.95, 0.25)),
        Bubble(listOf("THEN WHY ARE YOU", "HIDING IN HERE?"), Box(0.05, 0.60, 0.45, 0.80)),
        Bubble(listOf("I'M NOT HIDING.", "I'M WAITING."), Box(0.55, 0.60, 0.95, 0.80)),
    )

    /** Kadr aparatu: prostokąt strony widziany przez telefon. */
    private val windowWidth = 0.5
    private val windowHeight = 0.4

    private val random = Random(42)

    /** OCR kadru: dymki w całości lub ucięte, z przypadkowymi błędami znaków. */
    private fun ocr(centerX: Double, centerY: Double, noise: Boolean = true): OcrFrame {
        val window = Box(centerX - windowWidth / 2, centerY - windowHeight / 2, centerX + windowWidth / 2, centerY + windowHeight / 2)
        val blocks = page.mapNotNull { bubble -> visiblePart(bubble, window, noise) }
        return OcrFrame(blocks, meanLuma = 150)
    }

    private fun visiblePart(bubble: Bubble, window: Box, noise: Boolean): TextBlock? {
        val left = max(bubble.box.left, window.left)
        val right = min(bubble.box.right, window.right)
        val top = max(bubble.box.top, window.top)
        val bottom = min(bubble.box.bottom, window.bottom)
        if (right - left < 0.02 || bottom - top < 0.02) return null
        val fractionX = (right - left) / bubble.box.width
        val fractionY = (bottom - top) / bubble.box.height
        val visibleLines = bubble.lines.let { lines ->
            val count = ceil(lines.size * fractionY).toInt().coerceIn(1, lines.size)
            if (top > bubble.box.top) lines.takeLast(count) else lines.take(count)
        }.map { line ->
            val chars = (line.length * fractionX).roundToInt().coerceIn(1, line.length)
            if (left > bubble.box.left) line.takeLast(chars) else line.take(chars)
        }.map { if (noise) ocrNoise(it) else it }
        val box = Box(
            (left - window.left) / window.width,
            (top - window.top) / window.height,
            (right - window.left) / window.width,
            (bottom - window.top) / window.height,
        )
        val lineHeight = box.height / visibleLines.size
        return TextBlock(
            visibleLines.mapIndexed { i, text ->
                TextLine(text, Box(box.left, box.top + i * lineHeight, box.right, box.top + (i + 1) * lineHeight), 0.9)
            },
            box,
        )
    }

    /** Co trzecia linia ma jeden błędny znak (O↔0, I↔l…). */
    private fun ocrNoise(line: String): String {
        if (random.nextInt(3) != 0 || line.length < 4) return line
        val i = random.nextInt(line.length)
        val replacement = when (line[i]) {
            'O' -> '0'; 'I' -> 'l'; 'E' -> 'F'; 'S' -> '5'; else -> line[i]
        }
        return line.substring(0, i) + replacement + line.substring(i + 1)
    }

    /** Pozycje środka kadru: [ruch] do kolejnego dymka i [postój]. */
    private sealed interface Step {
        data class Hold(val x: Double, val y: Double, val ms: Long) : Step
        data class Move(val toX: Double, val toY: Double, val ms: Long) : Step
        data object Tap : Step
    }

    private class Result(val spoken: MutableList<String> = mutableListOf(), val announcements: MutableList<Any> = mutableListOf())

    /**
     * Uruchamia kontroler z prawdziwym pipeline’em na fałszywych portach. Przetwarzanie trwa 400 ms, a mowa
     * 60 ms na znak; w tym czasie telefon stoi w miejscu (Damian słucha).
     */
    private suspend fun simulate(steps: List<Step>, translate: Boolean = true): Result {
        val config = AutoReadConfig()
        val controller = MainController(config, MainState(translate = translate, firstLaunch = false))
        val pipeline = ReadPipeline(config, FakeLanguageIdentifier(), FakeTranslator(), FakeTranslationModels(), FakeVoices(), LanguageTag.POLISH)
        val result = Result()
        var now = 0L
        var x = 0.25
        var y = 0.15
        var busyUntil = 0L
        var pending: (suspend () -> Unit)? = null

        suspend fun handle(effects: List<MainEffect>) {
            for (effect in effects) {
                when (effect) {
                    is MainEffect.PrepareReading -> {
                        val prepared = pipeline.prepare(
                            effect.request,
                            capturePhoto = { ocr(x, y) },
                            selectFromPhoto = controller::selectFromPhoto,
                        )
                        busyUntil = now + 400
                        pending = {
                            handle(
                                controller.dispatch(
                                    if (prepared == null) MainEvent.NothingRecognized(effect.requestId)
                                    else MainEvent.ReadingPrepared(effect.requestId, prepared),
                                    now,
                                ),
                            )
                        }
                    }
                    is MainEffect.Speak -> {
                        val text = effect.chunks.joinToString(" ") { it.text }
                        result.spoken += text
                        busyUntil = now + text.length * 60L
                        pending = { handle(controller.dispatch(MainEvent.SpeechFinished(effect.requestId), now)) }
                    }
                    is MainEffect.StopSpeech -> pending = null
                    is MainEffect.Announce -> result.announcements += effect.message
                    else -> Unit
                }
            }
        }

        handle(controller.dispatch(MainEvent.StartupComplete, now))
        suspend fun frame() {
            while (pending != null && now >= busyUntil) {
                val action = pending!!
                pending = null
                action()
            }
            handle(controller.onFrame(ocr(x, y), now))
            now += config.analysisIntervalMs
        }

        for (step in steps) {
            when (step) {
                is Step.Hold -> {
                    x = step.x; y = step.y
                    val end = now + step.ms
                    while (now < end) frame()
                }
                is Step.Move -> {
                    val frames = max(1, (step.ms / config.analysisIntervalMs).toInt())
                    val startX = x; val startY = y
                    for (i in 1..frames) {
                        x = startX + (step.toX - startX) * i / frames
                        y = startY + (step.toY - startY) * i / frames
                        frame()
                    }
                }
                Step.Tap -> handle(controller.onScreenTapped(now))
            }
        }
        // Dokończ trwające czytanie.
        while (pending != null) {
            now = max(now, busyUntil)
            val action = pending!!
            pending = null
            action()
        }
        return result
    }

    private fun centerOf(i: Int) = page[i].box.centerX to page[i].box.centerY

    private fun hold(i: Int, ms: Long = 6000) = centerOf(i).let { (x, y) -> Step.Hold(x, y, ms) }
    private fun move(i: Int, ms: Long = 1400) = centerOf(i).let { (x, y) -> Step.Move(x, y, ms) }

    private fun expected(i: Int, translate: Boolean = true): String {
        val sentence = pl.czytnik.core.text.TextNormalizer.normalizeAllCaps(page[i].text, LanguageTag.ENGLISH)
        return if (translate) "[pl] $sentence" else sentence
    }

    @Test
    fun `each bubble on the page is read exactly once, in order`() = runTest {
        val result = simulate(listOf(hold(0), move(1), hold(1), move(2), hold(2), move(3), hold(3)))
        assertEquals((0..3).map { expected(it) }, result.spoken)
    }

    @Test
    fun `holding the phone still for a long time does not repeat the bubble`() = runTest {
        val result = simulate(listOf(hold(0, ms = 60_000)))
        assertEquals(listOf(expected(0)), result.spoken)
    }

    @Test
    fun `going back to a bubble reads it again`() = runTest {
        val result = simulate(listOf(hold(0), move(1), hold(1), move(0), hold(0)))
        assertEquals(listOf(expected(0), expected(1), expected(0)), result.spoken)
    }

    @Test
    fun `shaking between two bubbles does not repeat them`() = runTest {
        val jitter = (1..6).flatMap { listOf(Step.Move(0.52, 0.15, 350), Step.Move(0.48, 0.15, 350)) }
        val result = simulate(listOf(hold(0)) + jitter + listOf(move(1), hold(1)))
        assertEquals(listOf(expected(0), expected(1)), result.spoken)
    }

    @Test
    fun `tap while speaking stops and the bubble is not read again`() = runTest {
        val (x, y) = centerOf(0)
        val result = simulate(listOf(Step.Hold(x, y, 1800), Step.Tap, Step.Hold(x, y, 8000)))
        assertEquals(listOf(expected(0)), result.spoken)
    }

    @Test
    fun `reading without translation`() = runTest {
        val result = simulate(listOf(hold(0), move(1), hold(1)), translate = false)
        assertEquals(listOf(expected(0, translate = false), expected(1, translate = false)), result.spoken)
    }

    @Test
    fun `no voice hints while reading a page`() = runTest {
        val result = simulate(listOf(hold(0), move(1), hold(1), move(2), hold(2), move(3), hold(3)))
        assertTrue(result.announcements.all { it == pl.czytnik.core.model.Message.Welcome }, result.announcements.toString())
    }
}
