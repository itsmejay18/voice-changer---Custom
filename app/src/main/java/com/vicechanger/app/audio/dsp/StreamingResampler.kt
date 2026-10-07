package com.vicechanger.app.audio.dsp

/**
 * Streaming cubic (Catmull-Rom) resampler. One instance consumes `samplesPerOutput` input
 * samples for every output sample it produces, which is how the pitch shifter turns a
 * time-stretched signal back into a real-time signal with the same duration but a scaled
 * pitch.
 *
 * The internal buffer is only ever shifted by whole samples and always keeps one sample of
 * history before the read position, so output is continuous across arbitrarily sized pushes.
 */
class StreamingResampler(private val samplesPerOutput: Float) {

    init {
        require(samplesPerOutput > 0f) { "samplesPerOutput must be positive" }
    }

    private var buffer = FloatArray(4096)
    private var length = 0
    /** Fractional read position; kept in (0, length) and never below 1 so cubic history exists. */
    private var position = 1.0

    val bufferedSamples: Int get() = length

    fun push(samples: FloatArray, count: Int = samples.size) {
        ensureCapacity(length + count)
        System.arraycopy(samples, 0, buffer, length, count)
        length += count
    }

    /**
     * Produce up to [maxOut] samples into [out].
     * @return how many samples were actually written.
     */
    fun pull(out: FloatArray, maxOut: Int = out.size): Int {
        var produced = 0
        while (produced < maxOut) {
            val i = position.toInt()
            if (i + 2 >= length) break
            val t = (position - i).toFloat()
            out[produced++] = catmullRom(buffer[i - 1], buffer[i], buffer[i + 1], buffer[i + 2], t)
            position += samplesPerOutput
        }
        discardConsumed()
        return produced
    }

    private fun discardConsumed() {
        val drop = position.toInt() - 1
        if (drop <= 0) return
        val keep = length - drop
        if (keep > 0) System.arraycopy(buffer, drop, buffer, 0, keep)
        length = maxOf(keep, 0)
        position -= drop
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= buffer.size) return
        var size = buffer.size
        while (size < needed) size *= 2
        buffer = buffer.copyOf(size)
    }

    fun reset() {
        length = 0
        position = 1.0
    }

    companion object {
        /** Catmull-Rom spline: y(0) = b, y(1) = c. */
        fun catmullRom(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
            val t2 = t * t
            val t3 = t2 * t
            return 0.5f * ((2f * b) +
                (-a + c) * t +
                (2f * a - 5f * b + 4f * c - d) * t2 +
                (-a + 3f * b - 3f * c + d) * t3)
        }
    }
}
