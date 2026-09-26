package pl.czytnik.core.model

/** Fragment tekstu do wypowiedzenia; po nim [pauseAfterMs] ciszy. */
data class SpeechChunk(val text: String, val pauseAfterMs: Long = 0)

enum class HapticKind { TICK, DOUBLE, ERROR }
