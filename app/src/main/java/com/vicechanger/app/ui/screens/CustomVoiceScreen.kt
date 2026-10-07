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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.ui.Screen
import com.vicechanger.app.ui.components.BannerTone
import com.vicechanger.app.ui.components.MessageBanner
import com.vicechanger.app.ui.components.PrimaryActionButton
import com.vicechanger.app.ui.components.ScreenHeader
import com.vicechanger.app.ui.components.SecondaryActionButton
import com.vicechanger.app.ui.components.SectionHeader
import com.vicechanger.app.ui.components.VcIcon
import com.vicechanger.app.ui.components.VcPanel
import com.vicechanger.app.ui.components.VcSlider
import com.vicechanger.app.ui.theme.ViceColors
import com.vicechanger.app.voice.VoiceTransformer
import com.vicechanger.app.viewmodel.AppViewModel

/**
 * Custom Girl: the five sliders the spec asks for, a live description of what the current
 * values do to the audio, and saving/loading named custom voices.
 */
@Composable
fun CustomVoiceScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val draft by app.customDraft.collectAsStateWithLifecycle()
    val saved by app.savedCustom.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 18.dp),
    ) {
        ScreenHeader(
            title = "CUSTOM GIRL",
            subtitle = "Pitch, formant, brightness, resonance and effect",
            onBack = onBack,
        )

        Spacer(Modifier.height(14.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            VcPanel(modifier = Modifier.fillMaxWidth(), accent = ViceColors.Rose) {
                Column(Modifier.padding(16.dp)) {
                    VcSlider(
                        label = "Pitch",
                        value = draft.pitch,
                        valueRange = AudioConfig.MIN_PITCH_SEMITONES..AudioConfig.MAX_PITCH_SEMITONES,
                        onValueChange = { app.updateCustomDraft(draft.copy(pitch = it)) },
                        valueText = "%+.1f st".format(draft.pitch),
                    )
                    VcSlider(
                        label = "Formant",
                        value = draft.formant,
                        valueRange = AudioConfig.MIN_FORMANT..AudioConfig.MAX_FORMANT,
                        onValueChange = { app.updateCustomDraft(draft.copy(formant = it)) },
                        valueText = "x%.2f".format(draft.formant),
                    )
                    VcSlider(
                        label = "Brightness",
                        value = draft.brightness,
                        valueRange = -8f..8f,
                        onValueChange = { app.updateCustomDraft(draft.copy(brightness = it)) },
                        valueText = "%+.1f dB".format(draft.brightness),
                    )
                    VcSlider(
                        label = "Resonance",
                        value = draft.resonance,
                        valueRange = -6f..8f,
                        onValueChange = { app.updateCustomDraft(draft.copy(resonance = it)) },
                        valueText = "%+.1f dB".format(draft.resonance),
                    )
                    VcSlider(
                        label = "Effect intensity",
                        value = draft.effectStrength,
                        valueRange = 0f..1f,
                        onValueChange = { app.updateCustomDraft(draft.copy(effectStrength = it)) },
                        valueText = "%.0f%%".format(draft.effectStrength * 100),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Pitch ${VoiceTransformer.describe(draft)} - output trim " +
                            "%+.1f dB (automatic loudness compensation)".format(
                                VoiceTransformer.automaticTrim(draft),
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = ViceColors.Mint,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(
                    text = "USE THIS VOICE",
                    icon = VcIcon.Check,
                    onClick = { app.selectPreset(draft) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryActionButton(
                    text = "PREVIEW",
                    icon = VcIcon.Mic,
                    onClick = { onNavigate(Screen.Live) },
                    modifier = Modifier.weight(1f),
                    accent = ViceColors.Rose,
                )
            }

            Spacer(Modifier.height(10.dp))

            SecondaryActionButton(
                text = "RESET SLIDERS",
                icon = VcIcon.Refresh,
                onClick = { app.resetCustomDraft() },
                modifier = Modifier.fillMaxWidth(),
                accent = ViceColors.Amber,
            )

            Spacer(Modifier.height(22.dp))

            SectionHeader(text = "Save this configuration")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PrimaryActionButton(
                text = "SAVE PRESET",
                icon = VcIcon.Save,
                onClick = {
                    app.saveCustomPreset(name)
                    name = ""
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Saved voices are stored on this device only and appear in the voice picker " +
                    "next to the ten built-in voices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (saved.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(text = "Saved custom voices (${saved.size})")
                saved.forEach { preset ->
                    VcPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = VoiceTransformer.describe(preset),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SecondaryActionButton(
                                text = "LOAD",
                                icon = VcIcon.Sliders,
                                onClick = { app.updateCustomDraft(preset.copy(id = com.vicechanger.app.voice.BuiltInVoices.CUSTOM_ID)) },
                            )
                            Spacer(Modifier.width(6.dp))
                            SecondaryActionButton(
                                text = "DELETE",
                                icon = VcIcon.Delete,
                                onClick = { app.deleteCustomPreset(preset.id) },
                                accent = ViceColors.Red,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            MessageBanner(
                text = "Preview opens Live Mode with this configuration already armed - speak and you " +
                    "will hear it. Nothing is uploaded anywhere.",
                tone = BannerTone.INFO,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
