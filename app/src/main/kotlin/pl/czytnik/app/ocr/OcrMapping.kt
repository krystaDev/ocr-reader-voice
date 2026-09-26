package pl.czytnik.app.ocr

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import pl.czytnik.core.model.FrameGeometry
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.model.PixelRect
import pl.czytnik.core.model.TextBlock
import pl.czytnik.core.model.TextLine

private fun Rect.toPixelRect() = PixelRect(left, top, right, bottom)

/**
 * Wynik ML Kit → [OcrFrame] we współrzędnych widocznego kadru (0..1).
 *
 * @param bufferWidth szerokość bufora obrazu przed obrotem
 * @param bufferHeight wysokość bufora obrazu przed obrotem
 * @param rotationDegrees obrót bufora do pozycji „prosto” (ML Kit zwraca ramki w tym układzie)
 * @param cropRect obszar widoczny na podglądzie, w układzie bufora (CameraX `ImageProxy.cropRect`)
 */
fun Text.toOcrFrame(
    bufferWidth: Int,
    bufferHeight: Int,
    rotationDegrees: Int,
    cropRect: Rect,
    meanLuma: Int?,
): OcrFrame {
    val crop = FrameGeometry.toUpright(cropRect.toPixelRect(), bufferWidth, bufferHeight, rotationDegrees)
    val blocks = textBlocks.mapNotNull { block ->
        val lines = block.lines.mapNotNull { line ->
            val box = line.boundingBox?.let { FrameGeometry.normalize(it.toPixelRect(), crop) } ?: return@mapNotNull null
            TextLine(line.text, box, line.confidence.toDouble())
        }
        if (lines.isEmpty()) null else TextBlock.of(lines)
    }
    return OcrFrame(blocks, meanLuma)
}
