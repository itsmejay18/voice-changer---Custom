package com.vicechanger.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vicechanger.app.recording.AudioFileStore
import com.vicechanger.app.settings.AppSettings
import com.vicechanger.app.ui.theme.ViceColors
import com.vicechanger.app.utils.ShareIntents
import com.vicechanger.app.voice.VoicePreset
import com.vicechanger.app.voice.VoiceTransformer
import com.vicechanger.app.viewmodel.MessageViewModel

/**
 * Record -> process -> preview -> save -> share, shared by the Voice Message screen and the
 * Messenger screen. Both modes are the same pipeline with a different explanation on top, so
 * the controls live here once instead of being duplicated (and drifting) in two screens.
 */
@Composable
fun RecordProcessPanel(
    message: MessageViewModel,
    preset: VoicePreset,
    settings: AppSettings,
    onOpenVoicePicker: () -> Unit,
    modifier: Modifier = Modifier,
    showMessengerButton: Boolean = false,
) {
    val state by message.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        VcPanel(modifier = Modifier.fillMaxWidth(), accent = ViceColors.Rose) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = VoiceTransformer.describe(preset),
                            style = MaterialTheme.typography.labelMedium,
                            color = ViceColors.Mint,
                        )
                    }
                    VoiceChip(preset = preset, onClick = onOpenVoicePicker)
                }

                Spacer(Modifier.height(16.dp))

                StatusDot(
                    active = state.stage == MessageViewModel.Stage.RECORDING,
                    label = when (state.stage) {
                        MessageViewModel.Stage.RECORDING ->
                            "RECORDING  %.1f s".format(state.seconds)
                        MessageViewModel.Stage.RECORDED -> "RECORDED  %.1f s".format(state.seconds)
                        MessageViewModel.Stage.PROCESSING -> "PROCESSING..."
                        MessageViewModel.Stage.PROCESSED -> "READY  %.1f s".format(state.processedSeconds)
                        MessageViewModel.Stage.IDLE -> "IDLE"
                    },
                )

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    WaveformStrip(
                        levels = state.waveform,
                        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
                        color = when (state.stage) {
                            MessageViewModel.Stage.RECORDING -> ViceColors.Red
                            MessageViewModel.Stage.PROCESSED -> ViceColors.Mint
                            else -> ViceColors.RoseSoft
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))

                LevelBar(
                    label = "MIC INPUT",
                    value01 = state.level,
                    color = ViceColors.Violet,
                )
            }
        }

        if (state.failure != null) {
            Spacer(Modifier.height(12.dp))
            MessageBanner(text = state.failure!!.message, tone = BannerTone.ERROR)
        }

        Spacer(Modifier.height(14.dp))

        when (state.stage) {
            MessageViewModel.Stage.RECORDING -> {
                PrimaryActionButton(
                    text = "STOP",
                    icon = VcIcon.Stop,
                    onClick = { message.stopRecording() },
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(ViceColors.Red, ViceColors.RoseDeep),
                    ),
                )
                Spacer(Modifier.height(10.dp))
                SecondaryActionButton(
                    text = "DISCARD",
                    icon = VcIcon.Delete,
                    onClick = { message.cancelRecording() },
                    modifier = Modifier.fillMaxWidth(),
                    accent = ViceColors.Amber,
                )
            }
            MessageViewModel.Stage.PROCESSING -> {
                PrimaryActionButton(
                    text = "PROCESSING WITH ${state.processingVoiceName.uppercase()}...",
                    icon = VcIcon.Refresh,
                    onClick = {},
                    enabled = false,
                )
            }
            else -> {
                PrimaryActionButton(
                    text = if (state.hasRecording) "PROCESS AGAIN" else "RECORD",
                    icon = if (state.hasRecording) VcIcon.Refresh else VcIcon.Record,
                    onClick = {
                        if (state.hasRecording) {
                            message.process(preset, settings)
                        } else {
                            message.startRecording()
                        }
                    },
                    enabled = !state.busy,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryActionButton(
                        text = if (state.playing) "STOP" else "PLAY",
                        icon = if (state.playing) VcIcon.Stop else VcIcon.Play,
                        onClick = { if (state.playing) message.stopPlayback() else message.play() },
                        enabled = state.hasProcessed,
                        modifier = Modifier.weight(1f),
                        accent = ViceColors.Mint,
                    )
                    SecondaryActionButton(
                        text = "RECORD NEW",
                        icon = VcIcon.Record,
                        onClick = {
                            message.stopPlayback()
                            message.startRecording()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryActionButton(
                        text = "SAVE",
                        icon = VcIcon.Save,
                        onClick = { message.saveToLibrary() },
                        enabled = state.hasProcessed && !state.savedToLibrary,
                        modifier = Modifier.weight(1f),
                        accent = ViceColors.Amber,
                    )
                    SecondaryActionButton(
                        text = "SHARE",
                        icon = VcIcon.Share,
                        onClick = { shareProcessed(context, message, generic = true) },
                        enabled = state.hasProcessed,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showMessengerButton) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryActionButton(
                        text = "SEND WITH MESSENGER",
                        icon = VcIcon.Chat,
                        onClick = { shareProcessed(context, message, generic = false) },
                        enabled = state.hasProcessed &&
                            ShareIntents.isInstalled(context, ShareIntents.MESSENGER_PACKAGE),
                        modifier = Modifier.fillMaxWidth(),
                        accent = ViceColors.Violet,
                    )
                }
            }
        }

        if (state.hasProcessed) {
            Spacer(Modifier.height(14.dp))
            VcPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    MetricRow("File", state.processedName)
                    MetricRow("Length", "%.1f s".format(state.processedSeconds))
                    MetricRow("Output peak", "%.1f dBFS".format(state.processedPeakDb))
                    MetricRow("Processing delay", "%.0f ms".format(state.measuredLatencyMs))
                    MetricRow("Format", "WAV, 16-bit PCM, 48 kHz mono")
                    if (state.savedToLibrary) {
                        MetricRow("Library", "Saved to Music/${AudioFileStore.MEDIA_FOLDER}")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = ViceColors.Mint,
        )
    }
}

private fun shareProcessed(context: Context, message: MessageViewModel, generic: Boolean) {
    val file = message.shareableFile() ?: return
    val name = message.state.value.processedName
    if (generic) {
        context.startActivity(ShareIntents.shareAudio(context, file, name))
        return
    }
    val direct = ShareIntents.shareToMessenger(context, file, name)
    if (direct != null) {
        context.startActivity(direct)
    } else {
        context.startActivity(ShareIntents.shareAudio(context, file, name))
    }
}
