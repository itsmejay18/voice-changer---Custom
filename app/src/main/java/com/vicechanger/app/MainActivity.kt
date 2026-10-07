package com.vicechanger.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vicechanger.app.ui.AppRoot
import com.vicechanger.app.ui.theme.ViceChangerTheme
import com.vicechanger.app.utils.ShareIntents
import com.vicechanger.app.viewmodel.AppViewModel
import com.vicechanger.app.viewmodel.LiveViewModel
import com.vicechanger.app.viewmodel.MessageViewModel

/**
 * Single activity. Owns the microphone permission flow and hands three activity-scoped
 * ViewModels to the Compose tree, so a rotation keeps the voice selection, the live engine
 * and any recorded-but-unprocessed audio.
 */
class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the permission state is re-read after a trip to Settings. */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val app: AppViewModel = viewModel()
            val live: LiveViewModel = viewModel()
            val message: MessageViewModel = viewModel()

            val settings by app.appSettings.collectAsStateWithLifecycle()

            val tick by resumeTick
            var granted by remember(tick) { mutableStateOf(hasMicrophonePermission()) }
            var requested by remember { mutableStateOf(false) }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted ->
                granted = isGranted
                requested = true
                if (!isGranted) {
                    app.showMessage("Microphone permission is required for voice processing.")
                }
            }

            ViceChangerTheme(themeMode = settings.themeMode) {
                AppRoot(
                    app = app,
                    live = live,
                    message = message,
                    hasPermission = granted,
                    permissionPermanentlyDenied = requested &&
                        !granted &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
                    onRequestPermission = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                    onOpenAppSettings = { startActivity(ShareIntents.appSettingsIntent(this)) },
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the system settings page is the only way the user can un-deny the
        // permission, so re-read it on resume instead of making them restart the app.
        resumeTick.intValue++
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
