package com.vicechanger.app.dsp

import com.vicechanger.app.Signals
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.dsp.Compressor
import com.vicechanger.app.audio.dsp.EchoSuppressor
import com.vicechanger.app.audio.dsp.Limiter
import com.vicechanger.app.audio.dsp.RobotVoiceEffect
import com.vicechanger.app.audio.dsp.Saturator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicsTest {

    private val sampleRate = AudioConfig.SAMPLE_RATE

    @Test
    fun `limiter never lets a sample past the ceiling`() {
        val limiter = Limiter(sampleRate, AudioConfig.OUTPUT_CEILING)
        val loud = Signals.sine(300f, 1f, sampleRate, amplitude = 4f)
        limiter.process(loud, loud.size)
        val peak = Signals.peak(loud)
        assertTrue("peak $peak must respect the ceiling", peak <= AudioConfig.OUTPUT_CEILING + 1e-3f)
        assertFalse(Signals.hasNonFinite(loud))
    }

    @Test
    fun `limiter leaves quiet audio alone`() {
        val limiter = Limiter(sampleRate, AudioConfig.OUTPUT_CEILING)
        val quiet = Signals.sine(300f, 0.5f, sampleRate, amplitude = 0.2f)
        val expected = Signals.peak(quiet)
        limiter.process(quiet, quiet.size)
        assertEquals(expected, Signals.peak(quiet), 1e-3f)
    }

    @Test
    fun `compressor pulls down loud material and reports gain reduction`() {
        val compressor = Compressor(sampleRate).apply {
            thresholdDb = -24f
            ratio = 8f
            makeupDb = 0f
            attackMs = 2f
            releaseMs = 60f
        }
        val loud = Signals.sine(250f, 1f, sampleRate, amplitude = 0.9f)
        val before = Signals.rms(loud, loud.size / 2, loud.size)
        compressor.process(loud, loud.size)
        val after = Signals.rms(loud, loud.size / 2, loud.size)

        assertTrue("loud material must be reduced ($before -> $after)", after < before)
        assertTrue("gain reduction must be reported", compressor.currentGainReductionDb() > 1f)
    }

    @Test
    fun `compressor ignores material below the threshold`() {
        val compressor = Compressor(sampleRate).apply {
            thresholdDb = -20f
            ratio = 6f
            makeupDb = 0f
        }
        val quiet = Signals.sine(250f, 0.5f, sampleRate, amplitude = 0.01f)
        val before = Signals.rms(quiet)
        compressor.process(quiet, quiet.size)
        assertEquals(before, Signals.rms(quiet), 1e-4f)
        assertEquals(0f, compressor.currentGainReductionDb(), 0.01f)
    }

    @Test
    fun `saturator stays bounded and changes the waveform`() {
        val saturator = Saturator().apply {
            drive = 6f
            mix = 0.8f
        }
        val tone = Signals.sine(200f, 0.2f, sampleRate, amplitude = 0.9f)
        val original = tone.copyOf()
        saturator.process(tone, tone.size)

        assertTrue("output must stay in range", Signals.peak(tone) <= 1.05f)
        var difference = 0.0
        for (i in tone.indices) difference += kotlin.math.abs(tone[i] - original[i]).toDouble()
        assertTrue("saturation must alter the signal", difference > 1.0)
    }

    @Test
    fun `saturator with zero mix is a bypass`() {
        val saturator = Saturator().apply {
            drive = 5f
            mix = 0f
        }
        val tone = Signals.sine(200f, 0.1f, sampleRate, amplitude = 0.4f)
        val original = tone.copyOf()
        saturator.process(tone, tone.size)
        for (i in tone.indices) assertEquals(original[i], tone[i], 1e-6f)
    }

    @Test
    fun `echo suppressor ducks the input while the output is loud`() {
        val suppressor = EchoSuppressor(sampleRate).apply {
            enabled = true
            thresholdDb = -34f
            maxReductionDb = 18f
        }
        val input = Signals.sine(300f, 0.3f, sampleRate, amplitude = 0.3f)
        val before = Signals.rms(input)
        suppressor.process(input, outputLevelDb = -10f, length = input.size)
        val after = Signals.rms(input, input.size / 2, input.size)

        assertTrue("must attenuate ($before -> $after)", after < before * 0.5f)
        assertTrue(suppressor.currentReductionDb > 5f)
    }

    @Test
    fun `echo suppressor does nothing when disabled`() {
        val suppressor = EchoSuppressor(sampleRate).apply { enabled = false }
        val input = Signals.sine(300f, 0.2f, sampleRate, amplitude = 0.3f)
        val original = input.copyOf()
        suppressor.process(input, outputLevelDb = -5f, length = input.size)
        for (i in input.indices) assertEquals(original[i], input[i], 1e-6f)
        assertEquals(0f, suppressor.currentReductionDb, 1e-6f)
    }

    @Test
    fun `robot effect modulates without exploding`() {
        val effect = RobotVoiceEffect(sampleRate).apply {
            enabled = true
            depth = 0.6f
            combFeedback = 0.35f
            modulationHz = 58f
        }
        val tone = Signals.sine(220f, 0.5f, sampleRate, amplitude = 0.4f)
        val original = tone.copyOf()
        effect.process(tone, tone.size)

        assertFalse(Signals.hasNonFinite(tone))
        assertTrue("comb feedback must not blow up", Signals.peak(tone) < 3f)
        var difference = 0.0
        for (i in tone.indices) difference += kotlin.math.abs(tone[i] - original[i]).toDouble()
        assertTrue("modulation must alter the signal", difference > 1.0)
    }

    @Test
    fun `robot effect is a bypass when disabled`() {
        val effect = RobotVoiceEffect(sampleRate).apply { enabled = false }
        val tone = Signals.sine(220f, 0.1f, sampleRate, amplitude = 0.3f)
        val original = tone.copyOf()
        effect.process(tone, tone.size)
        for (i in tone.indices) assertEquals(original[i], tone[i], 1e-6f)
    }
}
