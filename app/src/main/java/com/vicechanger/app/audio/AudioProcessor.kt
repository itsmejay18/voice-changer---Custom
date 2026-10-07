package com.vicechanger.app.audio

import com.vicechanger.app.audio.dsp.Biquad
import com.vicechanger.app.audio.dsp.Compressor
import com.vicechanger.app.audio.dsp.EchoSuppressor
import com.vicechanger.app.audio.dsp.EqBank
import com.vicechanger.app.audio.dsp.LevelMeter
import com.vicechanger.app.audio.dsp.Limiter
import com.vicechanger.app.audio.dsp.PhaseVocoderStage
import com.vicechanger.app.audio.dsp.RobotVoiceEffect
import com.vicechanger.app.audio.dsp.SampleFifo
import com.vicechanger.app.audio.dsp.Saturator
import com.vicechanger.app.audio.dsp.SpectralStage
import com.vicechanger.app.audio.dsp.StreamingResampler
import kotlin.math.abs
import kotlin.math.pow

/**
 * The whole live voice path in one class:
 *
 *   rumble filter -> noise suppression + formant warp (spectral)
 *   -> pitch shift (phase vocoder + resampler)
 *   -> EQ / resonance -> saturation -> robot effect
 *   -> compressor -> output gain -> limiter -> meter
 *
 * Every stage is streaming and allocation-free in [process]: the audio thread calls it with
 * the same block size forever, and buffers are sized once in [configure].
 *
 * Order matters and is deliberate: the spectral stage runs *after* the pitch shift so its
 * formant warp is independent of the pitch amount, and the limiter is last so no preset -
 * including the custom one with every slider at maximum - can clip the output.
 */
class AudioProcessor(val sampleRate: Int = AudioConfig.SAMPLE_RATE) {

    private var params = ProcessorParams()

    private var pitchStage: PhaseVocoderStage? = null
    private var resampler: StreamingResampler? = null
    private var spectralStage: SpectralStage? = null

    private val eq = EqBank()
    private val dcBlocker = Biquad()
    private val saturator = Saturator()
    private val compressor = Compressor(sampleRate)
    private val limiter = Limiter(sampleRate, AudioConfig.OUTPUT_CEILING)
    private val robot = RobotVoiceEffect(sampleRate)
    private val echoSuppressor = EchoSuppressor(sampleRate)
    private val inputMeter = LevelMeter(sampleRate)
    private val outputMeter = LevelMeter(sampleRate)

    private val pitchFifo = SampleFifo()
    private val spectralFifo = SampleFifo()
    private var stageBuffer = FloatArray(4096)
    private var resampleBuffer = FloatArray(4096)

    /** Blocks that came out short because the pipeline had not filled yet. */
    var underruns: Long = 0
        private set

    /** Number of times the chain was reconfigured since construction. */
    var reconfigureCount: Int = 0
        private set

    val currentParams: ProcessorParams get() = params

    fun configure(newParams: ProcessorParams) {
        params = newParams
        reconfigureCount++

        dcBlocker.configure(Biquad.Kind.HIGHPASS, sampleRate, newParams.highpassHz, 0.707f)

        if (newParams.needsPitch) {
            val ratio = PhaseVocoderStage.ratioFor(newParams.pitchSemitones)
            pitchStage = PhaseVocoderStage(
                sampleRate = sampleRate,
                frameSize = AudioConfig.FFT_FRAME_SIZE,
                analysisHop = AudioConfig.FFT_HOP_SIZE,
                stretch = ratio,
            )
            resampler = StreamingResampler(ratio)
        } else {
            pitchStage = null
            resampler = null
        }

        spectralStage = if (newParams.needsSpectral) {
            SpectralStage(
                sampleRate = sampleRate,
                frameSize = AudioConfig.FFT_FRAME_SIZE,
                hopSize = AudioConfig.FFT_HOP_SIZE,
                formantRatio = newParams.formantRatio,
                noiseSuppression = newParams.noiseSuppression,
            )
        } else {
            null
        }

        eq.clear()
        for (stage in newParams.eqStages) eq.add(stage.kind, sampleRate, stage.freqHz, stage.q, stage.gainDb)

        saturator.drive = newParams.saturationDrive
        saturator.mix = newParams.saturationMix

        robot.enabled = newParams.isRobotEffect
        if (newParams.ringModHz > 0f) robot.modulationHz = newParams.ringModHz
        robot.depth = newParams.ringModDepth
        robot.combFeedback = if (newParams.combEnabled) 0.35f else 0f

        compressor.enabled = true
        compressor.thresholdDb = newParams.compressorThresholdDb
        compressor.ratio = newParams.compressorRatio
        compressor.attackMs = newParams.compressorAttackMs
        compressor.releaseMs = newParams.compressorReleaseMs
        compressor.makeupDb = newParams.compressorMakeupDb
        compressor.reset()

        echoSuppressor.enabled = newParams.echoReductionEnabled
        echoSuppressor.maxReductionDb = newParams.echoReductionDb

        limiter.ceiling = AudioConfig.OUTPUT_CEILING
        limiter.reset()

        pitchFifo.clear()
        spectralFifo.clear()
        if (stageBuffer.size < AudioConfig.FFT_FRAME_SIZE * 4) stageBuffer = FloatArray(AudioConfig.FFT_FRAME_SIZE * 4)
        if (resampleBuffer.size < AudioConfig.FFT_FRAME_SIZE * 4) {
            resampleBuffer = FloatArray(AudioConfig.FFT_FRAME_SIZE * 4)
        }
    }

    /**
     * Process one block in place-ish: [input] holds [count] samples, [output] receives
     * [count] samples. Both may be the same array.
     */
    fun process(input: FloatArray, count: Int, output: FloatArray) {
        ensureCapacity(count)
        System.arraycopy(input, 0, stageBuffer, 0, count)

        echoSuppressor.process(stageBuffer, outputMeter.rmsDb, count)
        dcBlocker.process(stageBuffer, count)
        inputMeter.process(stageBuffer, count)

        // ---- pitch shift -------------------------------------------------------------
        val vocoder = pitchStage
        val re = resampler
        if (vocoder != null && re != null) {
            val stretched = resampleBuffer
            val produced = vocoder.process(stageBuffer, count, stretched)
            if (produced > 0) re.push(stretched, produced)
            var pulled = re.pull(stretched, stretched.size)
            while (pulled > 0) {
                pitchFifo.push(stretched, pulled)
                pulled = if (pitchFifo.size < 8192) re.pull(stretched, stretched.size) else 0
            }
            val got = pitchFifo.pop(stageBuffer, count)
            if (got < count) {
                underruns++
                java.util.Arrays.fill(stageBuffer, got, count, 0f)
            }
        }

        // ---- noise suppression + formant warp ----------------------------------------
        val spectral = spectralStage
        if (spectral != null) {
            val temp = resampleBuffer
            val produced = spectral.process(stageBuffer, count, temp)
            if (produced > 0) spectralFifo.push(temp, produced)
            val got = spectralFifo.pop(stageBuffer, count)
            if (got < count) {
                underruns++
                java.util.Arrays.fill(stageBuffer, got, count, 0f)
            }
        }

        // ---- shaping, character, dynamics, safety ------------------------------------
        eq.process(stageBuffer, count)
        saturator.process(stageBuffer, count)
        robot.process(stageBuffer, count)
        compressor.process(stageBuffer, count)

        val outputGain = 10f.pow(params.outputGainDb / 20f)
        if (outputGain != 1f) {
            for (i in 0 until count) stageBuffer[i] *= outputGain
        }

        limiter.process(stageBuffer, count)
        outputMeter.process(stageBuffer, count)

        System.arraycopy(stageBuffer, 0, output, 0, count)
    }

    private fun ensureCapacity(count: Int) {
        val needed = count + AudioConfig.FFT_FRAME_SIZE
        if (stageBuffer.size < needed) stageBuffer = FloatArray(needed)
        if (resampleBuffer.size < needed) resampleBuffer = FloatArray(needed)
    }

    /**
     * Samples currently sitting inside the chain. AudioEngine measures this at runtime, so
     * the latency the UI shows is the real buffered amount, not a constant somebody typed.
     */
    val bufferedSamples: Int
        get() = (pitchStage?.bufferedSamples ?: 0) +
            (resampler?.bufferedSamples ?: 0) +
            pitchFifo.size +
            (spectralStage?.bufferedSamples ?: 0) +
            spectralFifo.size

    val inputLevelDb: Float get() = inputMeter.rmsDb
    val outputLevelDb: Float get() = outputMeter.rmsDb
    val outputPeakDb: Float get() = outputMeter.peakDb
    val outputPeakHoldDb: Float get() = outputMeter.peakHoldDb
    val gainReductionDb: Float get() = compressor.currentGainReductionDb()
    val limiterReductionDb: Float get() = limiter.lastGainReductionDb
    val echoReductionDb: Float get() = echoSuppressor.currentReductionDb
    val isWarmingUp: Boolean get() = pitchStage?.isWarmingUp ?: false

    fun reset() {
        inputMeter.reset()
        outputMeter.reset()
        compressor.reset()
        limiter.reset()
        robot.reset()
        eq.reset()
        dcBlocker.reset()
        pitchStage?.reset()
        spectralStage?.reset()
        resampler?.reset()
        pitchFifo.clear()
        spectralFifo.clear()
        underruns = 0
    }
}
