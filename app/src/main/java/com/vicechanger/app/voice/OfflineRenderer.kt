package com.vicechanger.app.voice

import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.AudioProcessor
import com.vicechanger.app.audio.ProcessorParams
import com.vicechanger.app.audio.dsp.LevelMeter

/**
 * Offline renderer for the voice message path. Runs the *same* [AudioProcessor] as live mode
 * block by block, so a voice message sounds exactly like what the user heard in Live Voice.
 *
 * The pipeline introduces a fixed delay (the analysis window plus the stage buffers). The
 * renderer measures that delay from the processor's own buffer occupancy after the first
 * block and trims it off the head, which is why the saved file starts where the recording
 * started instead of with 20 ms of silence.
 */
object OfflineRenderer {

    data class Rendered(
        val samples: FloatArray,
        val sampleRate: Int,
        val inputSamples: Int,
        val measuredLatencySamples: Int,
        val peak: Float,
        val blocksProcessed: Int,
    ) {
        val seconds: Float get() = samples.size.toFloat() / sampleRate
        val peakDb: Float get() = LevelMeter.toDb(peak)
    }

    fun render(
        input: FloatArray,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        params: ProcessorParams,
        inputGainDb: Float = 0f,
        blockSize: Int = AudioConfig.BLOCK_SIZE,
    ): Rendered {
        val processor = AudioProcessor(sampleRate)
        processor.configure(params)
        val gain = com.vicechanger.app.audio.AudioEngine.dbToLinear(inputGainDb)

        val head = FloatArray(blockSize)
        val block = FloatArray(blockSize)
        val outputBlock = FloatArray(blockSize)
        val collected = ArrayList<Float>((input.size * 1.5f).toInt() + blockSize * 8)
        var measuredLatency = 0
        var blocks = 0

        var index = 0
        while (index < input.size) {
            val count = minOf(blockSize, input.size - index)
            for (i in 0 until count) block[i] = input[index + i] * gain
            if (count < blockSize) java.util.Arrays.fill(block, count, blockSize, 0f)
            processor.process(block, blockSize, outputBlock)
            if (measuredLatency == 0) measuredLatency = processor.bufferedSamples
            for (i in 0 until blockSize) collected.add(outputBlock[i])
            index += count
            blocks++
        }

        // Flush the pipeline with silence so the tail is not truncated by the same delay.
        head.fill(0f)
        val flushBlocks = ((measuredLatency / blockSize.toFloat()).toInt()) + 4
        repeat(flushBlocks) {
            processor.process(head, blockSize, outputBlock)
            for (i in 0 until blockSize) collected.add(outputBlock[i])
            blocks++
        }

        val aligned = when {
            measuredLatency in 1 until collected.size -> {
                val size = minOf(input.size, collected.size - measuredLatency)
                FloatArray(size) { collected[measuredLatency + it] }
            }
            else -> FloatArray(input.size) { collected.getOrElse(it) { 0f } }
        }

        var peak = 0f
        for (sample in aligned) {
            val magnitude = kotlin.math.abs(sample)
            if (magnitude > peak) peak = magnitude
        }

        return Rendered(
            samples = aligned,
            sampleRate = sampleRate,
            inputSamples = input.size,
            measuredLatencySamples = measuredLatency,
            peak = peak,
            blocksProcessed = blocks,
        )
    }
}
