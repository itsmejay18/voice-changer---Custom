package com.vicechanger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vicechanger.app.BuildConfig
import com.vicechanger.app.ui.Screen
import com.vicechanger.app.ui.components.ActionCard
import com.vicechanger.app.ui.components.BrandHeader
import com.vicechanger.app.ui.components.VcIcon
import com.vicechanger.app.ui.components.ViceIcon
import com.vicechanger.app.ui.components.VcPanel
import com.vicechanger.app.ui.theme.OrbBrush
import com.vicechanger.app.ui.theme.ViceColors
import com.vicechanger.app.viewmodel.AppViewModel

/**
 * Home: brand block, the currently armed voice, and the four ways into the app.
 */
@Composable
fun HomeScreen(app: AppViewModel, onNavigate: (Screen) -> Unit) {
    val preset by app.selectedPreset.collectAsStateWithLifecycle()
    val customVoices by app.savedCustom.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
    ) {
        BrandHeader(tagline = "Female Voice Transformation")

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(CircleShape)
                        .background(OrbBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    ViceIcon(VcIcon.Mic, size = 62.dp, tint = ViceColors.TextPrimary, strokeWidth = 3.2f)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Voice Changer",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Current Voice",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = preset.name.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ViceColors.Rose,
                )
                Text(
                    text = preset.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            ActionCard(
                icon = VcIcon.Mic,
                title = "LIVE VOICE",
                subtitle = "Real-time voice changer with meters",
                accent = ViceColors.Rose,
                onClick = { onNavigate(Screen.Live) },
            )
            Spacer(Modifier.height(12.dp))
            ActionCard(
                icon = VcIcon.Gamepad,
                title = "MOBILE LEGENDS",
                subtitle = "Gaming voice mode and routing check",
                accent = ViceColors.Violet,
                onClick = { onNavigate(Screen.MobileLegends) },
            )
            Spacer(Modifier.height(12.dp))
            ActionCard(
                icon = VcIcon.Chat,
                title = "VOICE MESSAGE",
                subtitle = "Record, transform, save and share",
                accent = ViceColors.Mint,
                onClick = { onNavigate(Screen.VoiceMessage) },
            )
            Spacer(Modifier.height(12.dp))
            ActionCard(
                icon = VcIcon.Settings,
                title = "SETTINGS",
                subtitle = "Audio, voice, appearance, about",
                accent = ViceColors.Amber,
                onClick = { onNavigate(Screen.Settings) },
            )

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "10 voices + ${customVoices.size} saved custom",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            VcPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ViceIcon(VcIcon.Info, size = 16.dp, tint = ViceColors.Mint)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "No internet permission, no accounts, no analytics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Everything is processed on this device by the DSP chain in the app. " +
                            "The voice message path uses the same processing as live mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
