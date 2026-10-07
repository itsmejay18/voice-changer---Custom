package com.vicechanger.app.audio.dsp

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Second spectral stage: broadband noise suppression plus independent formant warping. The two
 * effects compose - each produces a per-bin gain and they are multiplied - so a preset can use
 * either, both or neither.
 *
 * Formant warping works on the *envelope* of the spectrum. The envelope is isolated with a
 * real-cepstrum lifter (so it follows the vocal-tract shape, not the individual harmonics),
 * then resampled along frequency by [formantRatio]: 1.25 moves the resonances up a quarter,
 * which is what actually makes a male take read as female instead of as a chipmunk. The warp
 * is energy-normalised, so changing it never changes loudness.
 *
 * Noise suppression is a minimum-statistics Wiener gate: per bin it tracks the smallest power
 * seen recently (creeping upwards by [NOISE_FLOOR_RISE] per frame so a floor that disappears is
 * forgotten), lifts that minimum by [MINIMUM_BIAS] to estimate the noise mean, and attenuates
 * accordingly with a bounded floor. Steady hiss is pulled down; speech, being non-stationary,
 * passes. A perfectly steady tone is indistinguishable from steady noise and therefore settles
 * at the floor - this is a spectral gate, not a voice detector, and it is documented as such.
 *
 * Phases are left untouched, so the processing is a smooth zero-phase filter.
 */
class SpectralStage(
    sampleRate: Int,
    frameSize: Int = 1024,
    hopSize: Int = 256,
    private val formantRatio: Float,
    private val noiseSuppression: Float,
    private val envelopeSmoothingHz: Float = 320f,
) : StftStage(sampleRate, frameSize, hopSize, hopSize) {

    private val binCount = frameSize / 2 + 1
    private val magnitude = FloatArray(binCount)
    private val phase = FloatArray(binCount)
    private val gain = FloatArray(binCount)
    private val formantGain = FloatArray(binCount)
    private val smoothedGain = FloatArray(binCount)
    private val noiseFloor = FloatArray(binCount)
    private val smoothedPower = FloatArray(binCount)
    private val envelopeWork = FloatArray(frameSize)
    private val logEnvelope = FloatArray(binCount)
    private val warpedEnvelope = FloatArray(binCount)
    private val cepstrumRe = FloatArray(frameSize)
    private val cepstrumIm = FloatArray(frameSize)
    private val envelopeFft = Fft(frameSize)
    private val lifterCut = maxOf(2, Math.round(frameSize * envelopeSmoothingHz / sampleRate).toInt())

    /** Broadband attenuation applied in the last frame, in dB (negative = attenuated). */
    var lastNoiseReductionDb: Float = 0f
        private set

    /** Per-bin gain the last frame ended up with, for diagnostics and the tests. */
    fun lastGainSnapshot(): FloatArray = gain.copyOf()

    private val warping: Boolean = kotlin.math.abs(formantRatio - 1f) > 0.004f

    override fun processFrame(re: FloatArray, im: FloatArray, frameIndex: Long) {
        for (k in 0 until binCount) {
            magnitude[k] = hypot(re[k], im[k])
            phase[k] = atan2(im[k], re[k])
        }

        applyNoiseSuppression(frameIndex)
        if (warping) applyFormantWarp()

        for (k in 0 until binCount) {
            smoothedGain[k] = 0.65f * gain[k] + 0.35f * smoothedGain[k]
            val shaped = magnitude[k] * smoothedGain[k]
            re[k] = shaped * cos(phase[k])
            im[k] = shaped * sin(phase[k])
        }
    }

    private fun applyNoiseSuppression(frameIndex: Long) {
        if (noiseSuppression <= 0.01f) {
            for (k in 0 until binCount) gain[k] = 1f
            lastNoiseReductionDb = 0f
            return
        }
        val floorGain = (0.45f - 0.30f * noiseSuppression).coerceIn(0.12f, 0.6f)
        var reduction = 0f
        for (k in 0 until binCount) {
            val power = magnitude[k] * magnitude[k]
            // Smooth first: raw STFT bin powers of broadband noise swing by an order of
            // magnitude frame to frame, and a minimum taken over that swing sits far below the
            // noise mean, which would leave the gate wide open.
            smoothedPower[k] = 0.5f * smoothedPower[k] + 0.5f * power
            val tracked = smoothedPower[k]
            if (frameIndex == 0L) {
                noiseFloor[k] = tracked * 0.1f
            } else {
                val crept = noiseFloor[k] * NOISE_FLOOR_RISE
                noiseFloor[k] = if (tracked < crept) tracked else crept
            }
            val estimate = noiseFloor[k] * MINIMUM_BIAS + 1e-12f
            val snr = power / estimate
            gain[k] = (snr / (1f + snr)).coerceIn(floorGain, 1f)
            reduction += 20f * (kotlin.math.ln(gain[k].coerceAtLeast(1e-4f).toDouble()).toFloat() / 2.302585f)
        }
        // Three-tap smoothing across frequency kills most of the "musical noise" artefacts.
        var previous = gain[0]
        for (k in 1 until binCount - 1) {
            val current = gain[k]
            gain[k] = 0.25f * previous + 0.5f * current + 0.25f * gain[k + 1]
            previous = current
        }
        lastNoiseReductionDb = reduction / binCount
    }

    private fun applyFormantWarp() {
        for (k in 0 until binCount) envelopeWork[k] = kotlin.math.ln(magnitude[k] + 1e-7f)
        for (k in binCount until frameSize) envelopeWork[k] = envelopeWork[frameSize - k]

        System.arraycopy(envelopeWork, 0, cepstrumRe, 0, frameSize)
        java.util.Arrays.fill(cepstrumIm, 0f)
        envelopeFft.forward(cepstrumRe, cepstrumIm)

        for (n in lifterCut + 1 until frameSize - lifterCut) cepstrumRe[n] = 0f

        envelopeFft.inverse(cepstrumRe, cepstrumIm)
        for (k in 0 until binCount) logEnvelope[k] = cepstrumRe[k]

        // Resample the envelope along frequency: sampling at k / ratio moves formants by ratio.
        for (k in 0 until binCount) {
            val source = k / formantRatio
            val i = source.toInt()
            warpedEnvelope[k] = if (i >= binCount - 1) {
                logEnvelope[binCount - 1]
            } else {
                val t = source - i
                logEnvelope[i] * (1f - t) + logEnvelope[i + 1] * t
            }
        }

        for (k in 0 until binCount) {
            val envelope = exp(logEnvelope[k])
            val warped = exp(warpedEnvelope[k])
            formantGain[k] = (warped / (envelope + 1e-9f)).coerceIn(0.05f, 20f)
        }
        var previous = formantGain[0]
        for (k in 1 until binCount - 1) {
            val current = formantGain[k]
            formantGain[k] = 0.25f * previous + 0.5f * current + 0.25f * formantGain[k + 1]
            previous = current
        }

        // Keep the frame's energy where it was: warping the envelope must not change loudness.
        var shapedEnergy = 0f
        var originalEnergy = 0f
        for (k in 0 until binCount) {
            originalEnergy += magnitude[k] * magnitude[k]
            val shaped = magnitude[k] * formantGain[k]
            shapedEnergy += shaped * shaped
        }
        val correction = if (shapedEnergy > 1e-12f && originalEnergy > 1e-12f) {
            sqrt(originalEnergy / shapedEnergy).coerceIn(0.5f, 2f)
        } else {
            1f
        }

        // Compose with whatever the noise suppressor decided.
        for (k in 0 until binCount) {
            gain[k] = (gain[k] * formantGain[k] * correction).coerceIn(0.02f, 25f)
        }
    }

    override fun reset() {
        super.reset()
        noiseFloor.fill(0f)
        smoothedPower.fill(0f)
        smoothedGain.fill(1f)
        formantGain.fill(1f)
    }

    /** Envelope lifter cutoff in bins; exposed for the tests that pin the smoothing band. */
    val lifterCutBins: Int get() = lifterCut

    val envelopeSmoothingBandHz: Float get() = envelopeSmoothingHz

    companion object {
        /** How fast the tracked per-bin minimum is allowed to creep upwards. */
        private const val NOISE_FLOOR_RISE = 1.006f

        /** The tracked minimum sits below the noise mean; this lifts it back up. */
        private const val MINIMUM_BIAS = 2.5f
    }
}
