package com.vicechanger.app.voice

import com.vicechanger.app.Signals
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.ProcessorParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRendererTest {

    private val sampleRate = AudioConfig.SAMPLE_RATE

    private val params = ProcessorParams(
        pitchSemitones = 5f,
        formantRatio = 1.1f,
        noiseSuppression = 0.3f,
    )

    @Test
    fun `output keeps the length of the input`() {
        val input = Signals.sine(220f, 3f, sampleRate, amplitude = 0.25f)
        val rendered = OfflineRenderer.render(input, sampleRate, params)
        assertEquals(input.size, rendered.samples.size)
        assertEquals(sampleRate, rendered.sampleRate)
        assertEquals(input.size, rendered.inputSamples)
    }

    @Test
    fun `the pipeline delay is measured and trimmed off the head`() {
        val input = Signals.silenceThenTone(silenceSeconds = 0.5f, toneSeconds = 1.5f)
        val rendered = OfflineRenderer.render(input, sampleRate, params)

        assertTrue("a delay must be measured", rendered.measuredLatencySamples > 0)

        // The first 400 ms of the input is silence; the output must not have smeared the tone
        // into it. Without the head trim, the transform would still be flushing buffer there.
        val quietWindow = sampleRate * 2 / 5
        val rmsBeforeTone = Signals.rms(rendered.samples, 0, quietWindow)
        val rmsDuringTone = Signals.rms(
            rendered.samples,
            sampleRate * 3 / 4,
            sampleRate * 3 / 2,
        )
        assertTrue("tone leaked into the silence: $rmsBeforeTone", rmsBeforeTone < 0.02f)
        assertTrue("the tone must be present: $rmsDuringTone", rmsDuringTone > 0.03f)
    }

    @Test
    fun `rendered audio never clips and never contains non-finite samples`() {
        val input = (Signals.sine(180f, 2f, sampleRate, 0.9f))
        val rendered = OfflineRenderer.render(
            input,
            sampleRate,
            params.copy(compressorMakeupDb = 12f, outputGainDb = 6f, saturationMix = 0.8f, saturationDrive = 6f),
        )
        assertFalse(Signals.hasNonFinite(rendered.samples))
        assertTrue(rendered.peak <= AudioConfig.OUTPUT_CEILING + 1e-3f)
    }

    @Test
    fun `rendered peak is reported in dBFS`() {
        val input = Signals.sine(200f, 1f, sampleRate, amplitude = 0.5f)
        val rendered = OfflineRenderer.render(input, sampleRate, params)
        assertTrue(rendered.peakDb < 0f)
        assertTrue(rendered.peakDb > -40f)
    }

    @Test
    fun `the renderer processes every block of a long recording`() {
        val input = Signals.noise(seconds = 30f, sampleRate = sampleRate, amplitude = 0.1f)
        val rendered = OfflineRenderer.render(input, sampleRate, params)
        assertEquals(input.size, rendered.samples.size)
        assertTrue(rendered.blocksProcessed >= input.size / AudioConfig.BLOCK_SIZE)
        assertFalse(Signals.hasNonFinite(rendered.samples))
    }

    @Test
    fun `a zero length input is handled without crashing`() {
        val rendered = OfflineRenderer.render(FloatArray(0), sampleRate, params)
        assertEquals(0, rendered.samples.size)
    }
}
