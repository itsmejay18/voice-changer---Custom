package com.vicechanger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vicechanger.app.ui.Screen
import com.vicechanger.app.ui.components.BannerTone
import com.vicechanger.app.ui.components.LevelBar
import com.vicechanger.app.ui.components.MessageBanner
import com.vicechanger.app.ui.components.MetricTile
import com.vicechanger.app.ui.components.PrimaryActionButton
import com.vicechanger.app.ui.components.ScreenHeader
import com.vicechanger.app.ui.components.SecondaryActionButton
import com.vicechanger.app.ui.components.SectionHeader
import com.vicechanger.app.ui.components.StatusDot
import com.vicechanger.app.ui.components.VcIcon
import com.vicechanger.app.ui.components.VcPanel
import com.vicechanger.app.ui.components.VoiceChip
import com.vicechanger.app.ui.theme.ViceColors
import com.vicechanger.app.viewmodel.AppViewModel
import com.vicechanger.app.viewmodel.LiveViewModel

/**
 * Live Mode: start the engine, watch real metered levels, see the measured pipeline latency.
 *
 * The latency shown is computed every ~240 ms from the actual buffer occupancies of the
 * capture side, the DSP stages and the output mixer - when the engine is not running the UI
 * shows a dash rather than a made-up number.
 */
@Composable
fun LiveVoiceScreen(
    app: AppViewModel,
    live: LiveViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val preset by app.selectedPreset.collectAsStateWithLifecycle()
    val settings by app.appSettings.collectAsStateWithLifecycle()
    val state by live.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { live.stop() }
    }

    // Settings or voice changed while running: push it into the engine without a restart.
    LaunchedEffect(preset, settings) {
        if (state.running) live.applyVoice(preset, settings)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 18.dp),
    ) {
        ScreenHeader(
            title = "LIVE VOICE",
            subtitle = "Real-time processing on a dedicated audio thread",
            onBack = onBack,
        )

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                        }
                        VoiceChip(
                            preset = preset,
                            onClick = { onNavigate(Screen.Presets) },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    StatusDot(
                        active = state.running,
                        label = when {
                            state.failure != null -> "STOPPED"
                            state.running -> "ACTIVE"
                            else -> "IDLE"
                        },
                    )
                    if (state.warmup) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Warming up the analysis window...",
                            style = MaterialTheme.typography.labelMedium,
                            color = ViceColors.Amber,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            VcPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    LevelBar(
                        label = "INPUT LEVEL",
                        value01 = state.inputLevel,
                        color = ViceColors.Violet,
                    )
                    Spacer(Modifier.height(14.dp))
                    LevelBar(
                        label = "OUTPUT LEVEL",
                        value01 = state.outputLevel,
                        color = ViceColors.Mint,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            label = "Pipeline delay",
                            value = if (state.running) "%.0f ms".format(state.latencyMs) else "---",
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "Output peak",
                            value = if (state.running) "%.1f dB".format(state.outputPeakDb) else "---",
                            accent = if (state.outputPeakDb > -1f) ViceColors.Amber else ViceColors.Mint,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            label = "Blocks",
                            value = state.blocksProcessed.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "Buffer gaps",
                            value = state.underruns.toString(),
                            accent = if (state.underruns > 0) ViceColors.Amber else ViceColors.Mint,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Measured pipeline delay = audio buffered in the capture buffer, " +
                            "the DSP stages and the output mixer, divided by the sample rate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "In: ${state.inputDevice}   -   Out: ${state.outputDevice}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.failure != null) {
                Spacer(Modifier.height(14.dp))
                MessageBanner(text = state.failure!!.message, tone = BannerTone.ERROR)
            }

            Spacer(Modifier.height(18.dp))

            if (state.running) {
                PrimaryActionButton(
                    text = "STOP VOICE",
                    icon = VcIcon.Stop,
                    onClick = { live.stop() },
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(ViceColors.Red, ViceColors.RoseDeep),
                    ),
                )
            } else {
                PrimaryActionButton(
                    text = "START VOICE",
                    icon = VcIcon.Mic,
                    onClick = { live.start(preset, settings) },
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(
                    text = if (state.muted) "UNMUTE" else "MUTE",
                    icon = if (state.muted) VcIcon.Volume else VcIcon.MicOff,
                    onClick = { live.toggleMute() },
                    enabled = state.running,
                    modifier = Modifier.weight(1f),
                    accent = ViceColors.Amber,
                )
                SecondaryActionButton(
                    text = "PICK VOICE",
                    icon = VcIcon.Sliders,
                    onClick = { onNavigate(Screen.Presets) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader(text = "All voices")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                app.allPresets().forEach { candidate ->
                    VoiceChip(
                        preset = candidate,
                        onClick = {
                            app.selectPreset(candidate)
                            live.applyVoice(candidate, settings)
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            MessageBanner(
                text = "Live Mode plays the transformed voice through your phone's own output so you " +
                    "can hear it. Android does not allow an app to take over another app's microphone, " +
                    "so this audio cannot be pushed into Mobile Legends or a Messenger call - see " +
                    "Mobile Legends Mode for what is possible and what is not.",
                tone = BannerTone.INFO,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
