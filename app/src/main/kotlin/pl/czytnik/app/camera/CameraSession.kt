package pl.czytnik.app.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pl.czytnik.app.ocr.toOcrFrame
import pl.czytnik.core.model.OcrFrame
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX (docs/TECH-SPEC.md, rozdz. 4.1): podgląd, analiza ~1280×720 i zdjęcie ~2560×1440 we wspólnym
 * `ViewPort` – analizowany i fotografowany jest dokładnie ten obszar, który widać na podglądzie.
 */
class CameraSession(private val context: Context) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var capture: ImageCapture? = null

    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView, analyzer: ImageAnalysis.Analyzer): Boolean =
        try {
            previewView.awaitLayout()
            val cameraProvider = awaitCameraProvider()
            provider = cameraProvider

            val preview = Preview.Builder()
                .setResolutionSelector(selector(Size(1280, 720)))
                .build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector(Size(1280, 720)))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor, analyzer)
            val imageCapture = ImageCapture.Builder()
                .setResolutionSelector(selector(Size(2560, 1440)))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            capture = imageCapture

            val group = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(analysis)
                .addUseCase(imageCapture)
            previewView.viewPort?.let(group::setViewPort)

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
            false
        }

    fun unbind() {
        provider?.unbindAll()
        camera = null
        capture = null
    }

    fun setTorch(on: Boolean) {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(on)
    }

    /** Zdjęcie wysokiej rozdzielczości + OCR; `null`, gdy aparat nie jest gotowy. */
    suspend fun takePhotoFrame(recognizer: TextRecognizer): OcrFrame? {
        val imageCapture = capture ?: return null
        val image = takePicture(imageCapture)
        try {
            val rotation = image.imageInfo.rotationDegrees
            val crop = image.cropRect
            val bitmap = withContext(Dispatchers.Default) { image.toBitmap() }
            val text = recognizer.process(InputImage.fromBitmap(bitmap, rotation)).await()
            return text.toOcrFrame(bitmap.width, bitmap.height, rotation, crop, meanLuma = null)
        } finally {
            image.close()
        }
    }

    fun shutdown() {
        analysisExecutor.shutdown()
        captureExecutor.shutdown()
    }

    private suspend fun takePicture(imageCapture: ImageCapture): ImageProxy =
        suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        if (continuation.isActive) continuation.resume(image) else image.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                },
            )
        }

    private fun selector(target: Size) = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(ResolutionStrategy(target, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        .build()

    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        continuation.resume(future.get())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    private suspend fun PreviewView.awaitLayout() {
        if (isLaidOut && width > 0 && height > 0) return
        suspendCancellableCoroutine { continuation -> doOnLayout { if (continuation.isActive) continuation.resume(Unit) } }
    }

    private companion object {
        const val TAG = "CameraSession"
    }
}
