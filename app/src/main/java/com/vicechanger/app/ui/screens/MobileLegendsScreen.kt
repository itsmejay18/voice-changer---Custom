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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vicechanger.app.audio.RoutingCapabilityProbe
import com.vicechanger.app.ui.Screen
import com.vicechanger.app.ui.components.BannerTone
import com.vicechanger.app.ui.components.MessageBanner
import com.vicechanger.app.ui.components.MetricTile
import com.vicechanger.app.ui.components.PrimaryActionButton
import com.vicechanger.app.ui.components.ScreenHeader
import com.vicechanger.app.ui.components.SecondaryActionButton
import com.vicechanger.app.ui.components.SectionHeader
import com.vicechanger.app.ui.components.VcIcon
import com.vicechanger.app.ui.components.VcPanel
import com.vicechanger.app.ui.theme.ViceColors
import com.vicechanger.app.viewmodel.AppViewModel
import com.vicechanger.app.viewmodel.MessageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mobile Legends Mode.
 *
 * The two buttons do real work: "CHECK DEVICE ROUTING" opens the microphone and the output for
 * a fraction of a second and reports what actually happened, and "TEST VOICE" records four
 * seconds, transforms them with the armed voice and plays the result back - which is exactly
 * what a teammate would hear.
 *
 * What no button here does is inject audio into Mobile Legends, because stock Android provides
 * no way to do that. The screen says so instead of pretending.
 */
@Composable
fun MobileLegendsScreen(
    app: AppViewModel,
    message: MessageViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val preset by app.selectedPreset.collectAsStateWithLifecycle()
    val settings by app.appSettings.collectAsStateWithLifecycle()
    val state by message.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<RoutingCapabilityProbe.Report?>(null) }
    var probing by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            testing = false
            message.stopPlayback()
            if (message.state.value.stage == MessageViewModel.Stage.RECORDING) message.stopRecording()
        }
    }

    // Test Voice: record four seconds, then let the state machine move to processing.
    LaunchedEffect(testing) {
        if (!testing) return@LaunchedEffect
        message.clearAll()
        message.startRecording()
        delay(TEST_RECORD_MILLIS)
        message.stopRecording()
    }

    LaunchedEffect(state.stage, testing) {
        if (!testing) return@LaunchedEffect
        when (state.stage) {
            MessageViewModel.Stage.RECORDED -> message.process(preset, settings)
            MessageViewModel.Stage.PROCESSED -> {
                if (!state.playing) message.play()
                testing = false
            }
            else -> Unit
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
            title = "MOBILE LEGENDS MODE",
            subtitle = "Gaming voice mode for ML voice chat",
            onBack = onBack,
        )

        Spacer(Modifier.height(14.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            MessageBanner(
                text = "VICE CHANGER processes your microphone audio. Whether another application " +
                    "can receive processed microphone audio depends on Android audio routing and " +
                    "device compatibility.",
                tone = BannerTone.WARNING,
            )

            Spacer(Modifier.height(12.dp))

            VcPanel(modifier = Modifier.fillMaxWidth(), accent = ViceColors.Violet) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Armed voice",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Record a voice note in ML's chat with this voice, or use Live Mode to " +
                            "hear yourself transform in real time while you play.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader(text = "Test this device")
            PrimaryActionButton(
                text = if (probing) "CHECKING..." else "CHECK DEVICE ROUTING",
                icon = VcIcon.Route,
                onClick = {
                    if (probing) return@PrimaryActionButton
                    probing = true
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            RoutingCapabilityProbe.probe(context)
                        }
                        report = result
                        probing = false
                    }
                },
                enabled = !probing,
            )

            Spacer(Modifier.height(10.dp))

            SecondaryActionButton(
                text = when {
                    testing && state.stage == MessageViewModel.Stage.RECORDING ->
                        "RECORDING TEST... %.1f s".format(state.seconds)
                    testing -> "PROCESSING..."
                    state.playing -> "PLAYING RESULT - TAP TO STOP"
                    else -> "TEST VOICE (4 s RECORD, TRANSFORM, PLAY)"
                },
                icon = if (state.playing) VcIcon.Stop else VcIcon.Mic,
                onClick = {
                    if (state.playing) {
                        message.stopPlayback()
                    } else if (!testing) {
                        testing = true
                    }
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                accent = ViceColors.Mint,
            )

            if (testing) {
                Spacer(Modifier.height(10.dp))
                MessageBanner(
                    text = "Speak now. VICE CHANGER is recording raw audio, will run it through the " +
                        "${preset.name} chain and play back exactly what a teammate would hear.",
                    tone = BannerTone.INFO,
                )
            }

            report?.let { result ->
                Spacer(Modifier.height(16.dp))
                SectionHeader(text = "Routing report")
                VcPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(
                                label = "Mic capture",
                                value = if (result.microphoneOpened) "OK" else "FAILED",
                                accent = if (result.microphoneOpened) ViceColors.Mint else ViceColors.Red,
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "Level seen",
                                value = "%.0f dB".format(result.capturePeakDbfs),
                                accent = if (result.microphoneDeliveredAudio) ViceColors.Mint else ViceColors.Amber,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(
                                label = "Output",
                                value = if (result.playbackOpened) "OK" else "FAILED",
                                accent = if (result.playbackOpened) ViceColors.Mint else ViceColors.Red,
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "Out buffer",
                                value = "%.0f ms".format(result.playbackBufferMs),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(
                                label = "Mic -> ML",
                                value = "NOT POSSIBLE",
                                accent = ViceColors.Red,
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "Headset",
                                value = if (result.headsetConnected) "CONNECTED" else "NONE",
                                accent = if (result.headsetConnected) ViceColors.Mint else ViceColors.Amber,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        result.details.forEach { line ->
                            Text(
                                text = "- $line",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        MessageBanner(text = result.verdict, tone = BannerTone.ERROR)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionHeader(text = "How to use this with Mobile Legends")
            VcPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Step(
                        "1",
                        "Test the voice here first. Four seconds is enough to hear whether it sounds " +
                            "like you want it.",
                    )
                    Step(
                        "2",
                        "Open Voice Message mode, pick the voice, record the callout you want - " +
                            "\"enemy missing\", \"push mid\" - and let VICE CHANGER process it.",
                    )
                    Step(
                        "3",
                        "Use SHARE to send it to ML's chat, or SAVE and attach it from the music " +
                            "library. Your teammates hear the female voice in a note rather than live.",
                    )
                    Step(
                        "4",
                        "For live talking, use Live Mode with headphones: it plays the transformed " +
                            "voice to you. If you are on a party call with a Bluetooth headset, the " +
                            "headset microphone still sends your natural voice - Android gives no app " +
                            "a way around that.",
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            MessageBanner(
                text = "No root exploits, no accessibility abuse, no hidden background microphone. " +
                    "VICE CHANGER does not claim to replace your microphone inside any other app.",
                tone = BannerTone.INFO,
            )

            Spacer(Modifier.height(12.dp))

            SecondaryActionButton(
                text = "OPEN VOICE MESSAGE MODE",
                icon = VcIcon.Chat,
                onClick = { onNavigate(Screen.VoiceMessage) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            color = ViceColors.Rose,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val TEST_RECORD_MILLIS = 4000L
