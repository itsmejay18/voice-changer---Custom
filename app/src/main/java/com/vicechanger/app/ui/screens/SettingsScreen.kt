package com.vicechanger.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vicechanger.app.BuildConfig
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.settings.ThemeMode
import com.vicechanger.app.ui.Screen
import com.vicechanger.app.ui.components.BannerTone
import com.vicechanger.app.ui.components.MessageBanner
import com.vicechanger.app.ui.components.ScreenHeader
import com.vicechanger.app.ui.components.SecondaryActionButton
import com.vicechanger.app.ui.components.SectionHeader
import com.vicechanger.app.ui.components.VcIcon
import com.vicechanger.app.ui.components.VcPanel
import com.vicechanger.app.ui.components.VcSlider
import com.vicechanger.app.ui.components.VcSwitchRow
import com.vicechanger.app.ui.components.ViceIcon
import com.vicechanger.app.ui.theme.ViceColors
import com.vicechanger.app.viewmodel.AppViewModel

/**
 * Settings: audio routing and levels, the voice defaults, appearance and about.
 *
 * Device lists come from the platform through [com.vicechanger.app.audio.AudioDeviceMonitor],
 * so a Bluetooth headset that connects while this screen is open appears without a restart.
 */
@Composable
fun SettingsScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val settings by app.appSettings.collectAsStateWithLifecycle()
    val devices by app.devices.state.collectAsStateWithLifecycle()
    val preset by app.selectedPreset.collectAsStateWithLifecycle()
    val draft by app.customDraft.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 18.dp),
    ) {
        ScreenHeader(
            title = "SETTINGS",
            subtitle = "Audio, voice, appearance, about",
            onBack = onBack,
        )

        Spacer(Modifier.height(14.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------------------------------------------------------------- audio ----
            SectionHeader(text = "Audio")
            VcPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Input device",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    DeviceRow(
                        label = "System default",
                        selected = settings.preferredInputDeviceId < 0,
                        onClick = { app.updateSettings { it.copy(preferredInputDeviceId = -1) } },
                    )
                    devices.inputs.forEach { device ->
                        DeviceRow(
                            label = device.label,
                            selected = settings.preferredInputDeviceId == device.id,
                            onClick = { app.updateSettings { it.copy(preferredInputDeviceId = device.id) } },
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Output device",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    DeviceRow(
                        label = "System default",
                        selected = settings.preferredOutputDeviceId < 0,
                        onClick = { app.updateSettings { it.copy(preferredOutputDeviceId = -1) } },
                    )
                    devices.outputs.forEach { device ->
                        DeviceRow(
                            label = device.label,
                            selected = settings.preferredOutputDeviceId == device.id,
                            onClick = { app.updateSettings { it.copy(preferredOutputDeviceId = device.id) } },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (devices.hasHeadset) {
                            "A headset or Bluetooth device is connected."
                        } else {
                            "No headset connected - output goes to the phone speaker. Echo reduction " +
                                "keeps that from howling."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    devices.lastChange?.let { change ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = change,
                            style = MaterialTheme.typography.labelMedium,
                            color = ViceColors.Mint,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            VcPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    VcSlider(
                        label = "Input gain",
                        value = settings.inputGainDb,
                        valueRange = -12f..18f,
                        onValueChange = { value -> app.updateSettings { it.copy(inputGainDb = value) } },
                        valueText = "%+.1f dB".format(settings.inputGainDb),
                    )
                    VcSlider(
                        label = "Output volume",
                        value = settings.outputVolume,
                        valueRange = 0f..1f,
                        onValueChange = { value -> app.updateSettings { it.copy(outputVolume = value) } },
                        valueText = "%.0f%%".format(settings.outputVolume * 100),
                    )
                    VcSlider(
                        label = "Noise suppression",
                        value = settings.noiseSuppression,
                        valueRange = 0f..1f,
                        onValueChange = { value -> app.updateSettings { it.copy(noiseSuppression = value) } },
                        valueText = "%.0f%%".format(settings.noiseSuppression * 100),
                    )
                    VcSlider(
                        label = "Echo reduction strength",
                        value = settings.echoReductionAmountDb,
                        valueRange = 0f..30f,
                        onValueChange = { value -> app.updateSettings { it.copy(echoReductionAmountDb = value) } },
                        valueText = "-%.0f dB".format(settings.echoReductionAmountDb),
                    )
                    Spacer(Modifier.height(6.dp))
                    VcSwitchRow(
                        title = "Echo reduction",
                        description = "Ducks the microphone while the output is loud. Keeps speaker " +
                            "monitoring from feeding back - turn it off when you use headphones.",
                        checked = settings.echoReduction,
                        onCheckedChange = { value -> app.updateSettings { it.copy(echoReduction = value) } },
                    )
                    VcSwitchRow(
                        title = "Low-latency mode",
                        description = "5 ms audio blocks and a smaller output buffer. Lower delay, " +
                            "slightly more CPU.",
                        checked = settings.lowLatencyMode,
                        onCheckedChange = { value -> app.updateSettings { it.copy(lowLatencyMode = value) } },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---------------------------------------------------------------- voice ----
            SectionHeader(text = "Voice")
            VcPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default voice",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = ViceColors.Rose,
                            )
                        }
                        SecondaryActionButton(
                            text = "CHANGE",
                            icon = VcIcon.Sliders,
                            onClick = { onNavigate(Screen.Presets) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "These three sliders tune the Custom Girl voice and are saved with it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VcSlider(
                        label = "Pitch",
                        value = draft.pitch,
                        valueRange = AudioConfig.MIN_PITCH_SEMITONES..AudioConfig.MAX_PITCH_SEMITONES,
                        onValueChange = { value -> app.updateCustomDraft(draft.copy(pitch = value)) },
                        valueText = "%+.1f st".format(draft.pitch),
                    )
                    VcSlider(
                        label = "Formant",
                        value = draft.formant,
                        valueRange = AudioConfig.MIN_FORMANT..AudioConfig.MAX_FORMANT,
                        onValueChange = { value -> app.updateCustomDraft(draft.copy(formant = value)) },
                        valueText = "x%.2f".format(draft.formant),
                    )
                    VcSlider(
                        label = "Effect strength",
                        value = draft.effectStrength,
                        valueRange = 0f..1f,
                        onValueChange = { value -> app.updateCustomDraft(draft.copy(effectStrength = value)) },
                        valueText = "%.0f%%".format(draft.effectStrength * 100),
                    )
                    Spacer(Modifier.height(6.dp))
                    SecondaryActionButton(
                        text = "OPEN CUSTOM GIRL SCREEN",
                        icon = VcIcon.Sliders,
                        onClick = { onNavigate(Screen.Custom) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ----------------------------------------------------------- appearance ----
            SectionHeader(text = "Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = settings.themeMode == mode
                    SecondaryActionButton(
                        text = mode.name,
                        icon = if (selected) VcIcon.Check else null,
                        onClick = { app.updateSettings { it.copy(themeMode = mode) } },
                        modifier = Modifier.weight(1f),
                        accent = if (selected) ViceColors.Rose else MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---------------------------------------------------------------- about ----
            SectionHeader(text = "About")
            VcPanel(modifier = Modifier.fillMaxWidth(), accent = ViceColors.Rose) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ViceIcon(VcIcon.Waveform, size = 26.dp, tint = ViceColors.RoseSoft)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "VICE CHANGER",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    AboutRow("Version", BuildConfig.VERSION_NAME)
                    AboutRow("Build", BuildConfig.BUILD_TYPE)
                    AboutRow("Application id", BuildConfig.APPLICATION_ID)
                    AboutRow("Engine", "Phase-vocoder pitch shift with independent formant warping")
                    AboutRow("Sample rate", "${AudioConfig.SAMPLE_RATE} Hz mono, 16-bit PCM")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Female Voice Transformation App",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Developed by:\nJay J. Ababon",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "(c) 2026 VICE CHANGER",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            VcPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Permissions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "RECORD_AUDIO is the only permission this app requests. No contacts, " +
                            "SMS, call logs, location, camera, storage or accessibility access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No internet permission is declared, so no audio and no usage data can " +
                            "leave this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ViceColors.Mint,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DeviceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViceIcon(
            if (selected) VcIcon.Check else VcIcon.Volume,
            size = 16.dp,
            tint = if (selected) ViceColors.Rose else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) ViceColors.Rose else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        SecondaryActionButton(
            text = if (selected) "SELECTED" else "USE",
            onClick = onClick,
            enabled = !selected,
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = ViceColors.Mint,
        )
    }
}
