package pl.czytnik.app.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import pl.czytnik.core.model.OcrFrame
import pl.czytnik.core.recording.FrameRecording
import pl.czytnik.core.recording.RecordedFrame
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecorderState(val recording: Boolean = false, val frames: Int = 0)

/**
 * Nagrywanie klatek analizy do strojenia (docs/TECH-SPEC.md, rozdz. 6.3): tylko tekst i ramki, w pamięci, do
 * [MAX_FRAMES] klatek; udostępniane jako plik JSON Lines. Wywoływać z wątku głównego.
 */
class FrameRecorder {

    private val lines = ArrayList<String>()
    private var startMs = 0L
    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    fun setRecording(on: Boolean, nowMs: Long) {
        if (on && lines.isEmpty()) startMs = nowMs
        _state.value = _state.value.copy(recording = on)
    }

    fun onFrame(frame: OcrFrame, nowMs: Long) {
        if (!_state.value.recording || lines.size >= MAX_FRAMES) return
        lines += FrameRecording.encode(RecordedFrame(nowMs - startMs, frame))
        _state.value = RecorderState(recording = lines.size < MAX_FRAMES, frames = lines.size)
    }

    fun clear() {
        lines.clear()
        _state.value = RecorderState(recording = _state.value.recording, frames = 0)
    }

    /** Zapisuje nagranie do pliku i zwraca intencję „Udostępnij”; `null`, gdy nagranie jest puste. */
    suspend fun shareIntent(context: Context): Intent? {
        if (lines.isEmpty()) return null
        val content = lines.joinToString("\n", postfix = "\n")
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
            File(dir, "czytnik-klatki-$stamp.jsonl").apply { writeText(content) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private companion object {
        /** Ok. 17 minut analizy co 350 ms. */
        const val MAX_FRAMES = 3000
    }
}
