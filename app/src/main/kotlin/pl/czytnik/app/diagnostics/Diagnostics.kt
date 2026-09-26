package pl.czytnik.app.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pl.czytnik.core.pipeline.PreparedReading
import pl.czytnik.core.pipeline.ReadMode

/** Jeden odczyt: czasy kroków (ms od startu odczytu) i teksty – do strojenia. Dane nie opuszczają telefonu. */
data class ReadingLog(
    val requestId: Long,
    val mode: ReadMode,
    val startedAtMs: Long,
    val photoMs: Long? = null,
    val preparedMs: Long? = null,
    val speechStartMs: Long? = null,
    val sourceLanguage: String? = null,
    val speechLanguage: String? = null,
    val originalText: String? = null,
    val spokenText: String? = null,
    val outcome: String? = null,
)

data class DiagnosticsState(
    val appStartMs: Long = 0,
    val firstFrameMs: Long? = null,
    val firstSpeechMs: Long? = null,
    val readings: List<ReadingLog> = emptyList(),
)

/** Pomiary czasów (docs/TECH-SPEC.md, rozdz. 6.2). Wywoływać z wątku głównego. */
class Diagnostics(appStartMs: Long) {

    private val _state = MutableStateFlow(DiagnosticsState(appStartMs = appStartMs))
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    fun frame(nowMs: Long) {
        if (_state.value.firstFrameMs == null) _state.update { it.copy(firstFrameMs = nowMs - it.appStartMs) }
    }

    fun started(requestId: Long, mode: ReadMode, nowMs: Long) = _state.update {
        it.copy(readings = (listOf(ReadingLog(requestId, mode, nowMs)) + it.readings).take(MAX_READINGS))
    }

    fun photoDone(requestId: Long, nowMs: Long) = updateReading(requestId) { it.copy(photoMs = nowMs - it.startedAtMs) }

    fun prepared(requestId: Long, reading: PreparedReading?, nowMs: Long) = updateReading(requestId) {
        it.copy(
            preparedMs = nowMs - it.startedAtMs,
            sourceLanguage = reading?.sourceLanguage?.code,
            speechLanguage = reading?.speechLanguage?.code,
            originalText = reading?.originalText,
            spokenText = reading?.spokenText,
            outcome = if (reading == null) "brak tekstu" else null,
        )
    }

    fun failed(requestId: Long, reason: String) = updateReading(requestId) { it.copy(outcome = reason) }

    fun speechStarted(requestId: Long, nowMs: Long) {
        updateReading(requestId) { it.copy(speechStartMs = nowMs - it.startedAtMs) }
        if (_state.value.firstSpeechMs == null) _state.update { it.copy(firstSpeechMs = nowMs - it.appStartMs) }
    }

    private fun updateReading(requestId: Long, change: (ReadingLog) -> ReadingLog) = _state.update { state ->
        state.copy(readings = state.readings.map { if (it.requestId == requestId) change(it) else it })
    }

    private companion object {
        const val MAX_READINGS = 30
    }
}
