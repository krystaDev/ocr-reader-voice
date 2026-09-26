package pl.czytnik.app.main

import android.content.Context
import pl.czytnik.app.R
import pl.czytnik.core.model.LanguageTag
import pl.czytnik.core.model.Message
import java.util.Locale

/** Treść komunikatów w języku interfejsu. */
fun Context.messageText(message: Message): String = when (message) {
    Message.WelcomeFirstLaunch -> getString(R.string.msg_welcome_first)
    Message.Welcome -> getString(R.string.msg_welcome)
    Message.PermissionExplanation -> getString(R.string.msg_permission_explanation)
    Message.PermissionDenied -> getString(R.string.msg_permission_denied)
    Message.NoText -> getString(R.string.msg_no_text)
    Message.HoldStill -> getString(R.string.msg_hold_still)
    Message.MoveAway -> getString(R.string.msg_move_away)
    Message.Stopped -> getString(R.string.msg_stopped)
    Message.NothingReadYet -> getString(R.string.msg_nothing_read_yet)
    Message.ReadingFailed -> getString(R.string.msg_reading_failed)
    Message.TranslationOn -> getString(R.string.msg_translation_on)
    Message.TranslationOff -> getString(R.string.msg_translation_off)
    is Message.DownloadingTranslation -> getString(R.string.msg_downloading_translation, languageName(message.language))
    Message.TranslationReady -> getString(R.string.msg_translation_ready)
    Message.NoInternetReadingOriginal -> getString(R.string.msg_no_internet)
    Message.TranslationFailedReadingOriginal -> getString(R.string.msg_translation_failed)
    is Message.ReadingOriginalWhileDownloading -> getString(R.string.msg_reading_original_downloading, languageName(message.language))
    is Message.NoVoice -> getString(R.string.msg_no_voice, languageName(message.language))
    Message.TorchOn -> getString(R.string.msg_torch_on)
    Message.TorchOff -> getString(R.string.msg_torch_off)
    Message.AutoTorchOn -> getString(R.string.msg_auto_torch_on)
    is Message.SpeechRate -> getString(R.string.msg_speech_rate, formatRate(message.rate))
    Message.CameraUnavailable -> getString(R.string.msg_camera_unavailable)
    Message.NoSpeechEngine -> getString(R.string.msg_no_speech_engine)
}

fun languageName(language: LanguageTag): String =
    Locale.forLanguageTag(language.code).getDisplayLanguage(Locale.getDefault()).ifEmpty { language.code }

fun formatRate(rate: Double): String = String.format(Locale.getDefault(), "%.2f", rate).trimEnd('0').trimEnd(',', '.')
