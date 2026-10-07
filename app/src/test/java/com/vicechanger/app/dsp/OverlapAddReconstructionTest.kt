package com.vicechanger.app.dsp

import com.vicechanger.app.Signals
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.dsp.StftStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The overlap-add machinery itself, with a stage that does not touch the spectrum: it must be
 * a bit-exact passthrough in the steady state.
 *
 * This is the regression test for the worst bug found in this codebase: the accumulator was
 * only partially cleared when it was compacted, so later frames added onto stale partial sums
 * and the output was up to 15x too loud with a frame-rate ripple. A 440 Hz tone came out as
 * 375 Hz of mush. Any change to StftStage that breaks the passthrough fails here immediately.
 */
class OverlapAddReconstructionTest {

    private class IdentityStage(sampleRate: Int, frameSize: Int, hop: Int) :
        StftStage(sampleRate, frameSize, hop, hop) {
        override fun processFrame(re: FloatArray, im: FloatArray, frameIndex: Long) = Unit
    }

    private val sampleRate = AudioConfig.SAMPLE_RATE

    private fun run(input: FloatArray, stage: IdentityStage): FloatArray {
        val collected = ArrayList<Float>()
        val block = FloatArray(AudioConfig.BLOCK_SIZE)
        val scratch = FloatArray(AudioConfig.BLOCK_SIZE * 8)
        var index = 0
        while (index < input.size) {
            val count = minOf(block.size, input.size - index)
            System.arraycopy(input, index, block, 0, count)
            val produced = stage.process(block, count, scratch)
            for (i in 0 until produced) collected.add(scratch[i])
            index += count
        }
        return collected.toFloatArray()
    }

    private fun assertExactReconstruction(input: FloatArray, hop: Int, label: String) {
        val stage = IdentityStage(sampleRate, AudioConfig.FFT_FRAME_SIZE, hop)
        val output = run(input, stage)

        assertTrue("$label: output must not be longer than the input", output.size <= input.size)
        assertTrue("$label: output must keep up with the input", output.size > input.size - 2048)

        // Skip the ramp-in (first full window) and the tail (the pipeline is not flushed here).
        val from = AudioConfig.FFT_FRAME_SIZE
        val to = minOf(output.size, input.size) - 2048
        var worst = 0f
        var worstAt = -1
        for (i in from until to) {
            val error = abs(input[i] - output[i])
            if (error > worst) {
                worst = error
                worstAt = i
            }
        }
        assertTrue("$label: worst reconstruction error $worst at sample $worstAt", worst < 1e-4f)
    }

    @Test
    fun `hop of a quarter window is an exact passthrough`() {
        val tone = Signals.sine(440f, 3f, sampleRate, amplitude = 0.2f)
        assertExactReconstruction(tone, AudioConfig.FFT_HOP_SIZE, "tone hop=256")
    }

    @Test
    fun `a non cola hop is still an exact passthrough`() {
        // 384 is what the phase vocoder uses for a 1.5x stretch; the Hann window squared is not
        // flat at that overlap, so only the normalisation table makes this exact.
        val tone = Signals.sine(220f, 3f, sampleRate, amplitude = 0.3f)
        assertExactReconstruction(tone, 384, "tone hop=384")
    }

    @Test
    fun `dc and noise survive the machinery`() {
        val dc = FloatArray(sampleRate * 2) { 0.5f }
        assertExactReconstruction(dc, AudioConfig.FFT_HOP_SIZE, "dc")

        val noise = Signals.noise(2f, sampleRate, amplitude = 0.25f)
        assertExactReconstruction(noise, AudioConfig.FFT_HOP_SIZE, "noise")
    }

    @Test
    fun `a long run does not drift`() {
        // The bug this guards against grew over time as the accumulator was compacted
        // repeatedly, so a long input is essential.
        val tone = Signals.sine(500f, 20f, sampleRate, amplitude = 0.2f)
        val stage = IdentityStage(sampleRate, AudioConfig.FFT_FRAME_SIZE, AudioConfig.FFT_HOP_SIZE)
        val output = run(tone, stage)

        val quarter = output.size / 4
        for (section in 0 until 4) {
            val from = section * quarter
            val to = from + quarter
            var worst = 0f
            for (i in from + AudioConfig.FFT_FRAME_SIZE until to) {
                if (i >= tone.size) break
                val error = abs(tone[i] - output[i])
                if (error > worst) worst = error
            }
            assertTrue("section $section drifted: worst error $worst", worst < 1e-4f)
        }
        assertEquals(0.2f * 0.7071f, Signals.rms(output, quarter, 2 * quarter), 0.002f)
    }

    @Test
    fun `phase vocoder output level matches the input level`() {
        val input = Signals.sine(300f, 2f, sampleRate, amplitude = 0.3f)
        val vocoder = com.vicechanger.app.audio.dsp.PhaseVocoderStage(
            sampleRate = sampleRate,
            frameSize = AudioConfig.FFT_FRAME_SIZE,
            analysisHop = AudioConfig.FFT_HOP_SIZE,
            stretch = 1.25f,
        )
        val collected = ArrayList<Float>()
        val block = FloatArray(AudioConfig.BLOCK_SIZE)
        val scratch = FloatArray(AudioConfig.BLOCK_SIZE * 8)
        var index = 0
        while (index < input.size) {
            val count = minOf(block.size, input.size - index)
            System.arraycopy(input, index, block, 0, count)
            val produced = vocoder.process(block, count, scratch)
            for (i in 0 until produced) collected.add(scratch[i])
            index += count
        }
        val output = collected.toFloatArray()
        val bodyStart = AudioConfig.FFT_FRAME_SIZE * 4
        val bodyEnd = output.size - AudioConfig.FFT_FRAME_SIZE * 4
        val ratio = Signals.rms(output, bodyStart, bodyEnd) / Signals.rms(input, 0, input.size)
        assertTrue("stretching must preserve level, ratio=$ratio", ratio in 0.9f..1.1f)
    }
}
