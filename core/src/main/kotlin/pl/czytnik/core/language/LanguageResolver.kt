package pl.czytnik.core.language

import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.ports.LanguageGuess

/**
 * Ustala język tekstu (docs/TECH-SPEC.md, rozdz. 4.3). Krótkie dymki („WHAT?!”) często dają wynik „nieokreślony” –
 * wtedy przyjmujemy ostatni pewnie rozpoznany język z sesji, a gdy go brak – [fallback] (język telefonu).
 */
class LanguageResolver(private val minConfidence: Double, private val fallback: LanguageTag) {

    var lastConfident: LanguageTag? = null
        private set

    fun resolve(guess: LanguageGuess): LanguageTag {
        if (guess.language.isDetermined && guess.confidence >= minConfidence) {
            lastConfident = guess.language.primary
            return guess.language.primary
        }
        return lastConfident ?: fallback.primary
    }
}
