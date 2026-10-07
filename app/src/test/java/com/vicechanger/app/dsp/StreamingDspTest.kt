package com.vicechanger.app.dsp

import com.vicechanger.app.Signals
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.dsp.PhaseVocoderStage
import com.vicechanger.app.audio.dsp.SampleFifo
import com.vicechanger.app.audio.dsp.StreamingResampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StreamingDspTest {

    @Test
    fun `resampler consumes the configured number of inputs per output`() {
        val resampler = StreamingResampler(samplesPerOutput = 2f)
        val input = FloatArray(100) { it.toFloat() }
        resampler.push(input, input.size)

        val out = FloatArray(200)
        val produced = resampler.pull(out, out.size)

        assertTrue("expected roughly half the inputs as outputs, got $produced", produced in 45..50)
        for (i in 0 until produced) {
            assertEquals(1f + 2f * i, out[i], 1e-3f)
        }
    }

    @Test
    fun `resampler stays continuous across arbitrarily sized pushes`() {
        val resampler = StreamingResampler(samplesPerOutput = 2f)
        val out = FloatArray(400)

        resampler.push(FloatArray(100) { it.toFloat() }, 100)
        val first = resampler.pull(out, out.size)

        val second = FloatArray(100) { (100 + it).toFloat() }
        resampler.push(second, second.size)
        val more = resampler.pull(out, out.size)

        assertTrue(first > 0 && more > 0)
        // The first sample after the second push must continue the same ramp: no glitch.
        assertEquals(99f, out[0], 1e-2f)
    }

    @Test
    fun `fifo preserves order and reports its size`() {
        val fifo = SampleFifo(initialCapacity = 16, maxCapacity = 64)
        fifo.push(FloatArray(10) { it.toFloat() }, 10)
        assertEquals(10, fifo.size)

        val first = FloatArray(5)
        assertEquals(5, fifo.pop(first, 5))
        assertEquals(0f, first[0], 0f)
        assertEquals(4f, first[4], 0f)
        assertEquals(5, fifo.size)

        val rest = FloatArray(5)
        assertEquals(5, fifo.pop(rest, 5))
        assertEquals(5f, rest[0], 0f)
        assertEquals(9f, rest[4], 0f)
        assertEquals(0, fifo.size)
    }

    @Test
    fun `fifo caps growth instead of exhausting memory`() {
        val fifo = SampleFifo(initialCapacity = 16, maxCapacity = 64)
        repeat(20) { fifo.push(FloatArray(16) { 1f }, 16) }
        assertTrue("size must stay bounded", fifo.size <= 64)
        assertTrue("dropped samples must be counted", fifo.overflowDrops > 0)
    }

    @Test
    fun `phase vocoder time-stretches without moving the pitch`() {
        val sampleRate = AudioConfig.SAMPLE_RATE
        val stretch = 1.5f
        val vocoder = PhaseVocoderStage(
            sampleRate = sampleRate,
            frameSize = AudioConfig.FFT_FRAME_SIZE,
            analysisHop = AudioConfig.FFT_HOP_SIZE,
            stretch = stretch,
        )
        val input = Signals.sine(220f, seconds = 2f, sampleRate = sampleRate, amplitude = 0.4f)
        val collected = ArrayList<Float>(input.size * 2)

        val block = FloatArray(AudioConfig.BLOCK_SIZE)
        val out = FloatArray(AudioConfig.BLOCK_SIZE * 4)
        var index = 0
        while (index < input.size) {
            val count = minOf(block.size, input.size - index)
            System.arraycopy(input, index, block, 0, count)
            val produced = vocoder.process(block, count, out)
            for (i in 0 until produced) collected.add(out[i])
            index += count
        }

        val output = collected.toFloatArray()
        val ratio = output.size.toFloat() / input.size
        assertEquals("stretch ratio", stretch, ratio, 0.12f)

        val body = output.drop(AudioConfig.FFT_FRAME_SIZE * 4).toFloatArray()
        val measured = Signals.dominantFrequency(body, sampleRate)
        assertEquals("time stretching must not shift the pitch", 220f, measured, 8f)
    }

    @Test
    fun `phase vocoder reports progress and buffers real samples`() {
        val vocoder = PhaseVocoderStage(
            sampleRate = AudioConfig.SAMPLE_RATE,
            stretch = 1.2f,
        )
        val block = FloatArray(AudioConfig.BLOCK_SIZE)
        val out = FloatArray(AudioConfig.BLOCK_SIZE * 4)
        repeat(6) { vocoder.process(block, block.size, out) }
        assertTrue("frames must have been processed", vocoder.framesProcessed > 0)
        assertTrue("stage must report buffered audio", vocoder.bufferedSamples > 0)
        assertTrue("pipeline delay must be positive", vocoder.pipelineDelaySamples() > 0)
    }

    @Test
    fun `stretch of one is the identity mapping`() {
        assertEquals(256, PhaseVocoderStage.synthesisHopFor(256, 1f))
        assertEquals(2f, PhaseVocoderStage.ratioFor(12f), 1e-4f)
        assertEquals(1f, PhaseVocoderStage.ratioFor(0f), 1e-5f)
        assertTrue(abs(PhaseVocoderStage.ratioFor(-12f) - 0.5f) < 1e-4f)
    }
}
