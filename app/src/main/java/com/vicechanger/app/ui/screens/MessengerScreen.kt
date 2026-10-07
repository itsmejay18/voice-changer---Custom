package com.vicechanger.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vicechanger.app.ui.Screen
import com.vicechanger.app.ui.components.BannerTone
import com.vicechanger.app.ui.components.MessageBanner
import com.vicechanger.app.ui.components.RecordProcessPanel
import com.vicechanger.app.ui.components.ScreenHeader
import com.vicechanger.app.ui.components.SectionHeader
import com.vicechanger.app.ui.components.VcPanel
import com.vicechanger.app.ui.theme.ViceColors
import com.vicechanger.app.utils.ShareIntents
import com.vicechanger.app.viewmodel.AppViewModel
import com.vicechanger.app.viewmodel.MessageViewModel

/**
 * Messenger Voice Mode. Everything here is implemented with the platform's own mechanisms -
 * no invented Messenger API, and the live-call section says plainly what depends on what.
 */
@Composable
fun MessengerScreen(
    app: AppViewModel,
    message: MessageViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val preset by app.selectedPreset.collectAsStateWithLifecycle()
    val settings by app.appSettings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val messengerInstalled = ShareIntents.isInstalled(context, ShareIntents.MESSENGER_PACKAGE)
    val anyAudioTarget = ShareIntents.hasAudioShareTarget(context)

    DisposableEffect(Unit) {
        onDispose {
            message.stopPlayback()
            if (message.state.value.stage == MessageViewModel.Stage.RECORDING) message.stopRecording()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 18.dp),
    ) {
        ScreenHeader(
            title = "MESSENGER VOICE MODE",
            subtitle = "Record, transform, share",
            onBack = onBack,
        )

        Spacer(Modifier.height(14.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            RecordProcessPanel(
                message = message,
                preset = preset,
                settings = settings,
                onOpenVoicePicker = { onNavigate(Screen.Presets) },
                showMessengerButton = true,
            )

            Spacer(Modifier.height(18.dp))

            SectionHeader(text = "Voice messages")
            MessageBanner(
                text = if (messengerInstalled) {
                    "Messenger is installed. \"SEND WITH MESSENGER\" hands the processed WAV straight " +
                        "to Messenger; SHARE opens the full Android share sheet."
                } else {
                    "Messenger was not found on this device, so SHARE uses the standard Android share " +
                        "sheet with whichever audio-capable app you have installed."
                },
                tone = if (messengerInstalled) BannerTone.SUCCESS else BannerTone.INFO,
            )

            if (!anyAudioTarget) {
                Spacer(Modifier.height(10.dp))
                MessageBanner(
                    text = "No installed app advertises support for audio files, so the share sheet may " +
                        "be empty. Install a messaging app or save to the library and attach the file " +
                        "manually.",
                    tone = BannerTone.WARNING,
                )
            }

            Spacer(Modifier.height(18.dp))

            SectionHeader(text = "Messenger calls")
            VcPanel(modifier = Modifier.fillMaxWidth(), accent = ViceColors.Violet) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Live call voice transformation depends on Android audio routing and " +
                            "Messenger compatibility.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "What is true on any normal (non-rooted) Android device:",
                        style = MaterialTheme.typography.labelLarge,
                        color = ViceColors.RoseSoft,
                    )
                    Bullet("An app may record the microphone and play audio out, at the same time - " +
                        "that is what Live Mode does.")
                    Bullet("An app may NOT replace the microphone stream that another app (Messenger, " +
                        "Mobile Legends, Discord) sends in a call. There is no public API for it, and no " +
                        "amount of code in this app can change that.")
                    Bullet("What does work for a call: play the transformed voice out of the phone " +
                        "speaker while the call is on speakerphone is NOT reliable either - it depends on " +
                        "echo cancellation on the other side.")
                    Bullet("What reliably works: send a transformed voice message (this screen) that " +
                        "the other person plays.")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "If a future Messenger or Android release exposes a virtual microphone, " +
                            "the routing layer in this app (RoutingCapabilityProbe) is where support " +
                            "would be reported - the UI is already built to show it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "-",
            style = MaterialTheme.typography.bodyMedium,
            color = ViceColors.Mint,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(6.dp))
}
