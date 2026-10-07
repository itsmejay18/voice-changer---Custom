package com.vicechanger.app.dsp

import com.vicechanger.app.Signals
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.AudioProcessor
import com.vicechanger.app.settings.AppSettings
import com.vicechanger.app.voice.BuiltInVoices
import com.vicechanger.app.voice.VoiceTransformer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live engine runs 5 ms blocks (240 samples), not the 10 ms blocks the offline renderer uses.
 * A chain that only works at the offline block size would be a live-only failure that no other test
 * would catch, and "it sounds like me on the phone" is exactly that class of bug.
 */
class LiveBlockSizeChainTest {

    private val sampleRate = AudioConfig.SAMPLE_RATE

    private fun runLive(input: FloatArray, preset: com.vicechanger.app.voice.VoicePreset): FloatArray {
        val processor = AudioProcessor(sampleRate)
        processor.configure(VoiceTransformer.transform(preset, AppSettings(echoReduction = false)))
        val blockSize = AudioConfig.BLOCK_SIZE_LOW_LATENCY
        val block = FloatArray(blockSize)
        val out = FloatArray(blockSize)
        val collected = ArrayList<Float>(input.size + blockSize * 8)
        var index = 0
        while (index < input.size) {
            val count = minOf(blockSize, input.size - index)
            java.util.Arrays.fill(block, 0f)
            System.arraycopy(input, index, block, 0, count)
            processor.process(block, blockSize, out)
            for (i in 0 until blockSize) collected.add(out[i])
            index += count
        }
        return collected.toFloatArray()
    }

    @Test
    fun `the live block size really shifts the pitch`() {
        val input = Signals.sine(200f, 2f, sampleRate, amplitude = 0.25f)
        val output = runLive(input, BuiltInVoices.NATURAL_GIRL)
        val measured = Signals.dominantFrequency(output, sampleRate)
        val expected = 200f * VoiceTransformer.pitchRatio(BuiltInVoices.NATURAL_GIRL)
        assertEquals("live chain measured $measured Hz", expected, measured, expected * 0.08f)
    }

    @Test
    fun `every built-in voice shifts at the live block size`() {
        val input = Signals.sine(180f, 1.5f, sampleRate, amplitude = 0.25f)
        for (preset in BuiltInVoices.ALL) {
            val output = runLive(input, preset)
            val expected = 180f * VoiceTransformer.pitchRatio(preset)
            val measured = Signals.dominantFrequency(output, sampleRate)
            assertEquals(
                "${preset.name}: expected around $expected Hz",
                expected,
                measured,
                expected * 0.15f,
            )
            assertFalse("${preset.name} produced non-finite audio", Signals.hasNonFinite(output))
        }
    }

    @Test
    fun `the live chain keeps a realistic voice audible`() {
        // The noise gate is deliberately excluded: it is *designed* to attenuate steady tones, and
        // this test is about the pitch/eq/trim/limiter path keeping speech audible. A single sine is
        // also the wrong probe - the compressor's makeup is deliberately conservative, so a steady
        // tone sitting 10 dB over the threshold comes out about 3 dB quieter, while a voice does not.
        val preset = BuiltInVoices.NATURAL_GIRL
        val blockSize = AudioConfig.BLOCK_SIZE_LOW_LATENCY
        val vowel = com.vicechanger.app.voice.DspSelfTest.syntheticVowel(sampleRate, 2f)
        val input = FloatArray(vowel.size)
        for (i in vowel.indices) input[i] = vowel[i] * 1.5f // a loud talker

        val processor = AudioProcessor(sampleRate)
        processor.configure(
            VoiceTransformer.transform(preset, AppSettings(echoReduction = false))
                .copy(noiseSuppression = 0f),
        )
        val block = FloatArray(blockSize)
        val out = FloatArray(blockSize)
        val collected = ArrayList<Float>(input.size + blockSize * 4)
        var index = 0
        while (index < input.size) {
            val count = minOf(blockSize, input.size - index)
            java.util.Arrays.fill(block, 0f)
            System.arraycopy(input, index, block, 0, count)
            processor.process(block, blockSize, out)
            for (i in 0 until blockSize) collected.add(out[i])
            index += count
        }
        val output = collected.toFloatArray()
        val inputRms = Signals.rms(input, input.size / 4, input.size)
        val outputRms = Signals.rms(output, output.size / 4, output.size)
        assertTrue(
            "a transformed voice must not be crushed: $inputRms -> $outputRms",
            outputRms > inputRms * 0.75f,
        )
        assertTrue(Signals.peak(output) <= AudioConfig.OUTPUT_CEILING + 1e-3f)
    }

    @Test
    fun `no buffer gaps in the steady state at the live block size`() {
        val input = Signals.noise(4f, sampleRate, amplitude = 0.1f)
        val processor = AudioProcessor(sampleRate)
        processor.configure(VoiceTransformer.transform(BuiltInVoices.GAMER_GIRL, AppSettings(echoReduction = false)))
        val blockSize = AudioConfig.BLOCK_SIZE_LOW_LATENCY
        val block = FloatArray(blockSize)
        val out = FloatArray(blockSize)
        var index = 0
        val gapsAfterWarmup = ArrayList<Long>()
        var warmupBlocks = 0
        while (index < input.size) {
            val count = minOf(blockSize, input.size - index)
            java.util.Arrays.fill(block, 0f)
            System.arraycopy(input, index, block, 0, count)
            processor.process(block, blockSize, out)
            warmupBlocks++
            if (warmupBlocks == 40) gapsAfterWarmup.add(processor.underruns)
            index += count
        }
        gapsAfterWarmup.add(processor.underruns)
        // The pipeline needs about two STFT windows to fill; after that it must not keep starving.
        assertTrue("warm-up gaps: ${gapsAfterWarmup.first()}", gapsAfterWarmup.first() < 40)
        assertEquals(
            "gaps must not keep growing after warm-up",
            gapsAfterWarmup.first(),
            gapsAfterWarmup.last(),
        )
    }
}
