package com.vicechanger.app.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln

/**
 * Two measurements that turn "it does not sound different" into numbers: the dominant frequency
 * (is the pitch actually shifted?) and the spectral centroid (did the vocal-tract envelope move?).
 *
 * Used by the on-device self test, where the user can see input vs output pitch for the voice
 * they selected.
 */
object SpectrumAnalysis {

    /** Dominant frequency of the middle of [samples], via Hann-windowed FFT + parabolic interpolation. */
    fun dominantFrequency(samples: FloatArray, sampleRate: Int, fftSize: Int = 8192): Float {
        val (re, im, size) = windowedSpectrum(samples, fftSize)
        var bestBin = 1
        var best = 0f
        for (k in 1 until size / 2) {
            val magnitude = hypot(re[k], im[k])
            if (magnitude > best) {
                best = magnitude
                bestBin = k
            }
        }
        if (bestBin <= 1 || bestBin >= size / 2 - 1) return bestBin * sampleRate.toFloat() / size
        val left = ln(hypot(re[bestBin - 1], im[bestBin - 1]).coerceAtLeast(1e-12f).toDouble())
        val centre = ln(hypot(re[bestBin], im[bestBin]).coerceAtLeast(1e-12f).toDouble())
        val right = ln(hypot(re[bestBin + 1], im[bestBin + 1]).coerceAtLeast(1e-12f).toDouble())
        val denominator = left - 2 * centre + right
        val refined = if (kotlin.math.abs(denominator) > 1e-9) {
            bestBin + (0.5 * (left - right) / denominator).toFloat()
        } else {
            bestBin.toFloat()
        }
        return refined * sampleRate / size
    }

    /** Spectral centroid in Hz - rises when the formant envelope is shifted up. */
    fun spectralCentroid(samples: FloatArray, sampleRate: Int, fftSize: Int = 8192): Float {
        val (re, im, size) = windowedSpectrum(samples, fftSize)
        var weighted = 0.0
        var total = 0.0
        for (k in 1 until size / 2) {
            val magnitude = hypot(re[k], im[k]).toDouble()
            weighted += (k.toDouble() * sampleRate / size) * magnitude
            total += magnitude
        }
        return if (total <= 0.0) 0f else (weighted / total).toFloat()
    }

    /**
     * Fundamental frequency by autocorrelation, searched in a voice range.
     *
     * A voiced sound's strongest *partial* sits near its first formant, not at its fundamental, so
     * the spectral peak is the wrong measurement for "did the pitch change". This is the right one.
     */
    fun fundamentalHz(
        samples: FloatArray,
        sampleRate: Int,
        minHz: Float = 70f,
        maxHz: Float = 500f,
    ): Float {
        val start = samples.size / 4
        val count = minOf(samples.size - start, sampleRate)
        val minLag = (sampleRate / maxHz).toInt().coerceAtLeast(2)
        val maxLag = minOf((sampleRate / minHz).toInt(), count / 2)
        if (count < 2048 || maxLag <= minLag) return 0f

        var bestLag = 0
        var best = 0.0
        for (lag in minLag..maxLag) {
            var sum = 0.0
            var i = 0
            while (i < count - lag) {
                sum += samples[start + i] * samples[start + i + lag]
                i++
            }
            val normalised = sum / (count - lag)
            if (normalised > best) {
                best = normalised
                bestLag = lag
            }
        }
        if (bestLag == 0 || best <= 0.0) return 0f

        // Parabolic refinement for sub-sample lag resolution.
        var refined = bestLag.toDouble()
        if (bestLag > minLag && bestLag < maxLag) {
            val left = lagScore(samples, start, count, bestLag - 1)
            val centre = best
            val right = lagScore(samples, start, count, bestLag + 1)
            val denominator = left - 2 * centre + right
            if (kotlin.math.abs(denominator) > 1e-12) {
                refined += 0.5 * (left - right) / denominator
            }
        }
        return if (refined <= 0.0) 0f else (sampleRate / refined).toFloat()
    }

    private fun lagScore(samples: FloatArray, start: Int, count: Int, lag: Int): Double {
        var sum = 0.0
        var i = 0
        while (i < count - lag) {
            sum += samples[start + i] * samples[start + i + lag]
            i++
        }
        return sum / (count - lag)
    }

    /** Peak absolute sample in dBFS. */
    fun peakDbfs(samples: FloatArray): Float {
        var peak = 0f
        for (sample in samples) {
            val magnitude = kotlin.math.abs(sample)
            if (magnitude > peak) peak = magnitude
        }
        return if (peak <= 1e-6f) -60f else 20f * ln(peak.toDouble()).toFloat() / 2.302585f
    }

    private data class Spectrum(val re: FloatArray, val im: FloatArray, val size: Int)

    private fun windowedSpectrum(samples: FloatArray, fftSize: Int): Spectrum {
        val size = maxOf(512, if (fftSize and (fftSize - 1) == 0) fftSize else 8192)
        val start = ((samples.size - size) / 2).coerceAtLeast(0)
        val re = FloatArray(size)
        val im = FloatArray(size)
        for (i in 0 until size) {
            val sample = samples.getOrElse(start + i) { 0f }
            val window = 0.5f - 0.5f * cos(2.0 * PI * i / size).toFloat()
            re[i] = sample * window
        }
        Fft(size).forward(re, im)
        return Spectrum(re, im, size)
    }
}
