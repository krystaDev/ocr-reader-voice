package pl.czytnik.app.main

import android.view.View
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import pl.czytnik.app.R
import pl.czytnik.app.ui.theme.White
import pl.czytnik.app.ui.theme.Yellow
import pl.czytnik.core.model.Box as TextBox
import pl.czytnik.core.state.ErrorKind
import pl.czytnik.core.state.Screen

@Composable
fun MainScreen(ui: MainUiState, vm: MainViewModel, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusLine(ui)
        when (val screen = ui.screen) {
            Screen.NeedsPermission -> PermissionContent(denied = false, onClick = vm::onRetry)
            Screen.PermissionDenied -> PermissionContent(denied = true, onClick = vm::onRetry)
            is Screen.Error -> ErrorContent(screen.kind, onRetry = vm::onRetry)
            else -> ReaderContent(ui, vm, onOpenSettings)
        }
    }
}

@Composable
private fun StatusLine(ui: MainUiState) {
    val status = when (ui.screen) {
        Screen.Starting -> stringResource(R.string.status_starting)
        Screen.Scanning -> stringResource(if (ui.blocks.isEmpty()) R.string.status_searching else R.string.status_text_visible)
        is Screen.Processing -> stringResource(R.string.status_processing)
        is Screen.Speaking -> stringResource(R.string.status_speaking)
        Screen.NeedsPermission, Screen.PermissionDenied -> stringResource(R.string.status_permission)
        is Screen.Error -> stringResource(R.string.status_error)
    }
    Text(status, style = MaterialTheme.typography.titleLarge, color = Yellow)
    // Komunikaty: przy TalkBack wypowiada je TalkBack (liveRegion), bez TalkBack – nasz TTS.
    if (ui.message.isNotEmpty()) {
        Text(
            ui.message,
            style = MaterialTheme.typography.bodyMedium,
            color = White,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ReaderContent(
    ui: MainUiState,
    vm: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val previewLabel = stringResource(R.string.preview_description)
    val tapAction = stringResource(
        if (ui.screen is Screen.Speaking || ui.screen is Screen.Processing) R.string.action_stop else R.string.action_read_now,
    )
    val previewState = when (ui.screen) {
        is Screen.Speaking -> stringResource(R.string.status_speaking)
        is Screen.Processing -> stringResource(R.string.status_processing)
        else -> stringResource(if (ui.blocks.isEmpty()) R.string.status_searching else R.string.status_text_visible)
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .heightIn(min = 160.dp),
    ) {
        key(ui.cameraGeneration) { CameraPreview(vm, Modifier.fillMaxSize()) }
        BlocksOverlay(ui.blocks, Modifier.fillMaxSize())
        // Cały podgląd działa jak jeden duży przycisk: czytaj teraz / zatrzymaj.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClickLabel = tapAction, role = Role.Button, onClick = vm::onScreenTapped)
                .semantics {
                    contentDescription = previewLabel
                    stateDescription = previewState
                    role = Role.Button
                    customActions = listOf(
                        CustomAccessibilityAction(tapAction) {
                            vm.onScreenTapped()
                            true
                        },
                    )
                },
        )
    }

    // Przyciski i tekst zajmują tyle, ile potrzebują, ale najwyżej ok. 62% ekranu – przy powiększeniu czcionki
    // do 200% przewijają się zamiast być ucięte; podgląd dostaje resztę.
    val maxControlsHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    Column(
        modifier = Modifier
            .heightIn(max = maxControlsHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Controls(ui, vm, onOpenSettings)
    }
}

@Composable
private fun Controls(ui: MainUiState, vm: MainViewModel, onOpenSettings: () -> Unit) {
    ui.lastSpokenText?.let { text ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = White)
        }
    }

    BigButton(
        text = stringResource(R.string.button_repeat),
        onClick = vm::onRepeat,
        primary = true,
        minHeight = PrimaryTouchTarget,
        modifier = Modifier.fillMaxWidth(),
    )
    BigToggle(
        label = stringResource(R.string.button_translate),
        stateText = stringResource(if (ui.translate) R.string.state_on else R.string.state_off),
        checked = ui.translate,
        onToggle = vm::onTranslateToggled,
        modifier = Modifier.fillMaxWidth(),
    )
    val rateState = stringResource(R.string.rate_state, formatRate(ui.speechRate))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigButton(stringResource(R.string.button_slower), vm::onSlower, Modifier.weight(1f), stateDescription = rateState)
        BigButton(stringResource(R.string.button_faster), vm::onFaster, Modifier.weight(1f), stateDescription = rateState)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ui.hasFlash) {
            BigToggle(
                label = stringResource(R.string.button_torch),
                stateText = stringResource(if (ui.torchOn) R.string.state_on else R.string.state_off),
                checked = ui.torchOn,
                onToggle = vm::onTorchToggled,
                modifier = Modifier.weight(1f),
            )
        }
        BigButton(stringResource(R.string.button_settings), onOpenSettings, Modifier.weight(1f))
    }
}

@Composable
private fun CameraPreview(vm: MainViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
    LaunchedEffect(previewView, lifecycleOwner) {
        vm.onCameraBound(vm.camera.bind(lifecycleOwner, previewView, vm.analyzer))
    }
    DisposableEffect(previewView) {
        onDispose {
            vm.camera.unbind()
            vm.onCameraUnbound()
        }
    }
}

/** Żółte obrysy wykrytych bloków – pomoc dla osób z resztkami wzroku. */
@Composable
private fun BlocksOverlay(blocks: List<TextBox>, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 4.dp.toPx())
        for (b in blocks) {
            drawRect(
                color = Yellow,
                topLeft = Offset((b.left * size.width).toFloat(), (b.top * size.height).toFloat()),
                size = Size((b.width * size.width).toFloat(), (b.height * size.height).toFloat()),
                style = stroke,
            )
        }
    }
}

@Composable
private fun PermissionContent(denied: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            stringResource(if (denied) R.string.msg_permission_denied else R.string.msg_permission_explanation),
            style = MaterialTheme.typography.bodyLarge,
            color = White,
        )
        BigButton(
            text = stringResource(if (denied) R.string.button_open_settings else R.string.button_grant_access),
            onClick = onClick,
            primary = true,
            minHeight = PrimaryTouchTarget,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ErrorContent(kind: ErrorKind, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            stringResource(
                when (kind) {
                    ErrorKind.CAMERA_UNAVAILABLE -> R.string.msg_camera_unavailable
                    ErrorKind.NO_SPEECH_ENGINE -> R.string.msg_no_speech_engine
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = White,
        )
        Spacer(Modifier.heightIn(min = 8.dp))
        BigButton(
            text = stringResource(R.string.button_retry),
            onClick = onRetry,
            primary = true,
            minHeight = PrimaryTouchTarget,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
