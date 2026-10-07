package com.vicechanger.app.settings

import com.vicechanger.app.utils.KeyValueStore
import com.vicechanger.app.utils.StoreValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/**
 * Everything the user can configure. Kept as one immutable value so the whole UI can observe
 * a single StateFlow and the audio engine can be handed a consistent snapshot.
 */
data class AppSettings(
    val defaultPresetId: String = "natural_girl",
    val inputGainDb: Float = 0f,
    val outputVolume: Float = 0.9f,
    val noiseSuppression: Float = 0.35f,
    val echoReduction: Boolean = true,
    val echoReductionAmountDb: Float = 18f,
    val lowLatencyMode: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val preferredInputDeviceId: Int = -1,
    val preferredOutputDeviceId: Int = -1,
    val customVoiceDraft: String = "",
) {
    fun clamped(): AppSettings = copy(
        inputGainDb = inputGainDb.coerceIn(-12f, 18f),
        outputVolume = outputVolume.coerceIn(0f, 1f),
        noiseSuppression = noiseSuppression.coerceIn(0f, 1f),
        echoReductionAmountDb = echoReductionAmountDb.coerceIn(0f, 30f),
    )
}

/**
 * Pure settings codec. Splitting it out of the store is what makes settings behaviour
 * testable: the unit tests map a settings object to a string map and back with no Android
 * framework in the loop.
 */
object SettingsCodec {

    const val KEY_PRESET = "settings_default_preset"
    const val KEY_INPUT_GAIN = "settings_input_gain_db"
    const val KEY_OUTPUT_VOLUME = "settings_output_volume"
    const val KEY_NOISE_SUPPRESSION = "settings_noise_suppression"
    const val KEY_ECHO_REDUCTION = "settings_echo_reduction"
    const val KEY_ECHO_AMOUNT = "settings_echo_amount_db"
    const val KEY_LOW_LATENCY = "settings_low_latency"
    const val KEY_THEME = "settings_theme_mode"
    const val KEY_INPUT_DEVICE = "settings_input_device_id"
    const val KEY_OUTPUT_DEVICE = "settings_output_device_id"
    const val KEY_CUSTOM_DRAFT = "settings_custom_draft"

    fun encode(settings: AppSettings): Map<String, String> = mapOf(
        KEY_PRESET to settings.defaultPresetId,
        KEY_INPUT_GAIN to settings.inputGainDb.toString(),
        KEY_OUTPUT_VOLUME to settings.outputVolume.toString(),
        KEY_NOISE_SUPPRESSION to settings.noiseSuppression.toString(),
        KEY_ECHO_REDUCTION to settings.echoReduction.toString(),
        KEY_ECHO_AMOUNT to settings.echoReductionAmountDb.toString(),
        KEY_LOW_LATENCY to settings.lowLatencyMode.toString(),
        KEY_THEME to settings.themeMode.name,
        KEY_INPUT_DEVICE to settings.preferredInputDeviceId.toString(),
        KEY_OUTPUT_DEVICE to settings.preferredOutputDeviceId.toString(),
        KEY_CUSTOM_DRAFT to settings.customVoiceDraft,
    )

    fun decode(read: (String) -> String?): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            defaultPresetId = read(KEY_PRESET) ?: defaults.defaultPresetId,
            inputGainDb = StoreValues.parseFloat(read(KEY_INPUT_GAIN), defaults.inputGainDb),
            outputVolume = StoreValues.parseFloat(read(KEY_OUTPUT_VOLUME), defaults.outputVolume),
            noiseSuppression = StoreValues.parseFloat(
                read(KEY_NOISE_SUPPRESSION),
                defaults.noiseSuppression,
            ),
            echoReduction = StoreValues.parseBoolean(
                read(KEY_ECHO_REDUCTION),
                defaults.echoReduction,
            ),
            echoReductionAmountDb = StoreValues.parseFloat(
                read(KEY_ECHO_AMOUNT),
                defaults.echoReductionAmountDb,
            ),
            lowLatencyMode = StoreValues.parseBoolean(read(KEY_LOW_LATENCY), defaults.lowLatencyMode),
            themeMode = runCatching { ThemeMode.valueOf(read(KEY_THEME) ?: "") }
                .getOrDefault(defaults.themeMode),
            preferredInputDeviceId = StoreValues.parseInt(
                read(KEY_INPUT_DEVICE),
                defaults.preferredInputDeviceId,
            ),
            preferredOutputDeviceId = StoreValues.parseInt(
                read(KEY_OUTPUT_DEVICE),
                defaults.preferredOutputDeviceId,
            ),
            customVoiceDraft = read(KEY_CUSTOM_DRAFT) ?: defaults.customVoiceDraft,
        ).clamped()
    }
}

/**
 * Observable settings store. Mutations go through [update] so persistence and the exposed
 * state can never disagree.
 */
class SettingsManager(private val store: KeyValueStore) {

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun load(): AppSettings = SettingsCodec.decode { key -> store.getString(key) }

    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val updated = transform(_settings.value).clamped()
        for ((key, value) in SettingsCodec.encode(updated)) store.putString(key, value)
        _settings.value = updated
        return updated
    }

    fun setDefaultPreset(id: String) = update { it.copy(defaultPresetId = id) }
    fun setInputGainDb(db: Float) = update { it.copy(inputGainDb = db) }
    fun setOutputVolume(volume: Float) = update { it.copy(outputVolume = volume) }
    fun setNoiseSuppression(value: Float) = update { it.copy(noiseSuppression = value) }
    fun setEchoReduction(enabled: Boolean) = update { it.copy(echoReduction = enabled) }
    fun setEchoReductionAmount(db: Float) = update { it.copy(echoReductionAmountDb = db) }
    fun setLowLatencyMode(enabled: Boolean) = update { it.copy(lowLatencyMode = enabled) }
    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setPreferredInputDevice(id: Int) = update { it.copy(preferredInputDeviceId = id) }
    fun setPreferredOutputDevice(id: Int) = update { it.copy(preferredOutputDeviceId = id) }
    fun setCustomVoiceDraft(draft: String) = update { it.copy(customVoiceDraft = draft) }
}
