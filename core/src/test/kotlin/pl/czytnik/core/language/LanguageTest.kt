package pl.czytnik.core.language

import kotlinx.coroutines.test.runTest
import pl.czytnik.core.FakeTranslationModels
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.ports.LanguageGuess
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageResolverTest {

    private val resolver = LanguageResolver(minConfidence = 0.5, fallback = LanguageTag("pl-PL"))

    @Test
    fun `confident result is used`() {
        assertEquals(LanguageTag.ENGLISH, resolver.resolve(LanguageGuess(LanguageTag("en-US"), 0.8)))
    }

    @Test
    fun `undetermined without history falls back to device language`() {
        assertEquals(LanguageTag.POLISH, resolver.resolve(LanguageGuess(LanguageTag.UNDETERMINED, 0.0)))
    }

    @Test
    fun `short bubble uses the last confident language of the session`() {
        resolver.resolve(LanguageGuess(LanguageTag.ENGLISH, 0.9))
        assertEquals(LanguageTag.ENGLISH, resolver.resolve(LanguageGuess(LanguageTag.UNDETERMINED, 0.0)))
        assertEquals(LanguageTag.ENGLISH, resolver.resolve(LanguageGuess(LanguageTag("de"), 0.3)))
    }
}

class TranslationPolicyTest {

    private val models = FakeTranslationModels()

    @Test
    fun `translation disabled`() = runTest {
        assertEquals(TranslationDecision.NotNeeded, TranslationPolicy.decide(null, LanguageTag.ENGLISH, models))
    }

    @Test
    fun `same language is not translated`() = runTest {
        assertEquals(TranslationDecision.NotNeeded, TranslationPolicy.decide(LanguageTag.POLISH, LanguageTag("pl-PL"), models))
    }

    @Test
    fun `undetermined or unsupported language is not translated`() = runTest {
        assertEquals(TranslationDecision.NotNeeded, TranslationPolicy.decide(LanguageTag.POLISH, LanguageTag.UNDETERMINED, models))
        assertEquals(TranslationDecision.NotNeeded, TranslationPolicy.decide(LanguageTag.POLISH, LanguageTag("xx"), models))
    }

    @Test
    fun `English to Polish with models on device`() = runTest {
        assertEquals(
            TranslationDecision.Translate(LanguageTag.ENGLISH, LanguageTag.POLISH),
            TranslationPolicy.decide(LanguageTag.POLISH, LanguageTag.ENGLISH, models),
        )
    }

    @Test
    fun `missing model is reported`() = runTest {
        assertEquals(
            TranslationDecision.ModelsMissing(listOf(LanguageTag("de"))),
            TranslationPolicy.decide(LanguageTag.POLISH, LanguageTag("de"), models),
        )
    }
}
