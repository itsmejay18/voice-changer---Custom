package com.vicechanger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.vicechanger.app.viewmodel.AppViewModel
import com.vicechanger.app.viewmodel.MessageViewModel

/**
 * Voice Message mode: the workflow that actually works everywhere - record, transform,
 * preview, save into the music library, share through the system share sheet.
 */
@Composable
fun VoiceMessageScreen(
    app: AppViewModel,
    message: MessageViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val preset by app.selectedPreset.collectAsStateWithLifecycle()
    val settings by app.appSettings.collectAsStateWithLifecycle()

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
            title = "VOICE MESSAGE",
            subtitle = "Record, transform, save and share",
            onBack = onBack,
        )

        Spacer(Modifier.height(14.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            RecordProcessPanel(
                message = message,
                preset = preset,
                settings = settings,
                onOpenVoicePicker = { onNavigate(Screen.Presets) },
            )

            Spacer(Modifier.height(18.dp))

            MessageBanner(
                text = "Record, process, then SAVE puts the file in Music/VICECHANGER and SHARE " +
                    "hands it to any messaging app through Android's own share sheet. The WAV is " +
                    "16-bit PCM at 48 kHz mono, which every messaging app accepts.",
                tone = BannerTone.INFO,
            )

            Spacer(Modifier.height(12.dp))

            MessageBanner(
                text = "A voice message is a file, not a live stream. Whether a *call* can carry the " +
                    "transformed voice depends on Android audio routing and the other app - that is " +
                    "explained in Messenger Voice Mode.",
                tone = BannerTone.WARNING,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
