package pl.czytnik.app.language

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.tasks.await
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.ports.LanguageGuess
import pl.czytnik.core.ports.LanguageIdentifier

/** ML Kit Language Identification; zwraca najbardziej prawdopodobny język z jego pewnością. */
class MlKitLanguageIdentifier : LanguageIdentifier {

    private val client = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.01f).build(),
    )

    override suspend fun identify(text: String): LanguageGuess {
        val best = client.identifyPossibleLanguages(text).await().maxByOrNull { it.confidence }
            ?: return LanguageGuess(LanguageTag.UNDETERMINED, 0.0)
        return LanguageGuess(LanguageTag(best.languageTag), best.confidence.toDouble())
    }

    fun close() = client.close()
}
