package com.vicechanger.app.voice

import com.vicechanger.app.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Custom voice presets: saving, listing, deleting, surviving a restart (a new manager over the
 * same store) and never letting a user-typed name break the storage format.
 */
class CustomPresetStorageTest {

    @Test
    fun `saving a custom preset makes it available to every mode`() {
        val store = InMemoryKeyValueStore()
        val manager = VoicePresetManager(store)

        assertEquals(10, manager.allPresets.size)
        assertEquals(0, manager.customPresets.size)

        val saved = manager.saveCustom(
            BuiltInVoices.CUSTOM_GIRL.copy(name = "Streaming Voice", pitch = 7f, formant = 1.2f),
        )

        assertTrue(saved.id.startsWith("custom_"))
        assertEquals("Streaming Voice", saved.name)
        assertEquals(11, manager.allPresets.size)
        assertEquals(saved, manager.byId(saved.id))
        assertTrue(manager.isSavedCustom(saved.id))
        assertFalse(manager.isSavedCustom("natural_girl"))
        assertEquals(7f, saved.pitch, 1e-3f)
    }

    @Test
    fun `saving twice never overwrites an earlier voice`() {
        val manager = VoicePresetManager(InMemoryKeyValueStore())
        val first = manager.saveCustom(BuiltInVoices.CUSTOM_GIRL.copy(name = "First"))
        val second = manager.saveCustom(BuiltInVoices.CUSTOM_GIRL.copy(name = "First"))
        assertFalse(first.id == second.id)
        assertEquals(2, manager.customPresets.size)
    }

    @Test
    fun `updateCustom replaces an existing saved voice and ignores unknown ids`() {
        val manager = VoicePresetManager(InMemoryKeyValueStore())
        val saved = manager.saveCustom(BuiltInVoices.CUSTOM_GIRL.copy(name = "Before"))

        val updated = manager.updateCustom(saved.copy(name = "After", pitch = 9f))
        assertEquals("After", updated?.name)
        assertEquals(1, manager.customPresets.size)
        assertEquals(9f, manager.customPresets.single().pitch, 1e-3f)

        assertEquals(null, manager.updateCustom(BuiltInVoices.NATURAL_GIRL.copy(name = "Nope")))
        assertEquals(1, manager.customPresets.size)
    }

    @Test
    fun `custom presets survive a restart`() {
        val store = InMemoryKeyValueStore()
        val first = VoicePresetManager(store)
        val saved = first.saveCustom(BuiltInVoices.CUSTOM_GIRL.copy(name = "Persisted", pitch = 3.5f))

        val second = VoicePresetManager(store)
        val reloaded = second.customPresets.firstOrNull { it.id == saved.id }
        assertNotNull("custom preset must be re-read from the store", reloaded)
        assertEquals("Persisted", reloaded!!.name)
        assertEquals(3.5f, reloaded.pitch, 1e-3f)
    }

    @Test
    fun `saved values are clamped on the way in and on the way out`() {
        val store = InMemoryKeyValueStore()
        val manager = VoicePresetManager(store)
        val saved = manager.saveCustom(BuiltInVoices.CUSTOM_GIRL.copy(pitch = 100f, formant = 5f))
        assertTrue(saved.pitch <= com.vicechanger.app.audio.AudioConfig.MAX_PITCH_SEMITONES)
        assertTrue(saved.formant <= com.vicechanger.app.audio.AudioConfig.MAX_FORMANT)

        // Even a hand-edited store is clamped when read back.
        store.putString(
            VoicePresetManager.KEY_CUSTOM_PRESETS,
            "custom_9|Broken|tag|999|9|99|-99|5|0|50|0",
        )
        val reparsed = VoicePresetManager(store).customPresets.first()
        assertTrue(reparsed.pitch <= com.vicechanger.app.audio.AudioConfig.MAX_PITCH_SEMITONES)
        assertTrue(reparsed.effectStrength <= 1f)
        assertTrue(reparsed.formant <= com.vicechanger.app.audio.AudioConfig.MAX_FORMANT)
    }

    @Test
    fun `deleting removes only the requested preset`() {
        val store = InMemoryKeyValueStore()
        val manager = VoicePresetManager(store)
        val first = manager.saveCustom(BuiltInVoices.CUSTOM_GIRL.copy(name = "One"))
        val second = manager.saveCustom(BuiltInVoices.CUSTOM_GIRL.copy(name = "Two"))

        manager.deleteCustom(first.id)

        assertEquals(1, manager.customPresets.size)
        assertEquals(second.id, manager.customPresets.first().id)
        assertEquals(10, manager.builtInPresets.size)
    }

    @Test
    fun `names containing the storage separators cannot break the format`() {
        val store = InMemoryKeyValueStore()
        val manager = VoicePresetManager(store)
        val saved = manager.saveCustom(
            BuiltInVoices.CUSTOM_GIRL.copy(name = "Bad|Name\nWith;Separators"),
        )
        val reloaded = VoicePresetManager(store).customPresets.single()
        assertEquals(saved.id, reloaded.id)
        assertFalse(reloaded.name.contains('|'))
        assertFalse(reloaded.name.contains('\n'))
    }

    @Test
    fun `line encoding round trips every field`() {
        val preset = BuiltInVoices.CUSTOM_GIRL.copy(
            id = "custom_42",
            name = "Round Trip",
            tagline = "saved",
            pitch = -3.5f,
            formant = 0.95f,
            brightness = -4.5f,
            resonance = 5.5f,
            effectStrength = 0.85f,
            noiseSuppression = 0.15f,
            highpassHz = 120f,
            compressionRatio = 9f,
        )
        val decoded = VoicePresetManager.decodeLine(VoicePresetManager.encodeLine(preset))
        assertNotNull(decoded)
        assertEquals(preset.id, decoded!!.id)
        assertEquals(preset.name, decoded.name)
        assertEquals(preset.pitch, decoded.pitch, 1e-3f)
        assertEquals(preset.formant, decoded.formant, 1e-3f)
        assertEquals(preset.brightness, decoded.brightness, 1e-3f)
        assertEquals(preset.resonance, decoded.resonance, 1e-3f)
        assertEquals(preset.effectStrength, decoded.effectStrength, 1e-3f)
        assertEquals(preset.highpassHz, decoded.highpassHz, 1e-3f)
    }

    @Test
    fun `malformed lines are ignored instead of crashing`() {
        assertTrue(VoicePresetManager.decodeCustom(null).isEmpty())
        assertTrue(VoicePresetManager.decodeCustom("").isEmpty())
        assertTrue(VoicePresetManager.decodeCustom("garbage").isEmpty())
        assertTrue(VoicePresetManager.decodeCustom("a|b|c").isEmpty())
    }

    @Test
    fun `custom ids increment and the draft is remembered`() {
        val store = InMemoryKeyValueStore()
        val manager = VoicePresetManager(store)
        val first = manager.saveCustom(BuiltInVoices.CUSTOM_GIRL.copy(name = "A"))
        val second = manager.saveCustom(BuiltInVoices.CUSTOM_GIRL.copy(name = "B"))
        assertFalse(first.id == second.id)

        val draft = BuiltInVoices.CUSTOM_GIRL.copy(pitch = 9.5f, name = "Draft")
        manager.saveCustomDraft(draft)
        val reloaded = VoicePresetManager(store).loadCustomDraft()
        assertEquals(9.5f, reloaded.pitch, 1e-3f)
    }

    @Test
    fun `a fresh store yields the default custom voice`() {
        val manager = VoicePresetManager(InMemoryKeyValueStore())
        assertEquals(BuiltInVoices.CUSTOM_GIRL.pitch, manager.loadCustomDraft().pitch, 1e-4f)
    }

    @Test
    fun `next custom id counts only well formed ids`() {
        val store = InMemoryKeyValueStore(
            mapOf(
                VoicePresetManager.KEY_CUSTOM_PRESETS to
                    "custom_3|Three|t|4|1.1|2|1|0.2|0.3|75|4\ncustom_x|NotNumeric|t|4|1.1|2|1|0.2|0.3|75|4",
            ),
        )
        val manager = VoicePresetManager(store)
        assertEquals("custom_4", manager.nextCustomId())
    }
}
