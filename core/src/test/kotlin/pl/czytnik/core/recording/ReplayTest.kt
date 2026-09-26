package pl.czytnik.core.recording

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import pl.czytnik.core.block
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.text.TextFingerprint
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplayTest {

    @Test
    fun `frames survive encoding and decoding`() {
        val frame = RecordedFrame(1050, OcrFrame(listOf(block("WHO ARE\nYOU?", confidence = 0.8)), meanLuma = 42))
        val decoded = FrameRecording.decode(FrameRecording.encode(frame))
        assertEquals(frame, decoded)
    }

    @Test
    fun `empty lines are skipped`() {
        val line = FrameRecording.encode(RecordedFrame(0, OcrFrame(emptyList())))
        assertEquals(2, FrameRecording.decodeAll("$line\n\n$line\n").size)
    }

    /** Każda nagrana sekwencja z `src/test/resources/sequences` jako osobny test. */
    @TestFactory
    fun `recorded sequences are read as expected`(): List<DynamicTest> {
        val dir = File(requireNotNull(javaClass.getResource("/sequences")) { "missing sequences dir" }.toURI())
        val sequences = dir.listFiles { f -> f.name.endsWith(".jsonl") }.orEmpty().sortedBy { it.name }
        assertTrue(sequences.isNotEmpty())
        return sequences.map { file ->
            DynamicTest.dynamicTest(file.nameWithoutExtension) {
                val expectedFile = File(file.parentFile, file.nameWithoutExtension + ".expected.txt")
                val expected = expectedFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                val actual = Replay.autoReads(FrameRecording.decodeAll(file.readText()))
                    .map { it.replace("\n", " / ") }
                assertEquals(expected.size, actual.size, "reads: $actual")
                expected.zip(actual).forEach { (e, a) ->
                    val similarity = TextFingerprint.of(e).dice(TextFingerprint.of(a))
                    assertTrue(similarity >= 0.8, "expected \"$e\", read \"$a\" (similarity $similarity)")
                }
            }
        }
    }
}
