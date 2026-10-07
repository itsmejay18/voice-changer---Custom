package com.vicechanger.app.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.AudioEngine
import com.vicechanger.app.audio.AudioFailure
import com.vicechanger.app.settings.AppSettings
import com.vicechanger.app.voice.VoicePreset
import com.vicechanger.app.voice.VoiceTransformer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live Mode. Owns the audio engine for the whole activity lifetime and polls it for the
 * measured values the UI shows.
 *
 * The engine is stopped in [stop] and again in [onCleared], and again when the screen leaves
 * the composition - three chances to release the microphone, because a leaked AudioRecord is
 * the classic way a voice app stays "busy" for other apps.
 */
class LiveViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val running: Boolean = false,
        val muted: Boolean = false,
        val inputLevel: Float = 0f,
        val outputLevel: Float = 0f,
        val outputPeakDb: Float = -60f,
        val latencyMs: Float = 0f,
        val blocksProcessed: Long = 0,
        val underruns: Long = 0,
        val inputDevice: String = "Default mic",
        val outputDevice: String = "Default output",
        val failure: AudioFailure? = null,
        val warmup: Boolean = false,
        val startedAtMillis: Long = 0,
    )

    private val engine = AudioEngine(application)

    private val audioManager: AudioManager? =
        application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * A phone call or another player taking the output stops the engine and says why, instead
     * of leaving a half-dead AudioTrack running.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                if (_state.value.running) {
                    stop()
                    _state.value = _state.value.copy(failure = AudioFailure.AUDIO_FOCUS_LOST)
                }
            }
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** Push a new voice / settings combination into the running engine. */
    fun applyVoice(preset: VoicePreset, settings: AppSettings) {
        engine.setParams(VoiceTransformer.transform(preset, settings))
        engine.setInputGainDb(settings.inputGainDb)
        engine.setOutputVolume(settings.outputVolume)
        engine.setPreferredDevices(settings.preferredInputDeviceId, settings.preferredOutputDeviceId)
    }

    fun start(preset: VoicePreset, settings: AppSettings) {
        if (_state.value.running) return
        requestAudioFocus()
        val params = VoiceTransformer.transform(preset, settings)
        val config = AudioEngine.EngineConfig(
            blockSize = if (settings.lowLatencyMode) AudioConfig.BLOCK_SIZE_LOW_LATENCY else AudioConfig.BLOCK_SIZE,
            lowLatencyMode = settings.lowLatencyMode,
            outputBufferBlocks = if (settings.lowLatencyMode) 3 else 5,
            params = params,
            inputGainDb = settings.inputGainDb,
            outputVolume = settings.outputVolume,
            preferredInputDeviceId = settings.preferredInputDeviceId,
            preferredOutputDeviceId = settings.preferredOutputDeviceId,
        )
        val result = engine.start(config)
        result.onSuccess {
            _state.value = _state.value.copy(
                running = true,
                failure = null,
                muted = false,
                startedAtMillis = System.currentTimeMillis(),
            )
            startPolling()
        }
        result.onFailure { error ->
            val failure = (error as? com.vicechanger.app.audio.AudioException)?.failure
                ?: AudioFailure.AUDIO_INIT_FAILED
            _state.value = _state.value.copy(running = false, failure = failure)
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        engine.stop()
        abandonAudioFocus()
        _state.value = _state.value.copy(
            running = false,
            inputLevel = 0f,
            outputLevel = 0f,
            latencyMs = 0f,
        )
    }

    @Suppress("unused")
    private val focusAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(focusAttributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()

    private fun requestAudioFocus() {
        runCatching { audioManager?.requestAudioFocus(focusRequest) }
    }

    private fun abandonAudioFocus() {
        runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
    }

    fun toggleMute() {
        val muted = !engine.isMuted
        engine.setMuted(muted)
        _state.value = _state.value.copy(muted = muted)
    }

    fun clearFailure() {
        _state.value = _state.value.copy(failure = null)
    }

    /** Samples the engine is holding back right now - shown next to the latency readout. */
    fun bufferedSamples(): Int = engine.bufferedSamples()

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(60)
                val snapshot = engine.snapshot()
                val failure = engine.lastFailure
                if (!snapshot.running && failure != null) {
                    _state.value = _state.value.copy(
                        running = false,
                        inputLevel = 0f,
                        outputLevel = 0f,
                        failure = failure,
                    )
                    pollJob?.cancel()
                    break
                }
                _state.value = _state.value.copy(
                    running = snapshot.running,
                    muted = snapshot.muted,
                    inputLevel = snapshot.inputLevel01,
                    outputLevel = snapshot.outputLevel01,
                    outputPeakDb = snapshot.outputPeakDb,
                    latencyMs = snapshot.latencyMs,
                    blocksProcessed = snapshot.blocksProcessed,
                    underruns = snapshot.underruns,
                    inputDevice = snapshot.inputDeviceLabel,
                    outputDevice = snapshot.outputDeviceLabel,
                    warmup = snapshot.running && snapshot.blocksProcessed < 20,
                )
            }
        }
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }
}
