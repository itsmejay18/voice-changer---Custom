package com.vicechanger.app.voice

import com.vicechanger.app.InMemoryKeyValueStore
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.dsp.Biquad
import com.vicechanger.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePresetTest {

    @Test
    fun `there are exactly ten built in voices with unique ids and names`() {
        val presets = BuiltInVoices.ALL
        assertEquals(10, presets.size)
        assertEquals(10, presets.map { it.id }.toSet().size)
        assertEquals(10, presets.map { it.name }.toSet().size)
        assertEquals(
            listOf(
                "Natural Girl", "Soft Girl", "Cute Girl", "Gamer Girl", "Mature Woman",
                "Bright Girl", "Deep Female", "Anime Style", "Robot Girl", "Custom Girl",
            ),
            presets.map { it.name },
        )
    }

    @Test
    fun `every shipped value is inside the supported range`() {
        for (preset in BuiltInVoices.ALL) {
            assertTrue(preset.id.isNotBlank())
            assertTrue(preset.tagline.isNotBlank())
            assertEquals("${preset.name} is not clamped", preset, preset.clamped())
            assertTrue(preset.pitch in AudioConfig.MIN_PITCH_SEMITONES..AudioConfig.MAX_PITCH_SEMITONES)
            assertTrue(preset.formant in AudioConfig.MIN_FORMANT..AudioConfig.MAX_FORMANT)
            assertTrue(preset.brightness in -8f..8f)
            assertTrue(preset.resonance in -6f..8f)
            assertTrue(preset.effectStrength in 0f..1f)
            assertTrue(preset.noiseSuppression in 0f..1f)
        }
    }

    @Test
    fun `the specification's ten voices are present by name`() {
        val required = listOf(
            "Natural Girl", "Soft Girl", "Cute Girl", "Gamer Girl", "Mature Woman",
            "Bright Girl", "Deep Female", "Anime Style", "Robot Girl", "Custom Girl",
        )
        for (name in required) {
            assertNotNull("missing voice $name", BuiltInVoices.ALL.firstOrNull { it.name == name })
        }
    }

    @Test
    fun `out of range values are clamped instead of throwing`() {
        val wild = BuiltInVoices.NATURAL_GIRL.copy(
            pitch = 99f,
            formant = 9f,
            brightness = 40f,
            resonance = -40f,
            effectStrength = 5f,
        ).clamped()
        assertEquals(AudioConfig.MAX_PITCH_SEMITONES, wild.pitch, 0f)
        assertEquals(AudioConfig.MAX_FORMANT, wild.formant, 0f)
        assertEquals(8f, wild.brightness, 0f)
        assertEquals(-6f, wild.resonance, 0f)
        assertEquals(1f, wild.effectStrength, 0f)
    }

    @Test
    fun `lookup falls back to the natural voice`() {
        assertEquals(BuiltInVoices.NATURAL_GIRL, BuiltInVoices.byId("does_not_exist"))
        assertEquals(BuiltInVoices.ROBOT_GIRL, BuiltInVoices.byId("robot_girl"))
    }

    @Test
    fun `transformer maps the robot voice to the modulation effect and nothing else`() {
        val robot = VoiceTransformer.transform(BuiltInVoices.ROBOT_GIRL, AppSettings())
        assertTrue(robot.ringModDepth > 0.1f)
        assertTrue(robot.combEnabled)
        assertEquals(VoiceTransformer.ROBOT_MODULATION_HZ, robot.ringModHz, 0.01f)

        val natural = VoiceTransformer.transform(BuiltInVoices.NATURAL_GIRL, AppSettings())
        assertEquals(0f, natural.ringModDepth, 0f)
        assertFalse(natural.combEnabled)
    }

    @Test
    fun `transformer always builds a pitch and formant chain from the preset`() {
        for (preset in BuiltInVoices.ALL) {
            val params = VoiceTransformer.transform(preset, AppSettings())
            assertEquals(preset.pitch, params.pitchSemitones, 1e-4f)
            assertEquals(preset.formant, params.formantRatio, 1e-4f)
            assertTrue(params.needsPitch)
            assertEquals(3, params.eqStages.size)
            assertTrue(params.eqStages.any { it.kind == Biquad.Kind.HIGHSHELF })
            assertTrue(params.eqStages.any { it.kind == Biquad.Kind.PEAKING })
            assertTrue(params.outputGainDb in -6f..6f)
        }
    }

    @Test
    fun `brightness drives the shelf gain and pitch drives the pitch ratio`() {
        val dim = VoiceTransformer.transform(BuiltInVoices.DEEP_FEMALE, AppSettings())
        val bright = VoiceTransformer.transform(BuiltInVoices.BRIGHT_GIRL, AppSettings())
        val dimShelf = dim.eqStages.first { it.kind == Biquad.Kind.HIGHSHELF }.gainDb
        val brightShelf = bright.eqStages.first { it.kind == Biquad.Kind.HIGHSHELF }.gainDb
        assertTrue(brightShelf > dimShelf)

        assertEquals(2f, VoiceTransformer.pitchRatio(BuiltInVoices.NATURAL_GIRL.copy(pitch = 12f)), 1e-4f)
        assertFalse(VoiceTransformer.describe(BuiltInVoices.ANIME_STYLE).isBlank())
    }

    @Test
    fun `settings noise suppression feeds the chain`() {
        val off = VoiceTransformer.transform(
            BuiltInVoices.NATURAL_GIRL.copy(noiseSuppression = 0f),
            AppSettings(noiseSuppression = 0f),
        )
        val on = VoiceTransformer.transform(
            BuiltInVoices.NATURAL_GIRL.copy(noiseSuppression = 1f),
            AppSettings(noiseSuppression = 1f),
        )
        assertTrue(on.noiseSuppression > off.noiseSuppression)
        assertEquals(0f, off.noiseSuppression, 1e-4f)
    }

    @Test
    fun `echo reduction setting reaches the chain`() {
        val enabled = VoiceTransformer.transform(
            BuiltInVoices.NATURAL_GIRL,
            AppSettings(echoReduction = true, echoReductionAmountDb = 12f),
        )
        val disabled = VoiceTransformer.transform(
            BuiltInVoices.NATURAL_GIRL,
            AppSettings(echoReduction = false),
        )
        assertTrue(enabled.echoReductionEnabled)
        assertFalse(disabled.echoReductionEnabled)
        assertEquals(12f, enabled.echoReductionDb, 0.01f)
    }
}
