package com.vicechanger.app.viewmodel

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vicechanger.app.audio.AudioDeviceMonitor
import com.vicechanger.app.recording.WavCodec
import com.vicechanger.app.settings.AppSettings
import com.vicechanger.app.settings.SettingsManager
import com.vicechanger.app.ui.Screen
import com.vicechanger.app.utils.SharedPrefsStore
import com.vicechanger.app.voice.DspSelfTest
import com.vicechanger.app.voice.VoicePreset
import com.vicechanger.app.voice.VoicePresetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the state that every screen shares: which screen is showing, which voice is armed, the
 * custom preset the user is editing and the settings. Activity-scoped, so rotating the phone
 * keeps the selection - and, because Live mode keeps its engine in its own ViewModel, rotating
 * does not interrupt audio either.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SharedPrefsStore(application)
    val settings: SettingsManager = SettingsManager(store)
    val presets: VoicePresetManager = VoicePresetManager(store)
    val devices: AudioDeviceMonitor = AudioDeviceMonitor(application)

    private val backStack = ArrayDeque<Screen>()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _selectedPreset = MutableStateFlow(presets.byId(settings.current.defaultPresetId))
    val selectedPreset: StateFlow<VoicePreset> = _selectedPreset.asStateFlow()

    private val _customDraft = MutableStateFlow(presets.loadCustomDraft())
    val customDraft: StateFlow<VoicePreset> = _customDraft.asStateFlow()

    private val _savedCustom = MutableStateFlow(presets.customPresets)
    val savedCustom: StateFlow<List<VoicePreset>> = _savedCustom.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * On-device proof that the DSP chain works: runs a synthetic vowel through the real chain for
     * the selected voice, reports the measured pitch/envelope change and keeps the processed audio
     * so the user can hear it without a microphone in the loop.
     */
    data class SelfTestState(
        val running: Boolean = false,
        val summary: String? = null,
        val passed: Boolean = false,
        val playing: Boolean = false,
        val file: File? = null,
        val error: String? = null,
    )

    private val _selfTest = MutableStateFlow(SelfTestState())
    val selfTest: StateFlow<SelfTestState> = _selfTest.asStateFlow()
    private var selfTestPlayer: MediaPlayer? = null

    val appSettings: StateFlow<AppSettings> get() = settings.settings

    init {
        devices.start()
    }

    fun navigate(screen: Screen) {
        if (screen == _screen.value) return
        backStack.addLast(_screen.value)
        _screen.value = screen
    }

    /** @return true when the back press was consumed by the in-app stack. */
    fun back(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        _screen.value = previous
        return true
    }

    fun selectPreset(preset: VoicePreset) {
        _selectedPreset.value = preset
        settings.setDefaultPreset(preset.id)
        if (preset.id == com.vicechanger.app.voice.BuiltInVoices.CUSTOM_ID) {
            _customDraft.value = preset
        }
    }

    fun updateCustomDraft(preset: VoicePreset) {
        val clamped = preset.clamped()
        _customDraft.value = clamped
        presets.saveCustomDraft(clamped)
    }

    fun resetCustomDraft() {
        val fresh = com.vicechanger.app.voice.BuiltInVoices.CUSTOM_GIRL
        _customDraft.value = fresh
        presets.saveCustomDraft(fresh)
    }

    fun saveCustomPreset(name: String): VoicePreset {
        val draft = _customDraft.value
        val saved = presets.saveCustom(
            draft.copy(
                name = name.ifBlank { "Custom Girl ${_savedCustom.value.size + 1}" },
                tagline = "Saved custom voice - ${draft.summary()}",
            ),
        )
        _savedCustom.value = presets.customPresets
        _selectedPreset.value = saved
        settings.setDefaultPreset(saved.id)
        _message.value = "Saved voice \"${saved.name}\""
        return saved
    }

    fun deleteCustomPreset(id: String) {
        presets.deleteCustom(id)
        _savedCustom.value = presets.customPresets
        if (_selectedPreset.value.id == id) {
            _selectedPreset.value = com.vicechanger.app.voice.BuiltInVoices.NATURAL_GIRL
            settings.setDefaultPreset(_selectedPreset.value.id)
        }
        _message.value = "Custom voice deleted"
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings.update(transform)
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    fun runSelfTest(preset: VoicePreset, settings: AppSettings) {
        if (_selfTest.value.running) return
        stopSelfTestPlayback()
        _selfTest.value = SelfTestState(running = true)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    val outcome = DspSelfTest.run(preset, settings)
                    val file = File(getApplication<Application>().cacheDir, "vc_self_test_${preset.id}.wav")
                    WavCodec.write(file, outcome.processed, outcome.sampleRate)
                    outcome.result to file
                }
            }
            outcome.fold(
                onSuccess = { (result, file) ->
                    _selfTest.value = SelfTestState(
                        running = false,
                        summary = result.summary(),
                        passed = result.passed,
                        file = file,
                    )
                    playSelfTest()
                },
                onFailure = { error ->
                    _selfTest.value = SelfTestState(
                        running = false,
                        error = "Self test failed: ${error.message ?: "unknown error"}",
                    )
                },
            )
        }
    }

    fun playSelfTest() {
        val file = _selfTest.value.file ?: return
        selfTestPlayer?.let { runCatching { it.release() } }
        selfTestPlayer = null
        val player = MediaPlayer()
        runCatching {
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                stopSelfTestPlayback()
            }
            player.prepare()
            player.start()
            selfTestPlayer = player
            _selfTest.value = _selfTest.value.copy(playing = true, error = null)
        }.onFailure {
            runCatching { player.release() }
            _selfTest.value = _selfTest.value.copy(
                playing = false,
                error = "The processed test tone could not be played back.",
            )
        }
    }

    fun stopSelfTestPlayback() {
        selfTestPlayer?.let { runCatching { it.release() } }
        selfTestPlayer = null
        if (_selfTest.value.playing) _selfTest.value = _selfTest.value.copy(playing = false)
    }

    /** True when a headset is connected right now (used by the diagnostics panel). */
    fun headsetConnected(): Boolean = devices.refresh().hasHeadset

    fun headsetLabel(): String? =
        devices.refresh().outputs.firstOrNull { it.isBluetooth || it.isWired }?.label

    /** Voice list for the picker: the ten built-ins then any saved custom voices. */
    fun allPresets(): List<VoicePreset> = presets.allPresets

    fun consumeMessage(): String? {
        val current = _message.value
        _message.update { null }
        return current
    }

    override fun onCleared() {
        stopSelfTestPlayback()
        devices.stop()
        super.onCleared()
    }
}
