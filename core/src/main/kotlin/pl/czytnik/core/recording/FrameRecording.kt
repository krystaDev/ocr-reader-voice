package pl.czytnik.core.recording

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.czytnik.core.model.Box
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.model.TextBlock
import pl.czytnik.core.model.TextLine

/**
 * Nagranie klatek analizy do strojenia auto-odczytu (docs/TECH-SPEC.md, rozdz. 6.3): tylko tekst i ramki, bez obrazów.
 * Format: JSON Lines – jedna klatka w linii, np.
 * `{"t":1050,"luma":140,"blocks":[{"lines":[{"text":"WHO ARE YOU?","box":[0.1,0.2,0.8,0.3],"conf":0.9}]}]}`.
 */
object FrameRecording {

    @Serializable
    private data class LineDto(val text: String, val box: List<Double>, val conf: Double? = null)

    @Serializable
    private data class BlockDto(val lines: List<LineDto>)

    @Serializable
    private data class FrameDto(val t: Long, val luma: Int? = null, val blocks: List<BlockDto>)

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(frame: RecordedFrame): String = json.encodeToString(
        FrameDto.serializer(),
        FrameDto(
            t = frame.timeMs,
            luma = frame.frame.meanLuma,
            blocks = frame.frame.blocks.map { block ->
                BlockDto(block.lines.map { LineDto(it.text, listOf(it.box.left, it.box.top, it.box.right, it.box.bottom), it.confidence) })
            },
        ),
    )

    fun decode(line: String): RecordedFrame {
        val dto = json.decodeFromString(FrameDto.serializer(), line)
        val blocks = dto.blocks.mapNotNull { block ->
            val lines = block.lines.map { l ->
                require(l.box.size == 4) { "box must have 4 numbers" }
                TextLine(l.text, Box(l.box[0], l.box[1], l.box[2], l.box[3]), l.conf)
            }
            if (lines.isEmpty()) null else TextBlock.of(lines)
        }
        return RecordedFrame(dto.t, OcrFrame(blocks, dto.luma))
    }

    fun decodeAll(text: String): List<RecordedFrame> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map(::decode).toList()
}

/** Klatka z czasem względem początku nagrania. */
data class RecordedFrame(val timeMs: Long, val frame: OcrFrame)
