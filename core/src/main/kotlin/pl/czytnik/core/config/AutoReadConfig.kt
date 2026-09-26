package pl.czytnik.core.config

/**
 * Parametry auto-odczytu do strojenia (docs/TECH-SPEC.md, rozdz. 3.5).
 *
 * Czasy w milisekundach, progi podobieństwa w zakresie 0..1, położenia jako ułamek wymiaru kadru.
 */
data class AutoReadConfig(
    val analysisIntervalMs: Long = 350,
    val stableWindowMs: Long = 1000,
    val stableMinFrames: Int = 3,
    val stableSimilarity: Double = 0.75,
    val maxCenterShift: Double = 0.08,
    val minLetters: Int = 3,
    val lineMinConfidence: Double = 0.5,
    val edgeMargin: Double = 0.02,
    val sameBlockSimilarity: Double = 0.75,
    val containment: Double = 0.9,
    val presentSimilarity: Double = 0.5,
    val leaveMs: Long = 2000,
    val memoryMaxEntries: Int = 20,
    val hapticMinIntervalMs: Long = 1500,
    val noTextHintMs: Long = 10_000,
    val noTextRepeatMs: Long = 20_000,
    val noTextMaxHints: Int = 3,
    val unstableHintMs: Long = 4000,
    val edgeHintMs: Long = 2000,
    val useHighResCapture: Boolean = true,
    val normalizeAllCaps: Boolean = true,
    val lowLightLuma: Int = 40,
    val lowLightMs: Long = 2000,
    val languageMinConfidence: Double = 0.5,
    val speechChunkMaxChars: Int = 500,
    val paragraphPauseMs: Long = 350,
    val minSpeechRate: Double = 0.5,
    val maxSpeechRate: Double = 2.0,
    val speechRateStep: Double = 0.25,
    val backgroundForgetMs: Long = 60_000,
    val stillWorkingHintMs: Long = 3000,
    val modelRetryMs: Long = 60_000,
) {
    init {
        require(analysisIntervalMs > 0) { "analysisIntervalMs must be positive" }
        require(stableWindowMs >= analysisIntervalMs) { "stableWindowMs must cover at least one analysis interval" }
        require(stableMinFrames >= 1) { "stableMinFrames must be at least 1" }
        for ((name, value) in listOf(
            "stableSimilarity" to stableSimilarity,
            "maxCenterShift" to maxCenterShift,
            "lineMinConfidence" to lineMinConfidence,
            "edgeMargin" to edgeMargin,
            "sameBlockSimilarity" to sameBlockSimilarity,
            "containment" to containment,
            "presentSimilarity" to presentSimilarity,
            "languageMinConfidence" to languageMinConfidence,
        )) {
            require(value in 0.0..1.0) { "$name must be in 0..1, was $value" }
        }
        require(presentSimilarity <= sameBlockSimilarity) {
            "presentSimilarity must not exceed sameBlockSimilarity (hysteresis)"
        }
        require(lowLightLuma in 0..255) { "lowLightLuma must be in 0..255" }
        require(memoryMaxEntries >= 1) { "memoryMaxEntries must be at least 1" }
        require(speechChunkMaxChars >= 50) { "speechChunkMaxChars must be at least 50" }
        require(minSpeechRate in 0.1..maxSpeechRate) { "minSpeechRate must be in 0.1..maxSpeechRate" }
        require(speechRateStep > 0) { "speechRateStep must be positive" }
    }
}
