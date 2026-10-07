package com.vicechanger.app

import com.vicechanger.app.audio.dsp.Fft
import com.vicechanger.app.utils.KeyValueStore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Random

/**
 * Test-side signal helpers. Everything is deterministic (seeded noise, fixed phases) so a
 * failure always means the code changed, not that a random draw was unlucky.
 */
object Signals {

    fun sine(
        frequencyHz: Float,
        seconds: Float,
        sampleRate: Int = 48_000,
        amplitude: Float = 0.2f,
        phase: Float = 0f,
    ): FloatArray {
        val count = (seconds * sampleRate).toInt()
        return FloatArray(count) { i ->
            amplitude * sin(2.0 * PI * frequencyHz * i / sampleRate + phase).toFloat()
        }
    }

    /** Sawtooth-like harmonic stack: a steady spectral envelope the formant tests can measure. */
    fun harmonicStack(
        fundamentalHz: Float,
        harmonics: Int,
        seconds: Float,
        sampleRate: Int = 48_000,
        amplitude: Float = 0.5f,
    ): FloatArray {
        val count = (seconds * sampleRate).toInt()
        val output = FloatArray(count)
        for (h in 1..harmonics) {
            val partialAmplitude = amplitude / (h * h)
            for (i in 0 until count) {
                output[i] += partialAmplitude *
                    sin(2.0 * PI * fundamentalHz * h * i / sampleRate).toFloat()
            }
        }
        return output
    }

    fun noise(seconds: Float, sampleRate: Int = 48_000, amplitude: Float = 0.1f, seed: Long = 42L): FloatArray {
        val random = Random(seed)
        val count = (seconds * sampleRate).toInt()
        return FloatArray(count) { ((random.nextFloat() * 2f) - 1f) * amplitude }
    }

    fun silence(seconds: Float, sampleRate: Int = 48_000): FloatArray =
        FloatArray((seconds * sampleRate).toInt())

    /** Silence, then speech-level tone: used to prove the offline renderer's head trim. */
    fun silenceThenTone(
        silenceSeconds: Float,
        toneSeconds: Float,
        frequencyHz: Float = 300f,
        sampleRate: Int = 48_000,
    ): FloatArray {
        val lead = silence(silenceSeconds, sampleRate)
        val tone = sine(frequencyHz, toneSeconds, sampleRate, amplitude = 0.25f)
        return lead + tone
    }

    fun rms(samples: FloatArray, from: Int = 0, to: Int = samples.size): Float {
        val start = from.coerceIn(0, samples.size)
        val end = to.coerceIn(start, samples.size)
        if (end <= start) return 0f
        var sum = 0.0
        for (i in start until end) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / (end - start)).toFloat()
    }

    fun peak(samples: FloatArray): Float = samples.maxOfOrNull { kotlin.math.abs(it) } ?: 0f

    fun hasNonFinite(samples: FloatArray): Boolean =
        samples.any { it.isNaN() || it.isInfinite() }

    /**
     * Dominant frequency of the middle of a buffer via a Hann-windowed FFT with parabolic
     * interpolation. This is how the pitch tests check the *actual* pitch of the output.
     */
    fun dominantFrequency(samples: FloatArray, sampleRate: Int = 48_000, fftSize: Int = 8192): Float {
        require(fftSize.let { it > 0 && (it and (it - 1)) == 0 }) { "fftSize must be a power of two" }
        val start = ((samples.size - fftSize) / 2).coerceAtLeast(0)
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            val sample = samples.getOrElse(start + i) { 0f }
            val window = 0.5f - 0.5f * cos(2.0 * PI * i / fftSize).toFloat()
            re[i] = sample * window
        }
        val fft = Fft(fftSize)
        fft.forward(re, im)
        var bestBin = 1
        var bestMagnitude = 0f
        for (k in 1 until fftSize / 2) {
            val magnitude = hypot(re[k], im[k])
            if (magnitude > bestMagnitude) {
                bestMagnitude = magnitude
                bestBin = k
            }
        }
        var refined = bestBin.toFloat()
        if (bestBin > 1 && bestBin < fftSize / 2 - 1) {
            val left = ln(hypot(re[bestBin - 1], im[bestBin - 1]).coerceAtLeast(1e-12f).toDouble())
            val centre = ln(hypot(re[bestBin], im[bestBin]).coerceAtLeast(1e-12f).toDouble())
            val right = ln(hypot(re[bestBin + 1], im[bestBin + 1]).coerceAtLeast(1e-12f).toDouble())
            val denominator = left - 2 * centre + right
            if (kotlin.math.abs(denominator) > 1e-9) {
                refined += (0.5 * (left - right) / denominator).toFloat()
            }
        }
        return refined * sampleRate / fftSize
    }

    /** Spectral centroid in Hz: rises when the vocal-tract envelope is shifted up. */
    fun spectralCentroid(samples: FloatArray, sampleRate: Int = 48_000, fftSize: Int = 8192): Float {
        val start = ((samples.size - fftSize) / 2).coerceAtLeast(0)
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            val sample = samples.getOrElse(start + i) { 0f }
            val window = 0.5f - 0.5f * cos(2.0 * PI * i / fftSize).toFloat()
            re[i] = sample * window
        }
        Fft(fftSize).forward(re, im)
        var weighted = 0.0
        var total = 0.0
        for (k in 1 until fftSize / 2) {
            val magnitude = hypot(re[k], im[k]).toDouble()
            weighted += (k.toDouble() * sampleRate / fftSize) * magnitude
            total += magnitude
        }
        return if (total <= 0.0) 0f else (weighted / total).toFloat()
    }

    /** Frequency response of a configured chain at one frequency (steady state, via FFT ratio). */
    fun gainAt(
        input: FloatArray,
        output: FloatArray,
        frequencyHz: Float,
        sampleRate: Int = 48_000,
        fftSize: Int = 16384,
    ): Float {
        val inputMagnitude = magnitudeAt(input, frequencyHz, sampleRate, fftSize)
        val outputMagnitude = magnitudeAt(output, frequencyHz, sampleRate, fftSize)
        return if (inputMagnitude <= 0f) 0f else outputMagnitude / inputMagnitude
    }

    private fun magnitudeAt(
        samples: FloatArray,
        frequencyHz: Float,
        sampleRate: Int,
        fftSize: Int,
    ): Float {
        // Goertzel-style single-bin correlation: enough to compare levels at one frequency.
        var real = 0.0
        var imaginary = 0.0
        val start = ((samples.size - fftSize) / 2).coerceAtLeast(0)
        val count = minOf(fftSize, samples.size - start)
        for (i in 0 until count) {
            val angle = 2.0 * PI * frequencyHz * i / sampleRate
            real += samples[start + i] * cos(angle)
            imaginary += samples[start + i] * sin(angle)
        }
        return sqrt(real * real + imaginary * imaginary).toFloat() / count
    }
}

/** In-memory [KeyValueStore] so storage behaviour is testable without Android. */
class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {

    private val values = LinkedHashMap<String, String>(initial)

    val snapshot: Map<String, String> get() = values.toMap()

    override fun getString(key: String, defaultValue: String?): String? = values[key] ?: defaultValue

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override fun getFloat(key: String, defaultValue: Float): Float =
        values[key]?.toFloatOrNull() ?: defaultValue

    override fun putFloat(key: String, value: Float) {
        values[key] = value.toString()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: defaultValue

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value.toString()
    }

    override fun getInt(key: String, defaultValue: Int): Int = values[key]?.toIntOrNull() ?: defaultValue

    override fun putInt(key: String, value: Int) {
        values[key] = value.toString()
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun contains(key: String): Boolean = values.containsKey(key)
}
