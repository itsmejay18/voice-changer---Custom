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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vicechanger.app.ui.Screen
import com.vicechanger.app.ui.components.BannerTone
import com.vicechanger.app.ui.components.MessageBanner
import com.vicechanger.app.ui.components.PresetCard
import com.vicechanger.app.ui.components.ScreenHeader
import com.vicechanger.app.ui.components.SecondaryActionButton
import com.vicechanger.app.ui.components.VcIcon
import com.vicechanger.app.voice.VoiceTransformer
import com.vicechanger.app.viewmodel.AppViewModel

/**
 * The ten voices (plus anything saved from Custom Girl). Selecting here arms the voice for
 * every mode and persists it as the default.
 */
@Composable
fun PresetPickerScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val selected by app.selectedPreset.collectAsStateWithLifecycle()
    val savedCustom by app.savedCustom.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 18.dp),
    ) {
        ScreenHeader(
            title = "CHOOSE VOICE",
            subtitle = "10 female voices, all processed on device",
            onBack = onBack,
        )

        Spacer(Modifier.height(14.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            app.allPresets().forEach { preset ->
                PresetCard(
                    preset = preset,
                    selected = preset.id == selected.id,
                    onClick = { app.selectPreset(preset) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    text = VoiceTransformer.describe(preset),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            if (savedCustom.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MessageBanner(
                    text = "${savedCustom.size} saved custom voice(s). Tap one above to arm it, or " +
                        "edit them under Custom Girl.",
                    tone = BannerTone.SUCCESS,
                )
                Spacer(Modifier.height(12.dp))
            }

            SecondaryActionButton(
                text = "EDIT CUSTOM GIRL",
                icon = VcIcon.Sliders,
                onClick = { onNavigate(Screen.Custom) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
