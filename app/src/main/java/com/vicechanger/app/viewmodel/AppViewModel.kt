package com.vicechanger.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vicechanger.app.audio.AudioDeviceMonitor
import com.vicechanger.app.settings.AppSettings
import com.vicechanger.app.settings.SettingsManager
import com.vicechanger.app.ui.Screen
import com.vicechanger.app.utils.SharedPrefsStore
import com.vicechanger.app.voice.VoicePreset
import com.vicechanger.app.voice.VoicePresetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    /** Voice list for the picker: the ten built-ins then any saved custom voices. */
    fun allPresets(): List<VoicePreset> = presets.allPresets

    fun consumeMessage(): String? {
        val current = _message.value
        _message.update { null }
        return current
    }

    override fun onCleared() {
        devices.stop()
        super.onCleared()
    }
}
