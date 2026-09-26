package pl.czytnik.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.czytnik.app.main.DiagnosticsScreen
import pl.czytnik.app.main.MainScreen
import pl.czytnik.app.main.MainViewModel
import pl.czytnik.app.main.UiCommand
import pl.czytnik.app.ui.theme.CzytnikTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // PRD: ekran nie gaśnie podczas pracy aplikacji; klawisze głośności sterują mową.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = viewModel.onStart(hasCameraPermission())
            override fun onStop(owner: LifecycleOwner) = viewModel.onStop()
        })

        setContent {
            CzytnikTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(viewModel)
                }
            }
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun App(vm: MainViewModel) {
        val ui by vm.ui.collectAsStateWithLifecycle()
        var showDiagnostics by rememberSaveable { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.onPermissionResult(granted)
        }
        LaunchedEffect(vm) {
            vm.commands.collect { command ->
                when (command) {
                    UiCommand.RequestCameraPermission -> permissionLauncher.launch(Manifest.permission.CAMERA)
                    UiCommand.OpenAppSettings -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                    )
                }
            }
        }
        if (showDiagnostics) {
            BackHandler { showDiagnostics = false }
            DiagnosticsScreen(vm, onClose = { showDiagnostics = false })
        } else {
            MainScreen(ui, vm, onOpenDiagnostics = { showDiagnostics = true })
        }
    }
}
