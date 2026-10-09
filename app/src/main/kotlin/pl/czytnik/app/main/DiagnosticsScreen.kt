package pl.czytnik.app.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.czytnik.app.BuildConfig
import pl.czytnik.app.diagnostics.ReadingLog
import pl.czytnik.app.ui.theme.White
import pl.czytnik.app.ui.theme.Yellow
import pl.czytnik.core.config.AutoReadConfig
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ekran diagnostyczny (etap 2): czasy odczytów, tekst przed i po tłumaczeniu, strojenie parametrów auto-odczytu.
 * Na razie tylko po polsku – służy do testów z Damianem.
 */
@Composable
fun DiagnosticsScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.diagnostics.state.collectAsStateWithLifecycle()
    val recorder by vm.recorder.state.collectAsStateWithLifecycle()
    var config by remember { mutableStateOf(vm.config) }
    fun update(change: (AutoReadConfig) -> AutoReadConfig) {
        val updated = try {
            change(config)
        } catch (_: IllegalArgumentException) {
            return
        }
        config = updated
        vm.updateConfig(updated)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BigButton("WRÓĆ", onClose, primary = true, modifier = Modifier.fillMaxWidth())
        Title("Diagnostyka")
        Info("Wersja ${BuildConfig.VERSION_NAME}")
        Info("Start → pierwsza klatka: ${state.firstFrameMs?.let { "$it ms" } ?: "–"}")
        Info("Start → pierwsza mowa: ${state.firstSpeechMs?.let { "$it ms" } ?: "–"}")

        Title("Nagrywanie klatek")
        Info("Zapisuje tylko rozpoznany tekst i ramki (bez obrazów) – do strojenia auto-odczytu. Klatek: ${recorder.frames}")
        BigToggle("NAGRYWAJ", if (recorder.recording) "WŁ." else "WYŁ.", recorder.recording, { vm.setRecording(!recorder.recording) }, Modifier.fillMaxWidth())
        BigButton("UDOSTĘPNIJ NAGRANIE", vm::shareRecording, modifier = Modifier.fillMaxWidth())
        BigButton("WYCZYŚĆ NAGRANIE", { vm.recorder.clear() }, modifier = Modifier.fillMaxWidth())

        Title("Parametry auto-odczytu")
        Stepper("Czas bezruchu", "${config.stableWindowMs} ms", { update { it.copy(stableWindowMs = it.stableWindowMs - 100) } }, { update { it.copy(stableWindowMs = it.stableWindowMs + 100) } })
        Stepper("Podobieństwo klatek", percent(config.stableSimilarity), { update { it.copy(stableSimilarity = round2(it.stableSimilarity - 0.05)) } }, { update { it.copy(stableSimilarity = round2(it.stableSimilarity + 0.05)) } })
        Stepper("Margines krawędzi", percent(config.edgeMargin), { update { it.copy(edgeMargin = round2(it.edgeMargin - 0.01)) } }, { update { it.copy(edgeMargin = round2(it.edgeMargin + 0.01)) } })
        Stepper("Zapominanie dymka", "${config.leaveMs} ms", { update { it.copy(leaveMs = it.leaveMs - 500) } }, { update { it.copy(leaveMs = it.leaveMs + 500) } })
        Stepper("Min. liter", "${config.minLetters}", { update { it.copy(minLetters = (it.minLetters - 1).coerceAtLeast(1)) } }, { update { it.copy(minLetters = it.minLetters + 1) } })
        BigToggle("Zdjęcie HD", if (config.useHighResCapture) "WŁ." else "WYŁ.", config.useHighResCapture, { update { it.copy(useHighResCapture = !it.useHighResCapture) } }, Modifier.fillMaxWidth())
        BigToggle("WIELKIE → zdania", if (config.normalizeAllCaps) "WŁ." else "WYŁ.", config.normalizeAllCaps, { update { it.copy(normalizeAllCaps = !it.normalizeAllCaps) } }, Modifier.fillMaxWidth())
        BigButton("PRZYWRÓĆ DOMYŚLNE", { update { AutoReadConfig() } }, modifier = Modifier.fillMaxWidth())

        Title("Ostatnie odczyty")
        if (state.readings.isEmpty()) Info("Brak")
        state.readings.forEach { ReadingItem(it) }
    }
}

@Composable
private fun ReadingItem(log: ReadingLog) {
    HorizontalDivider(color = White)
    Info("#${log.requestId} ${log.mode}  ${log.sourceLanguage ?: "?"} → ${log.speechLanguage ?: "?"}" + (log.outcome?.let { "  ($it)" } ?: ""))
    Info("zdjęcie+OCR: ${ms(log.photoMs)}, gotowe: ${ms(log.preparedMs)}, mowa: ${ms(log.speechStartMs)}")
    log.originalText?.let { Info("Oryginał: $it") }
    if (log.spokenText != null && log.spokenText != log.originalText) Info("Czytane: ${log.spokenText}")
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Info("$label: $value")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton("−", onMinus, Modifier.weight(1f), stateDescription = "$label $value")
            BigButton("+", onPlus, Modifier.weight(1f), stateDescription = "$label $value")
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, color = Yellow, modifier = Modifier.semantics { heading() })

@Composable
private fun Info(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = White)

private fun ms(value: Long?) = value?.let { "$it ms" } ?: "–"
private fun percent(value: Double) = "${(value * 100).roundToInt()}%"
private fun round2(value: Double) = String.format(Locale.ROOT, "%.2f", value).toDouble()
