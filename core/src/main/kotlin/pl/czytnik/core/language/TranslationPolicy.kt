package pl.czytnik.core.language

import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.ports.TranslationModels

sealed interface TranslationDecision {
    /** Nie tłumaczymy: tłumaczenie wyłączone, ten sam język, język nierozpoznany lub nieobsługiwany. */
    data object NotNeeded : TranslationDecision

    data class Translate(val from: LanguageTag, val to: LanguageTag) : TranslationDecision

    /** Tłumaczenie potrzebne, ale brakuje modeli – czytamy oryginał i pobieramy [missing]. */
    data class ModelsMissing(val missing: List<LanguageTag>) : TranslationDecision
}

/** Kiedy tłumaczyć (docs/TECH-SPEC.md, rozdz. 4.4). */
object TranslationPolicy {

    suspend fun decide(
        translateTo: LanguageTag?,
        source: LanguageTag,
        models: TranslationModels,
    ): TranslationDecision {
        val target = translateTo?.primary ?: return TranslationDecision.NotNeeded
        val from = source.primary
        if (!from.isDetermined || from.sameLanguageAs(target)) return TranslationDecision.NotNeeded
        if (!models.isSupported(from) || !models.isSupported(target)) return TranslationDecision.NotNeeded
        val missing = listOf(from, target).filterNot { models.isDownloaded(it) }
        return if (missing.isEmpty()) {
            TranslationDecision.Translate(from, target)
        } else {
            TranslationDecision.ModelsMissing(missing)
        }
    }
}
