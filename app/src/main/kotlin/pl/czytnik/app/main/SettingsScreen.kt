package pl.czytnik.app.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.czytnik.app.BuildConfig
import pl.czytnik.app.R
import pl.czytnik.app.ui.theme.White
import pl.czytnik.app.ui.theme.Yellow
import pl.czytnik.core.model.LanguageTag

/** Ustawienia (docs/TECH-SPEC.md, rozdz. 5.2): jedna kolumna, każda pozycja ≥ 76 dp. */
@Composable
fun SettingsScreen(
    ui: MainUiState,
    vm: MainViewModel,
    onClose: () -> Unit,
    onChooseLanguage: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val models by vm.downloadedModels.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshDownloadedModels() }
    val on = stringResource(R.string.state_on)
    val off = stringResource(R.string.state_off)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BigButton(stringResource(R.string.button_back), onClose, primary = true, modifier = Modifier.fillMaxWidth())
        SectionTitle(stringResource(R.string.settings_title))

        SettingText(stringResource(R.string.settings_target_language, languageName(ui.targetLanguage)))
        BigButton(stringResource(R.string.button_change_language), onChooseLanguage, modifier = Modifier.fillMaxWidth())

        BigToggle(stringResource(R.string.settings_auto_read), if (ui.autoRead) on else off, ui.autoRead, { vm.setAutoRead(!ui.autoRead) }, Modifier.fillMaxWidth())
        if (!ui.autoRead) SettingText(stringResource(R.string.settings_auto_read_off_hint))
        BigToggle(stringResource(R.string.settings_auto_torch), if (ui.autoTorch) on else off, ui.autoTorch, { vm.setAutoTorch(!ui.autoTorch) }, Modifier.fillMaxWidth())

        val rateState = stringResource(R.string.rate_state, formatRate(ui.speechRate))
        SettingText(rateState)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(stringResource(R.string.button_slower), vm::onSlower, Modifier.weight(1f), stateDescription = rateState)
            BigButton(stringResource(R.string.button_faster), vm::onFaster, Modifier.weight(1f), stateDescription = rateState)
        }

        SectionTitle(stringResource(R.string.settings_voices))
        BigButton(stringResource(R.string.button_install_voices), { context.startSafely(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }, modifier = Modifier.fillMaxWidth())
        BigButton(stringResource(R.string.button_tts_settings), { context.startSafely(Intent("com.android.settings.TTS_SETTINGS")) }, modifier = Modifier.fillMaxWidth())

        SectionTitle(stringResource(R.string.settings_models))
        val removable = models.filterNot { it.sameLanguageAs(LanguageTag.ENGLISH) }
        if (removable.isEmpty()) SettingText(stringResource(R.string.settings_no_models))
        removable.forEach { language ->
            BigButton(
                stringResource(R.string.button_delete_model, languageName(language)),
                { vm.deleteModel(language) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionTitle(stringResource(R.string.settings_about))
        SettingText(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
        BigButton(stringResource(R.string.button_diagnostics), onOpenDiagnostics, modifier = Modifier.fillMaxWidth())
    }
}

/** Lista języków docelowych (ML Kit Translation), posortowana po nazwie w języku interfejsu. */
@Composable
fun LanguagePickerScreen(ui: MainUiState, vm: MainViewModel, onClose: () -> Unit) {
    val languages = remember { vm.supportedLanguages().sortedBy { languageName(it).lowercase() } }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { BigButton(stringResource(R.string.button_back), onClose, primary = true, modifier = Modifier.fillMaxWidth()) }
        item { SectionTitle(stringResource(R.string.settings_choose_language)) }
        items(languages, key = { it.code }) { language ->
            val selected = language.sameLanguageAs(ui.targetLanguage)
            BigToggle(
                label = languageName(language).replaceFirstChar { it.titlecase() },
                stateText = stringResource(if (selected) R.string.state_selected else R.string.state_not_selected),
                checked = selected,
                onToggle = {
                    vm.setTargetLanguage(language)
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, color = Yellow, modifier = Modifier.semantics { heading() })

@Composable
private fun SettingText(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = White)

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Brak ekranu w tym telefonie – nic nie robimy.
    }
}
