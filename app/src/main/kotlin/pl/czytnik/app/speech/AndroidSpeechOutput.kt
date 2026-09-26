package pl.czytnik.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.SpeechChunk
import pl.czytnik.core.ports.VoiceCatalog
import java.util.Locale

/**
 * Android TextToSpeech (docs/TECH-SPEC.md, rozdz. 4.5). Wszystkie metody i wywołania zwrotne – na wątku głównym.
 *
 * @param onReady `true`, gdy silnik mowy działa; `false`, gdy go brak lub nie wystartował
 */
class AndroidSpeechOutput(context: Context, private val onReady: (Boolean) -> Unit) : VoiceCatalog {

    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Na czas czytania inne aplikacje (np. muzyka) są ściszane. */
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .build()
    private var ready = false
    private val pendingAnnouncements = mutableListOf<String>()

    private var currentRequestId: Long? = null
    private var lastUtteranceId: String? = null
    private var firstUtteranceId: String? = null
    private var onStart: (() -> Unit)? = null
    private var onDone: (() -> Unit)? = null
    private var utteranceCounter = 0L

    var rate: Double = 1.0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post { onInit(status == TextToSpeech.SUCCESS) }
    }

    private fun onInit(success: Boolean) {
        ready = success && tts.engines.isNotEmpty()
        if (ready) {
            tts.setAudioAttributes(audioAttributes)
            tts.setOnUtteranceProgressListener(progressListener)
            pendingAnnouncements.forEach { announce(it) }
        }
        pendingAnnouncements.clear()
        onReady(ready)
    }

    /**
     * Czyta fragmenty tekstu. [flush] – przerwij to, co jest mówione (np. powitanie); inaczej dołącz do kolejki
     * (po komunikatach wypowiadanych przed tekstem).
     */
    fun speak(
        requestId: Long,
        chunks: List<SpeechChunk>,
        language: LanguageTag,
        flush: Boolean,
        onStart: () -> Unit,
        onDone: () -> Unit,
    ) {
        if (!ready || chunks.isEmpty()) {
            main.post(onDone)
            return
        }
        if (flush) tts.stop()
        audioManager?.requestAudioFocus(focusRequest)
        currentRequestId = requestId
        this.onStart = onStart
        this.onDone = onDone
        applyLanguage(language)
        tts.setSpeechRate(rate.toFloat())
        chunks.forEachIndexed { index, chunk ->
            val id = "read-$requestId-$index"
            if (index == 0) firstUtteranceId = id
            if (index == chunks.lastIndex && chunk.pauseAfterMs == 0L) lastUtteranceId = id
            tts.speak(chunk.text, TextToSpeech.QUEUE_ADD, null, id)
            if (chunk.pauseAfterMs > 0) {
                val pauseId = "$id-pause"
                if (index == chunks.lastIndex) lastUtteranceId = pauseId
                tts.playSilentUtterance(chunk.pauseAfterMs, TextToSpeech.QUEUE_ADD, pauseId)
            }
        }
    }

    /** Krótki komunikat w języku interfejsu, dołączany do kolejki. Przed startem silnika – zapamiętany. */
    fun announce(text: String) {
        if (!ready) {
            pendingAnnouncements += text
            return
        }
        applyLanguage(LanguageTag(Locale.getDefault().toLanguageTag()))
        tts.setSpeechRate(rate.toFloat())
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "announce-${utteranceCounter++}")
    }

    fun stop() {
        clearRequest()
        if (ready) tts.stop()
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    override fun hasVoice(language: LanguageTag): Boolean {
        if (!ready) return true
        return tts.isLanguageAvailable(Locale.forLanguageTag(language.code)) >= TextToSpeech.LANG_AVAILABLE
    }

    fun shutdown() {
        clearRequest()
        tts.shutdown()
    }

    private fun clearRequest() {
        currentRequestId = null
        firstUtteranceId = null
        lastUtteranceId = null
        onStart = null
        onDone = null
    }

    /** Głos offline dla języka (preferowany kraj telefonu), a gdy go brak – domyślny głos języka lub telefonu. */
    private fun applyLanguage(language: LanguageTag) {
        val locale = Locale.forLanguageTag(language.code)
        val voice = bestVoice(locale)
        when {
            voice != null -> tts.voice = voice
            tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE -> tts.language = locale
            else -> tts.language = Locale.getDefault()
        }
    }

    private fun bestVoice(locale: Locale): Voice? {
        val voices = try {
            tts.voices
        } catch (e: Exception) {
            Log.w(TAG, "Cannot list voices", e)
            null
        } ?: return null
        val deviceCountry = Locale.getDefault().country.takeIf { Locale.getDefault().language == locale.language }
        return voices
            .filter { it.locale.language == locale.language }
            .filterNot { it.isNetworkConnectionRequired }
            .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features }
            .sortedWith(
                compareByDescending<Voice> { it.locale.country == (locale.country.ifEmpty { deviceCountry }) }
                    .thenByDescending { it.quality },
            )
            .firstOrNull()
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            main.post { if (utteranceId == firstUtteranceId) onStart?.invoke() }
        }

        override fun onDone(utteranceId: String) {
            main.post { finishIfLast(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            main.post { finishIfLast(utteranceId) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            main.post { finishIfLast(utteranceId) }
        }
    }

    private fun finishIfLast(utteranceId: String) {
        if (utteranceId != lastUtteranceId) return
        val done = onDone
        clearRequest()
        audioManager?.abandonAudioFocusRequest(focusRequest)
        done?.invoke()
    }

    private companion object {
        const val TAG = "SpeechOutput"
    }
}
