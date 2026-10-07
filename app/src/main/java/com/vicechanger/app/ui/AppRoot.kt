package com.vicechanger.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vicechanger.app.ui.components.BannerTone
import com.vicechanger.app.ui.components.MessageBanner
import com.vicechanger.app.ui.components.PrimaryActionButton
import com.vicechanger.app.ui.components.VcIcon
import com.vicechanger.app.ui.components.VcPanel
import com.vicechanger.app.ui.components.ViceIcon
import com.vicechanger.app.ui.screens.CustomVoiceScreen
import com.vicechanger.app.ui.screens.HomeScreen
import com.vicechanger.app.ui.screens.LiveVoiceScreen
import com.vicechanger.app.ui.screens.MessengerScreen
import com.vicechanger.app.ui.screens.MobileLegendsScreen
import com.vicechanger.app.ui.screens.PresetPickerScreen
import com.vicechanger.app.ui.screens.SettingsScreen
import com.vicechanger.app.ui.screens.VoiceMessageScreen
import com.vicechanger.app.ui.theme.ViceColors
import com.vicechanger.app.viewmodel.AppViewModel
import com.vicechanger.app.viewmodel.LiveViewModel
import com.vicechanger.app.viewmodel.MessageViewModel
import kotlinx.coroutines.delay

/**
 * Navigation, the microphone permission gate, and the rules about when audio is allowed to run.
 *
 * Audio policy implemented here:
 *  - the live engine is stopped whenever the user leaves the live/picker/custom group,
 *  - it is stopped again when the activity goes to the background (no hidden background
 *    microphone, ever).
 */
@Composable
fun AppRoot(
    app: AppViewModel,
    live: LiveViewModel,
    message: MessageViewModel,
    hasPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screen by app.screen.collectAsStateWithLifecycle()
    val transientMessage by app.message.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(enabled = true) {
        if (!app.back()) onExit()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                live.stop()
                message.stopPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(screen) {
        val voiceSelecting = screen == Screen.Live || screen == Screen.Presets || screen == Screen.Custom
        if (!voiceSelecting) live.stop()
    }

    LaunchedEffect(transientMessage) {
        if (transientMessage != null) {
            delay(2600)
            app.clearMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedVisibility(visible = transientMessage != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                MessageBanner(
                    text = transientMessage ?: "",
                    tone = BannerTone.SUCCESS,
                )
            }
        }

        if (!hasPermission) {
            MicrophonePermissionGate(
                permanentlyDenied = permissionPermanentlyDenied,
                onRequestPermission = onRequestPermission,
                onOpenAppSettings = onOpenAppSettings,
            )
            return@Column
        }

        when (screen) {
            Screen.Home -> HomeScreen(app = app, onNavigate = { app.navigate(it) })

            Screen.Live -> LiveVoiceScreen(
                app = app,
                live = live,
                onBack = { app.back() },
                onNavigate = { app.navigate(it) },
            )

            Screen.Presets -> PresetPickerScreen(
                app = app,
                onBack = { app.back() },
                onNavigate = { app.navigate(it) },
            )

            Screen.Custom -> CustomVoiceScreen(
                app = app,
                onBack = { app.back() },
                onNavigate = { app.navigate(it) },
            )

            Screen.VoiceMessage -> VoiceMessageScreen(
                app = app,
                message = message,
                onBack = { app.back() },
                onNavigate = { app.navigate(it) },
            )

            Screen.MobileLegends -> MobileLegendsScreen(
                app = app,
                message = message,
                onBack = { app.back() },
                onNavigate = { app.navigate(it) },
            )

            Screen.Messenger -> MessengerScreen(
                app = app,
                message = message,
                onBack = { app.back() },
                onNavigate = { app.navigate(it) },
            )

            Screen.Settings -> SettingsScreen(
                app = app,
                onBack = { app.back() },
                onNavigate = { app.navigate(it) },
            )
        }
    }
}

@Composable
private fun MicrophonePermissionGate(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        ViceIcon(VcIcon.Mic, size = 72.dp, tint = ViceColors.Rose, strokeWidth = 3f)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "VICE CHANGER",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "AI-Free Real-Time Female Voice Changer",
            style = MaterialTheme.typography.bodyMedium,
            color = ViceColors.RoseSoft,
        )
        Spacer(Modifier.height(24.dp))
        VcPanel(modifier = Modifier.fillMaxWidth(), accent = ViceColors.Rose) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = "Microphone permission",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "VICE CHANGER needs microphone access to process your voice. Audio is " +
                        "processed on this device and never uploaded - the app has no internet " +
                        "permission at all.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        if (permanentlyDenied) {
            MessageBanner(
                text = "Microphone permission is required for voice processing. It was denied, so " +
                    "open the app settings and allow Microphone.",
                tone = BannerTone.ERROR,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryActionButton(
                text = "OPEN APP SETTINGS",
                icon = VcIcon.Settings,
                onClick = onOpenAppSettings,
            )
        } else {
            PrimaryActionButton(
                text = "ALLOW MICROPHONE",
                icon = VcIcon.Mic,
                onClick = onRequestPermission,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Only RECORD_AUDIO is requested. No storage, contacts, location or camera access.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
