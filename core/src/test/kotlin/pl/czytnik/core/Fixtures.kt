package pl.czytnik.core

import pl.czytnik.core.model.Box
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.TextBlock
import pl.czytnik.core.model.TextLine
import pl.czytnik.core.ports.LanguageGuess
import pl.czytnik.core.ports.LanguageIdentifier
import pl.czytnik.core.ports.TranslationModels
import pl.czytnik.core.ports.Translator
import pl.czytnik.core.ports.VoiceCatalog

/** Blok o podanym tekście (linie rozdzielone `\n`) w prostokącie; linie ułożone równo w pionie. */
fun block(
    text: String,
    left: Double = 0.2,
    top: Double = 0.3,
    right: Double = 0.8,
    bottom: Double = 0.5,
    confidence: Double? = null,
): TextBlock {
    val lines = text.split("\n")
    val lineHeight = (bottom - top) / lines.size
    return TextBlock(
        lines = lines.mapIndexed { i, line ->
            TextLine(line, Box(left, top + i * lineHeight, right, top + (i + 1) * lineHeight), confidence)
        },
        box = Box(left, top, right, bottom),
    )
}

class FakeLanguageIdentifier(
    var result: LanguageGuess = LanguageGuess(LanguageTag.ENGLISH, 0.9),
) : LanguageIdentifier {
    override suspend fun identify(text: String): LanguageGuess = result
}

class FakeTranslator(var failing: Boolean = false) : Translator {
    val calls = mutableListOf<String>()

    override suspend fun translate(text: String, from: LanguageTag, to: LanguageTag): String {
        calls += text
        if (failing) error("translation failed")
        return "[$to] $text"
    }
}

class FakeTranslationModels(
    val supported: Set<String> = setOf("en", "pl", "de"),
    val downloaded: MutableSet<String> = mutableSetOf("en", "pl"),
) : TranslationModels {
    override fun isSupported(language: LanguageTag) = language.primary.code in supported
    override suspend fun isDownloaded(language: LanguageTag) = language.primary.code in downloaded
}

class FakeVoices(val available: Set<String> = setOf("en", "pl")) : VoiceCatalog {
    override fun hasVoice(language: LanguageTag) = language.primary.code in available
}
