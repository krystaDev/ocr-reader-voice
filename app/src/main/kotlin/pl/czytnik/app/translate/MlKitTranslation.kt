package pl.czytnik.app.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.ports.TranslationModels
import pl.czytnik.core.ports.Translator
import com.google.mlkit.nl.translate.Translator as MlKitTranslator

/**
 * ML Kit Translation offline (docs/TECH-SPEC.md, rozdz. 4.4). Modele (~30 MB na język) pobierane raz przez
 * [download]; tłumaczenie działa potem bez sieci. Wywoływać z wątku głównego.
 */
class MlKitTranslation : Translator, TranslationModels {

    private val modelManager = RemoteModelManager.getInstance()
    private val translators = mutableMapOf<Pair<String, String>, MlKitTranslator>()

    private fun mlKitCode(language: LanguageTag): String? = TranslateLanguage.fromLanguageTag(language.primary.code)

    override fun isSupported(language: LanguageTag): Boolean = mlKitCode(language) != null

    override suspend fun isDownloaded(language: LanguageTag): Boolean {
        val code = mlKitCode(language) ?: return false
        return modelManager.isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await()
    }

    suspend fun download(language: LanguageTag) {
        val code = requireNotNull(mlKitCode(language)) { "Unsupported language: $language" }
        modelManager.download(TranslateRemoteModel.Builder(code).build(), DownloadConditions.Builder().build()).await()
    }

    override suspend fun translate(text: String, from: LanguageTag, to: LanguageTag): String {
        val source = requireNotNull(mlKitCode(from)) { "Unsupported language: $from" }
        val target = requireNotNull(mlKitCode(to)) { "Unsupported language: $to" }
        val translator = translators.getOrPut(source to target) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
        return translator.translate(text).await()
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
