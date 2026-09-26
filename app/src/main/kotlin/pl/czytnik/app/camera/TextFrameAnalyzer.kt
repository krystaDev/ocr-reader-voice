package pl.czytnik.app.camera

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import pl.czytnik.app.ocr.toOcrFrame
import pl.czytnik.core.model.OcrFrame

/**
 * Analiza klatek podglądu: co najwyżej jedna klatka na [intervalMs] trafia do OCR. Wynik ([OcrFrame]) jest
 * dostarczany na wątku głównym (domyślny wykonawca zadań ML Kit).
 */
class TextFrameAnalyzer(
    private val recognizer: TextRecognizer,
    private val intervalMs: () -> Long,
    private val isPaused: () -> Boolean,
    private val onFrame: (OcrFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private var lastAnalysisMs = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val mediaImage = image.image
        if (isPaused() || mediaImage == null || now - lastAnalysisMs < intervalMs()) {
            image.close()
            return
        }
        lastAnalysisMs = now
        val width = image.width
        val height = image.height
        val rotation = image.imageInfo.rotationDegrees
        val crop = image.cropRect
        val luma = meanLuma(image)
        recognizer.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { text -> onFrame(text.toOcrFrame(width, height, rotation, crop, luma)) }
            .addOnCompleteListener { image.close() }
    }

    /** Średnia jasność kanału Y (0..255) z próbki co 16. piksela. */
    private fun meanLuma(image: ImageProxy): Int? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var sum = 0L
        var count = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val index = y * rowStride + x * pixelStride
                if (index < buffer.limit()) {
                    sum += buffer.get(index).toInt() and 0xFF
                    count++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }
        return if (count == 0) null else (sum / count).toInt()
    }

    private companion object {
        const val SAMPLE_STEP = 16
    }
}
