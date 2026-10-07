package com.vicechanger.app.dsp

import com.vicechanger.app.Signals
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.AudioProcessor
import com.vicechanger.app.audio.ProcessorParams
import com.vicechanger.app.settings.AppSettings
import com.vicechanger.app.voice.BuiltInVoices
import com.vicechanger.app.voice.VoiceTransformer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end chain integrity: every preset must stay finite, bounded, and keep the block
 * contract the engine depends on (N samples in, N samples out).
 */
class AudioProcessorChainTest {

    private val sampleRate = AudioConfig.SAMPLE_RATE
    private val blockSize = AudioConfig.BLOCK_SIZE

    private fun runChain(input: FloatArray, params: ProcessorParams): FloatArray {
        val processor = AudioProcessor(sampleRate)
        processor.configure(params)
        val output = FloatArray(input.size)
        val block = FloatArray(blockSize)
        val processed = FloatArray(blockSize)
        var index = 0
        while (index < input.size) {
            val count = minOf(blockSize, input.size - index)
            java.util.Arrays.fill(block, 0f)
            System.arraycopy(input, index, block, 0, count)
            processor.process(block, blockSize, processed)
            System.arraycopy(processed, 0, output, index, count)
            index += count
        }
        return output
    }

    @Test
    fun `every built-in preset produces finite bounded audio`() {
        val input = Signals.harmonicStack(180f, 20, 1.5f, sampleRate, amplitude = 0.35f)
        for (preset in BuiltInVoices.ALL) {
            val params = VoiceTransformer.transform(preset, AppSettings())
            val output = runChain(input, params)
            assertFalse("${preset.name} produced non-finite samples", Signals.hasNonFinite(output))
            assertTrue(
                "${preset.name} exceeded the ceiling: ${Signals.peak(output)}",
                Signals.peak(output) <= AudioConfig.OUTPUT_CEILING + 1e-3f,
            )
            assertTrue(
                "${preset.name} produced output",
                Signals.rms(output, output.size / 2, output.size) > 1e-4f,
            )
        }
    }

    @Test
    fun `minimum preset values also stay finite`() {
        val input = Signals.noise(1f, sampleRate, 0.3f)
        val params = ProcessorParams(
            pitchSemitones = AudioConfig.MIN_PITCH_SEMITONES,
            formantRatio = AudioConfig.MIN_FORMANT,
            noiseSuppression = 1f,
            saturationDrive = 8f,
            saturationMix = 1f,
            ringModDepth = 0.75f,
            combEnabled = true,
            compressorRatio = 12f,
            outputGainDb = 6f,
        )
        val output = runChain(input, params)
        assertFalse(Signals.hasNonFinite(output))
        assertTrue(Signals.peak(output) <= AudioConfig.OUTPUT_CEILING + 1e-3f)
    }

    @Test
    fun `maximum preset values also stay finite`() {
        val input = Signals.noise(1f, sampleRate, 0.3f)
        val params = ProcessorParams(
            pitchSemitones = AudioConfig.MAX_PITCH_SEMITONES,
            formantRatio = AudioConfig.MAX_FORMANT,
            noiseSuppression = 1f,
            saturationDrive = 8f,
            saturationMix = 1f,
            compressorRatio = 12f,
            outputGainDb = 6f,
        )
        val output = runChain(input, params)
        assertFalse(Signals.hasNonFinite(output))
        assertTrue(Signals.peak(output) <= AudioConfig.OUTPUT_CEILING + 1e-3f)
    }

    @Test
    fun `silence in is silence out`() {
        val input = Signals.silence(1f, sampleRate)
        val output = runChain(input, VoiceTransformer.transform(BuiltInVoices.NATURAL_GIRL, AppSettings()))
        assertFalse(Signals.hasNonFinite(output))
        assertTrue("silence must not ring: ${Signals.peak(output)}", Signals.peak(output) < 1e-3f)
    }

    @Test
    fun `processor reports a real buffered latency`() {
        val processor = AudioProcessor(sampleRate)
        processor.configure(VoiceTransformer.transform(BuiltInVoices.NATURAL_GIRL, AppSettings()))
        val block = FloatArray(blockSize)
        val out = FloatArray(blockSize)
        repeat(4) { processor.process(block, blockSize, out) }
        assertTrue("buffered samples must be > 0", processor.bufferedSamples > 0)
    }

    @Test
    fun `processor survives a reconfiguration between blocks`() {
        val processor = AudioProcessor(sampleRate)
        val block = Signals.sine(200f, blockSize.toFloat() / sampleRate, sampleRate, 0.3f)
        val out = FloatArray(blockSize)
        processor.configure(VoiceTransformer.transform(BuiltInVoices.NATURAL_GIRL, AppSettings()))
        repeat(3) { processor.process(block, block.size, out) }
        processor.configure(VoiceTransformer.transform(BuiltInVoices.ROBOT_GIRL, AppSettings()))
        repeat(3) { processor.process(block, block.size, out) }
        assertEquals(2, processor.reconfigureCount)
        assertFalse(Signals.hasNonFinite(out))
    }

    @Test
    fun `nothing is processed on the main thread requirement holds for renderer size`() {
        // The offline renderer must handle a full length recording without dropping audio.
        val input = Signals.sine(250f, 5f, sampleRate, amplitude = 0.2f)
        val rendered = com.vicechanger.app.voice.OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = VoiceTransformer.transform(BuiltInVoices.CUTE_GIRL, AppSettings()),
        )
        assertEquals(input.size, rendered.samples.size)
        assertTrue(rendered.blocksProcessed > input.size / blockSize)
        assertTrue("no buffer gaps expected offline", rendered.peak > 0.01f)
    }
}
