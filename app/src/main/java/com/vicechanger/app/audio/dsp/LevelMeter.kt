package com.vicechanger.app.audio.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * RMS + peak meter with proper attack/release ballistics, so the UI bars look like a real
 * meter instead of a flickering number. Also reports everything in dBFS, which is what the
 * engine exposes to the UI and to the tests.
 *
 * The ballistics coefficients are derived from the block length on every call: the app feeds
 * blocks of 5-10 ms, and a per-sample coefficient applied once per block would make the meter
 * hundreds of times too slow (which is exactly what the meter test caught).
 */
class LevelMeter(
    private val sampleRate: Int,
    private val attackMs: Float = 8f,
    private val releaseMs: Float = 260f,
) {

    private var rmsEnvelope = 0f
    private var peakEnvelope = 0f
    private var lastRmsDb = MIN_DB
    private var lastPeakDb = MIN_DB

    var peakHoldDb: Float = MIN_DB
        private set

    fun process(buffer: FloatArray, length: Int = buffer.size) {
        if (length <= 0) return
        var sum = 0f
        var peak = 0f
        for (i in 0 until min(length, buffer.size)) {
            val x = buffer[i]
            sum += x * x
            val magnitude = abs(x)
            if (magnitude > peak) peak = magnitude
        }
        val count = min(length, buffer.size)
        val rms = sqrt(sum / count)
        val attack = coefficient(attackMs, count)
        val release = coefficient(releaseMs, count)
        rmsEnvelope = ballistics(rmsEnvelope, rms, attack, release)
        peakEnvelope = ballistics(peakEnvelope, peak, attack, release)
        lastRmsDb = toDb(rmsEnvelope)
        lastPeakDb = toDb(peakEnvelope)
        peakHoldDb = if (lastPeakDb >= peakHoldDb) lastPeakDb else max(lastPeakDb, peakHoldDb - 0.35f)
    }

    private fun coefficient(timeMs: Float, samples: Int): Float =
        exp(-samples.toDouble() / (sampleRate * timeMs / 1000.0)).toFloat()

    private fun ballistics(current: Float, target: Float, attack: Float, release: Float): Float {
        val coefficient = if (target > current) attack else release
        return coefficient * current + (1f - coefficient) * target
    }

    /** Smoothed RMS in dBFS, floored at [MIN_DB]. */
    val rmsDb: Float get() = lastRmsDb

    /** Smoothed peak in dBFS, floored at [MIN_DB]. */
    val peakDb: Float get() = lastPeakDb

    /** Meter position in 0..1 for a UI bar spanning [-60 dB, 0 dB]. */
    fun level01(decibels: Float = lastRmsDb): Float = ((decibels - MIN_DB) / -MIN_DB).coerceIn(0f, 1f)

    fun reset() {
        rmsEnvelope = 0f
        peakEnvelope = 0f
        lastRmsDb = MIN_DB
        lastPeakDb = MIN_DB
        peakHoldDb = MIN_DB
    }

    companion object {
        const val MIN_DB = -60f

        fun toDb(linear: Float): Float =
            if (linear <= 1e-6f) MIN_DB else max(MIN_DB, 20f * ln(linear.toDouble()).toFloat() / 2.302585f)

        /** Linear peak of a buffer, used by tests and the recorder. */
        fun linearPeak(buffer: FloatArray, length: Int = buffer.size): Float {
            var peak = 0f
            for (i in 0 until min(length, buffer.size)) peak = max(peak, abs(buffer[i]))
            return peak
        }
    }
}
