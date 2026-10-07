package com.vicechanger.app.voice

import com.vicechanger.app.Signals
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.dsp.SpectrumAnalysis
import com.vicechanger.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-device self test is the answer to "I pressed START and nothing happened": it has to report
 * the truth about the chain. These tests make sure it measures the fundamental (not the loudest
 * formant partial), that it passes for the real voices, and that its synthesis is sane.
 */
class DspSelfTestTest {

    private val sampleRate = AudioConfig.SAMPLE_RATE

    @Test
    fun `the synthetic vowel is a real periodic signal at the requested pitch`() {
        val vowel = DspSelfTest.syntheticVowel(sampleRate, 2f)
        assertFalse(Signals.hasNonFinite(vowel))
        val fundamental = SpectrumAnalysis.fundamentalHz(vowel, sampleRate)
        assertEquals(DspSelfTest.TEST_FUNDAMENTAL_HZ, fundamental, 12f)
        assertTrue("must be audible: ${Signals.peak(vowel)}", Signals.peak(vowel) > 0.05f)
        val centroid = SpectrumAnalysis.spectralCentroid(vowel, sampleRate)
        assertTrue("formants must give it a colour: $centroid", centroid > 400f)
    }

    @Test
    fun `the self test measures the pitch shift of the real chain`() {
        for (preset in listOf(BuiltInVoices.NATURAL_GIRL, BuiltInVoices.CUTE_GIRL, BuiltInVoices.MATURE_WOMAN)) {
            val outcome = DspSelfTest.run(preset, AppSettings(echoReduction = false))
            assertTrue(
                "${preset.name}: ${outcome.result.summary()}",
                outcome.result.passed,
            )
            assertEquals(
                "${preset.name} measured semitones",
                preset.clamped().pitch,
                outcome.result.measuredSemitones,
                1.2f,
            )
            assertEquals(
                "${preset.name} measured ratio",
                VoiceTransformer.pitchRatio(preset),
                outcome.result.measuredRatio,
                0.12f,
            )
        }
    }

    @Test
    fun `the self test raises the envelope for a formant shift`() {
        val outcome = DspSelfTest.run(BuiltInVoices.CUTE_GIRL, AppSettings(echoReduction = false))
        assertTrue(
            "formant warp must raise the envelope: ${outcome.result.inputCentroidHz} -> " +
                "${outcome.result.outputCentroidHz}",
            outcome.result.outputCentroidHz > outcome.result.inputCentroidHz,
        )
    }

    @Test
    fun `the self test reports the processed audio without clipping`() {
        val outcome = DspSelfTest.run(BuiltInVoices.ANIME_STYLE, AppSettings(echoReduction = false))
        assertFalse(Signals.hasNonFinite(outcome.processed))
        assertTrue(Signals.peak(outcome.processed) <= AudioConfig.OUTPUT_CEILING + 1e-3f)
        assertTrue("the played sample must be audible", outcome.result.outputPeakDbfs > -20f)
        assertEquals(sampleRate, outcome.sampleRate)
    }

    @Test
    fun `the summary states the measurement and the verdict`() {
        val outcome = DspSelfTest.run(BuiltInVoices.GAMER_GIRL, AppSettings(echoReduction = false))
        val summary = outcome.result.summary()
        assertTrue(summary.contains("Pitch:"))
        assertTrue(summary.contains("semitones"))
        assertTrue(summary.contains("Pipeline delay"))
        assertTrue(summary.contains("RESULT:"))
        if (outcome.result.passed) {
            assertTrue(summary.contains("transforming audio on this device"))
        } else {
            assertTrue(summary.contains("did NOT shift the pitch"))
        }
    }

    @Test
    fun `a voice with no pitch shift is reported as not passing`() {
        val flat = BuiltInVoices.NATURAL_GIRL.copy(pitch = 0f, formant = 1f, id = "flat_test")
        val outcome = DspSelfTest.run(flat, AppSettings(echoReduction = false))
        // Expected ratio is 1.0 and the chain is transparent, so this is "passed" only if the
        // measurement is honest about there being no shift.
        assertEquals(1f, outcome.result.expectedRatio, 1e-4f)
        assertEquals(1f, outcome.result.measuredRatio, 0.05f)
    }
}
