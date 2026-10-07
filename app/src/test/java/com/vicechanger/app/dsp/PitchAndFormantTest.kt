package com.vicechanger.app.dsp

import com.vicechanger.app.Signals
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.voice.OfflineRenderer
import com.vicechanger.app.audio.ProcessorParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tests that matter most: does the pitch shift actually shift the pitch, does the formant
 * shift move the vocal-tract envelope *without* moving the harmonics, and does the noise
 * suppressor suppress noise without eating a steady voice.
 */
class PitchAndFormantTest {

    private val sampleRate = AudioConfig.SAMPLE_RATE

    private val bareChain = ProcessorParams(
        noiseSuppression = 0f,
        highpassHz = 20f,
        saturationMix = 0f,
        // Ratio 1 means the compressor is genuinely out of the way; setting the threshold low
        // would instead be maximum compression and would silently attenuate everything.
        compressorThresholdDb = -24f,
        compressorRatio = 1f,
        compressorMakeupDb = 0f,
        outputGainDb = 0f,
        echoReductionEnabled = false,
    )

    @Test
    fun `seven semitones up multiplies the pitch by the right ratio`() {
        val input = Signals.sine(220f, 2f, sampleRate, amplitude = 0.2f)
        val rendered = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(pitchSemitones = 7f),
        )
        val measured = Signals.dominantFrequency(rendered.samples, sampleRate)
        val expected = 220f * 1.4983f
        assertEquals("measured $measured Hz", expected, measured, expected * 0.06f)
    }

    @Test
    fun `five semitones down lowers the pitch by the right ratio`() {
        val input = Signals.sine(220f, 2f, sampleRate, amplitude = 0.2f)
        val rendered = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(pitchSemitones = -5f),
        )
        val measured = Signals.dominantFrequency(rendered.samples, sampleRate)
        val expected = 220f * 0.7492f
        assertEquals("measured $measured Hz", expected, measured, expected * 0.06f)
    }

    @Test
    fun `zero pitch leaves the pitch where it was`() {
        val input = Signals.sine(300f, 1.5f, sampleRate, amplitude = 0.2f)
        val rendered = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(pitchSemitones = 0f),
        )
        val measured = Signals.dominantFrequency(rendered.samples, sampleRate)
        assertEquals(300f, measured, 8f)
    }

    @Test
    fun `formant shift moves the envelope and leaves the fundamental alone`() {
        val input = Signals.harmonicStack(
            fundamentalHz = 200f,
            harmonics = 24,
            seconds = 2f,
            sampleRate = sampleRate,
            amplitude = 0.4f,
        )
        val unchanged = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(pitchSemitones = 0f, formantRatio = 1.0f),
        )
        val warped = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(pitchSemitones = 0f, formantRatio = 1.3f),
        )

        val centroidBefore = Signals.spectralCentroid(unchanged.samples, sampleRate)
        val centroidAfter = Signals.spectralCentroid(warped.samples, sampleRate)
        assertTrue(
            "formant warp must raise the spectral centroid ($centroidBefore -> $centroidAfter)",
            centroidAfter > centroidBefore * 1.05f,
        )

        val fundamental = Signals.dominantFrequency(warped.samples, sampleRate)
        assertEquals("formant warping must not move the harmonics", 200f, fundamental, 12f)
    }

    @Test
    fun `pitch and formant are independent`() {
        val input = Signals.harmonicStack(180f, 24, 2f, sampleRate, amplitude = 0.4f)
        val high = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(pitchSemitones = 6f, formantRatio = 1.2f),
        )
        val fundamental = Signals.dominantFrequency(high.samples, sampleRate)
        assertEquals(180f * 1.4142f, fundamental, 180f * 1.4142f * 0.08f)
    }

    @Test
    fun `noise suppression reduces steady noise`() {
        val input = Signals.noise(seconds = 3f, sampleRate = sampleRate, amplitude = 0.08f)
        val rendered = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(noiseSuppression = 1f),
        )
        val tailStart = rendered.samples.size * 3 / 4
        val inputRms = Signals.rms(input, tailStart, input.size)
        val outputRms = Signals.rms(rendered.samples, tailStart, rendered.samples.size)
        assertTrue(
            "noise must come out quieter ($inputRms -> $outputRms)",
            outputRms < inputRms * 0.92f,
        )
    }

    @Test
    fun `bypassing noise suppression leaves noise alone`() {
        val input = Signals.noise(seconds = 3f, sampleRate = sampleRate, amplitude = 0.08f)
        val rendered = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(noiseSuppression = 0f),
        )
        val tailStart = rendered.samples.size * 3 / 4
        val inputRms = Signals.rms(input, tailStart, input.size)
        val outputRms = Signals.rms(rendered.samples, tailStart, rendered.samples.size)
        assertEquals("with suppression off the level must match", inputRms, outputRms, inputRms * 0.1f)
    }

    @Test
    fun `noise suppression keeps a steady tone recognisable`() {
        val input = Signals.sine(440f, 2f, sampleRate, amplitude = 0.2f)
        val rendered = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(noiseSuppression = 1f),
        )
        val inputRms = Signals.rms(input, input.size / 2, input.size)
        val outputRms = Signals.rms(rendered.samples, rendered.samples.size / 2, rendered.samples.size)

        // A perfectly steady tone is indistinguishable from steady noise, so the gate settles at
        // its floor: it is attenuated (measured about -7 dB) but must never be crushed or shifted.
        assertTrue(
            "a steady tone must survive the gate ($inputRms -> $outputRms)",
            outputRms > inputRms * 0.2f,
        )
        assertTrue("the gate must not silence a tone", outputRms < inputRms)
        val measured = Signals.dominantFrequency(rendered.samples, sampleRate)
        assertEquals("the gate must not shift the tone", 440f, measured, 12f)
    }

    @Test
    fun `speech like bursts pass the gate untouched`() {
        val burst = FloatArray(sampleRate * 3) { i ->
            val phase = (i / (sampleRate / 4)) % 2
            if (phase == 0) {
                0.25f * kotlin.math.sin(2.0 * Math.PI * 300 * i / sampleRate).toFloat()
            } else {
                0f
            }
        }
        val rendered = OfflineRenderer.render(
            input = burst,
            sampleRate = sampleRate,
            params = bareChain.copy(noiseSuppression = 1f),
        )
        val onFrom = sampleRate
        val onTo = sampleRate + sampleRate / 4
        val inputRms = Signals.rms(burst, onFrom, onTo)
        val outputRms = Signals.rms(rendered.samples, onFrom, onTo)
        assertEquals(
            "non-stationary speech must pass ($inputRms -> $outputRms)",
            inputRms,
            outputRms,
            inputRms * 0.1f,
        )
    }

    @Test
    fun `the suppression gain is bounded and off means off`() {
        val stage = com.vicechanger.app.audio.dsp.SpectralStage(
            sampleRate = sampleRate,
            frameSize = AudioConfig.FFT_FRAME_SIZE,
            hopSize = AudioConfig.FFT_HOP_SIZE,
            formantRatio = 1f,
            noiseSuppression = 1f,
        )
        val block = Signals.noise(0.2f, sampleRate, amplitude = 0.1f)
        val scratch = FloatArray(AudioConfig.BLOCK_SIZE * 8)
        repeat(20) { stage.process(block, block.size, scratch) }
        val gains = stage.lastGainSnapshot()
        assertTrue("bins must be attenuated", gains.any { it < 0.9f })
        assertTrue("gains must stay bounded", gains.all { it in 0.05f..1.0001f })
        assertTrue("reduction must be reported", stage.lastNoiseReductionDb < 0f)

        val off = com.vicechanger.app.audio.dsp.SpectralStage(
            sampleRate = sampleRate,
            frameSize = AudioConfig.FFT_FRAME_SIZE,
            hopSize = AudioConfig.FFT_HOP_SIZE,
            formantRatio = 1f,
            noiseSuppression = 0f,
        )
        repeat(20) { off.process(block, block.size, scratch) }
        assertTrue("suppression off must be a bypass", off.lastGainSnapshot().all { it == 1f })
        assertEquals(0f, off.lastNoiseReductionDb, 0f)
    }

    @Test
    fun `brightness shelf changes the high frequency balance`() {
        val input = Signals.harmonicStack(150f, 40, 1.5f, sampleRate, amplitude = 0.3f)
        val flat = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(pitchSemitones = 0f),
        )
        val bright = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = bareChain.copy(
                pitchSemitones = 0f,
                eqStages = listOf(
                    com.vicechanger.app.audio.EqStage(
                        kind = com.vicechanger.app.audio.dsp.Biquad.Kind.HIGHSHELF,
                        freqHz = 6_500f,
                        gainDb = 6f,
                    ),
                ),
            ),
        )
        val before = Signals.spectralCentroid(flat.samples, sampleRate)
        val after = Signals.spectralCentroid(bright.samples, sampleRate)
        assertTrue("brightness must raise the centroid ($before -> $after)", after > before * 1.02f)
    }
}
