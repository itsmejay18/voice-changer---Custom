package com.vicechanger.app.voice

import com.vicechanger.app.utils.KeyValueStore
import com.vicechanger.app.utils.StoreValues

/**
 * Single repository for voices: the ten built-ins plus whatever the user saved from the
 * Custom Girl screen.
 *
 * Custom presets are stored as one line per preset so the format stays readable, and the
 * encode/decode pair is pure - the unit tests round-trip it without any Android dependency.
 */
class VoicePresetManager(private val store: KeyValueStore) {

    val builtInPresets: List<VoicePreset> get() = BuiltInVoices.ALL

    private var cache: List<VoicePreset>? = null

    val customPresets: List<VoicePreset>
        get() = cache ?: decodeCustom(store.getString(KEY_CUSTOM_PRESETS)).also { cache = it }

    val allPresets: List<VoicePreset> get() = builtInPresets + customPresets

    fun byId(id: String): VoicePreset =
        allPresets.firstOrNull { it.id == id } ?: BuiltInVoices.NATURAL_GIRL

    /** True when the id belongs to a saved custom preset rather than the ten built-ins. */
    fun isSavedCustom(id: String): Boolean = customPresets.any { it.id == id }

    /**
     * Save a configuration as a *new* voice. The id is always freshly allocated, so saving
     * twice never silently overwrites an earlier voice; use [updateCustom] to replace one.
     */
    fun saveCustom(preset: VoicePreset): VoicePreset {
        val sanitized = preset.copy(
            id = nextCustomId(),
            isCustom = true,
        ).clamped()
        val updated = customPresets.filterNot { it.id == sanitized.id } + sanitized
        persist(updated)
        return sanitized
    }

    /** Replace an existing saved voice in place. Returns null when the id is not a saved one. */
    fun updateCustom(preset: VoicePreset): VoicePreset? {
        if (!isSavedCustom(preset.id)) return null
        val sanitized = preset.copy(isCustom = true).clamped()
        persist(customPresets.map { if (it.id == sanitized.id) sanitized else it })
        return sanitized
    }

    fun deleteCustom(id: String) {
        persist(customPresets.filterNot { it.id == id })
    }

    fun nextCustomId(): String {
        val existing = customPresets.mapNotNull { it.id.removePrefix(CUSTOM_PREFIX).toIntOrNull() }
        val next = (existing.maxOrNull() ?: 0) + 1
        return "$CUSTOM_PREFIX$next"
    }

    /** Last custom slider values, so the Custom Girl screen reopens where the user left it. */
    fun loadCustomDraft(): VoicePreset {
        val stored = store.getString(KEY_CUSTOM_DRAFT) ?: return BuiltInVoices.CUSTOM_GIRL
        return decodeLine(stored)?.copy(isCustom = true) ?: BuiltInVoices.CUSTOM_GIRL
    }

    fun saveCustomDraft(preset: VoicePreset) {
        store.putString(KEY_CUSTOM_DRAFT, encodeLine(preset.copy(isCustom = true).clamped()))
    }

    private fun persist(presets: List<VoicePreset>) {
        cache = presets
        store.putString(KEY_CUSTOM_PRESETS, presets.joinToString("\n") { encodeLine(it) })
    }

    companion object {
        const val KEY_CUSTOM_PRESETS = "custom_presets"
        const val KEY_CUSTOM_DRAFT = "custom_preset_draft"
        private const val CUSTOM_PREFIX = "custom_"
        private const val FIELD_SEPARATOR = '|'
        private const val LIST_SEPARATOR = ';'

        /**
         * One preset as a single line:
         * id|name|tagline|pitch|formant|brightness|resonance|effect|noise|highpass|ratio
         */
        fun encodeLine(preset: VoicePreset): String = listOf(
            sanitize(preset.id),
            sanitize(preset.name),
            sanitize(preset.tagline),
            preset.pitch.toString(),
            preset.formant.toString(),
            preset.brightness.toString(),
            preset.resonance.toString(),
            preset.effectStrength.toString(),
            preset.noiseSuppression.toString(),
            preset.highpassHz.toString(),
            preset.compressionRatio.toString(),
        ).joinToString(FIELD_SEPARATOR.toString())

        fun decodeLine(line: String): VoicePreset? {
            if (line.isBlank()) return null
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size < 8) return null
            val id = parts[0].trim()
            if (id.isBlank()) return null
            return VoicePreset(
                id = id,
                name = parts[1].ifBlank { "Custom Girl" },
                tagline = parts.getOrElse(2) { "Saved custom voice" },
                pitch = StoreValues.parseFloat(parts.getOrNull(3), 4.5f),
                formant = StoreValues.parseFloat(parts.getOrNull(4), 1.12f),
                brightness = StoreValues.parseFloat(parts.getOrNull(5), 2f),
                resonance = StoreValues.parseFloat(parts.getOrNull(6), 1.5f),
                effectStrength = StoreValues.parseFloat(parts.getOrNull(7), 0.25f),
                noiseSuppression = StoreValues.parseFloat(parts.getOrNull(8), 0.35f),
                highpassHz = StoreValues.parseFloat(parts.getOrNull(9), 75f),
                compressionRatio = StoreValues.parseFloat(parts.getOrNull(10), 4f),
                isCustom = true,
            ).clamped()
        }

        fun decodeCustom(raw: String?): List<VoicePreset> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split("\n").mapNotNull { decodeLine(it.trim()) }
        }

        /** Names may contain the separators; strip them so the format cannot be broken. */
        private fun sanitize(value: String): String =
            value.replace(FIELD_SEPARATOR, ' ').replace('\n', ' ').replace(LIST_SEPARATOR, ' ').trim()
    }
}
