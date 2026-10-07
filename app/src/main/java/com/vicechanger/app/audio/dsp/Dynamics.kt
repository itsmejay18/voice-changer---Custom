package com.vicechanger.app.audio.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Feed-forward compressor with a soft knee. Keeps the transformed voice at a usable,
 * even level before it reaches the limiter, which is what stops presets from jumping in
 * loudness when the user switches between them.
 */
class Compressor(private val sampleRate: Int) {

    var enabled: Boolean = true
    var thresholdDb: Float = -22f
    var ratio: Float = 4f
    var attackMs: Float = 4f
    var releaseMs: Float = 140f
    var makeupDb: Float = 6f
    var kneeDb: Float = 6f

    private var envelope = 0f
    private var attackCoefficient = 0f
    private var releaseCoefficient = 0f
    private var makeupLinear = 1f
    private var dirty = true

    private fun refresh() {
        attackCoefficient = exp(-1.0 / (sampleRate * attackMs / 1000.0)).toFloat()
        releaseCoefficient = exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()
        makeupLinear = 10.0.pow(makeupDb / 20.0).toFloat()
        dirty = false
    }

    fun process(buffer: FloatArray, length: Int = buffer.size) {
        if (dirty) refresh()
        if (!enabled) return
        val kneeStart = thresholdDb - kneeDb / 2f
        val kneeEnd = thresholdDb + kneeDb / 2f
        val slope = 1f - 1f / ratio
        for (i in 0 until length) {
            val x = buffer[i]
            val rectified = abs(x)
            val coefficient = if (rectified > envelope) attackCoefficient else releaseCoefficient
            envelope = coefficient * envelope + (1f - coefficient) * rectified
            if (envelope < 1e-7f) {
                buffer[i] = x * makeupLinear
                continue
            }
            val levelDb = 20f * ln(envelope.toDouble()).toFloat() / LN10
            val gainReductionDb = when {
                levelDb <= kneeStart -> 0f
                levelDb >= kneeEnd -> slope * (levelDb - thresholdDb)
                else -> {
                    val over = levelDb - kneeStart
                    slope * over * over / (2f * kneeDb)
                }
            }
            val gain = 10f.pow(-gainReductionDb / 20f)
            buffer[i] = x * gain * makeupLinear
        }
    }

    /** Current gain reduction in dB (positive number = how much is being pulled down). */
    fun currentGainReductionDb(): Float {
        if (envelope < 1e-7f) return 0f
        val levelDb = 20f * ln(envelope.toDouble()).toFloat() / LN10
        if (levelDb <= thresholdDb) return 0f
        return (1f - 1f / ratio) * (levelDb - thresholdDb)
    }

    fun reset() {
        envelope = 0f
        dirty = true
    }

    private companion object {
        const val LN10 = 2.302585092994046f
    }
}

/**
 * Peak limiter with instant attack and a smooth release. This is the last thing that
 * touches a sample before it leaves the engine, so it is the reason the output can never
 * clip no matter what the presets ask the chain to do.
 */
class Limiter(private val sampleRate: Int, var ceiling: Float = 0.97f) {

    var enabled: Boolean = true
    private var gain = 1f
    private var releaseCoefficient = exp(-1.0 / (sampleRate * 60.0 / 1000.0)).toFloat()

    var lastGainReductionDb: Float = 0f
        private set

    fun process(buffer: FloatArray, length: Int = buffer.size) {
        if (!enabled) return
        var worst = 0f
        for (i in 0 until length) {
            val x = buffer[i]
            val magnitude = abs(x)
            if (magnitude * gain > ceiling) {
                gain = if (magnitude > 1e-7f) ceiling / magnitude else 1f
            } else {
                gain = releaseCoefficient * gain + (1f - releaseCoefficient)
                if (gain > 1f) gain = 1f
            }
            var y = x * gain
            // Soft clip is the safety net for the sample where the limiter is still opening.
            if (y > ceiling) y = softClip(y, ceiling)
            if (y < -ceiling) y = -softClip(-y, ceiling)
            buffer[i] = y
            val reduction = abs(y)
            if (reduction > worst) worst = reduction
        }
        lastGainReductionDb = if (worst > 1e-6f && worst > ceiling) {
            20f * ln((worst / ceiling).toDouble()).toFloat() / 2.302585f
        } else {
            0f
        }
    }

    private fun softClip(x: Float, limit: Float): Float {
        val over = x - limit
        return limit + limit * tanh(over / limit)
    }

    fun reset() {
        gain = 1f
    }
}

/**
 * Gentle asymmetric-free tanh saturation. Adds harmonics for the "gamer girl" edge and,
 * more importantly, thickens quiet speech the way a real preamp does.
 */
class Saturator {

    var drive: Float = 1f
    var mix: Float = 0f

    fun process(buffer: FloatArray, length: Int = buffer.size) {
        if (mix <= 0.001f || drive <= 0.001f) return
        val normaliser = 1f / tanh(drive)
        for (i in 0 until length) {
            val x = buffer[i]
            val wet = tanh(drive * x) * normaliser
            buffer[i] = x * (1f - mix) + wet * mix
        }
    }

    fun reset() = Unit
}

/**
 * Half-duplex echo/feedback suppressor. Android will not let an app cancel the acoustic
 * path from its own loudspeaker back into its microphone, so instead of pretending, the
 * engine ducks the microphone while the output is loud - the standard, honest way to keep
 * speaker monitoring from howling. Off when the user monitors on a headset.
 */
class EchoSuppressor(private val sampleRate: Int) {

    var enabled: Boolean = false
    var thresholdDb: Float = -34f
    var maxReductionDb: Float = 18f

    private var reductionDb = 0f
    private var releaseCoefficient = exp(-1.0 / (sampleRate * 220.0 / 1000.0)).toFloat()
    private var attackCoefficient = exp(-1.0 / (sampleRate * 8.0 / 1000.0)).toFloat()

    /** Feed the previous output block's level, then process the next input block. */
    fun process(buffer: FloatArray, outputLevelDb: Float, length: Int = buffer.size) {
        if (!enabled) {
            reductionDb = 0f
            return
        }
        val target = if (outputLevelDb > thresholdDb) {
            min(maxReductionDb, (outputLevelDb - thresholdDb) * 0.8f)
        } else {
            0f
        }
        for (i in 0 until length) {
            val coefficient = if (target > reductionDb) attackCoefficient else releaseCoefficient
            reductionDb = coefficient * reductionDb + (1f - coefficient) * target
            val gain = 10f.pow(-reductionDb / 20f)
            buffer[i] *= gain
        }
    }

    val currentReductionDb: Float get() = reductionDb
}

/** Ring modulator + short comb: the robot preset's character, in one pass. */
class RobotVoiceEffect(private val sampleRate: Int) {

    var enabled: Boolean = false
    var modulationHz: Float = 58f
    var depth: Float = 0.55f
    var combDelayMs: Float = 6.5f
    var combFeedback: Float = 0.35f

    private var phase = 0.0
    private var combBuffer = FloatArray(2048)
    private var combIndex = 0
    private var combDelaySamples = 1

    private fun refreshDelay() {
        combDelaySamples = (sampleRate * combDelayMs / 1000f).toInt().coerceIn(1, combBuffer.size - 1)
    }

    fun process(buffer: FloatArray, length: Int = buffer.size) {
        if (!enabled) return
        refreshDelay()
        val phaseIncrement = 2.0 * Math.PI * modulationHz / sampleRate
        for (i in 0 until length) {
            val x = buffer[i]
            val carrier = (0.5 + 0.5 * kotlin.math.cos(phase)).toFloat()
            phase += phaseIncrement
            if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
            val modulated = x * (1f - depth + depth * carrier)
            val readIndex = if (combIndex - combDelaySamples < 0) {
                combIndex - combDelaySamples + combBuffer.size
            } else {
                combIndex - combDelaySamples
            }
            val delayed = combBuffer[readIndex]
            combBuffer[combIndex] = modulated + delayed * combFeedback
            combIndex = (combIndex + 1) % combBuffer.size
            buffer[i] = modulated * 0.7f + delayed * 0.6f
        }
    }

    fun reset() {
        combBuffer.fill(0f)
        combIndex = 0
        phase = 0.0
    }
}
