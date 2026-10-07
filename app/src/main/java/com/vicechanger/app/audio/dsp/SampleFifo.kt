package com.vicechanger.app.audio.dsp

/**
 * Fixed-rate sample FIFO used between pipeline stages. The stages produce a jittery number
 * of samples per call (STFT hops never line up exactly with the I/O block size), so each
 * stage boundary gets one of these to convert that jitter back into fixed-size blocks.
 *
 * Growth is capped: a runaway producer cannot exhaust memory, it just loses the oldest
 * samples (counted in [overflowDrops] so the engine can report it instead of hiding it).
 */
class SampleFifo(initialCapacity: Int = 16384, private val maxCapacity: Int = 48000 * 4) {

    private var buffer = FloatArray(initialCapacity.coerceAtLeast(64))
    private var length = 0

    var overflowDrops: Long = 0L
        private set

    val size: Int get() = length

    fun clear() {
        length = 0
        overflowDrops = 0L
    }

    fun push(source: FloatArray, count: Int) {
        if (count <= 0) return
        ensureCapacity(length + count)
        System.arraycopy(source, 0, buffer, length, count)
        length += count
    }

    /**
     * Copy up to [count] samples into [destination].
     * @return the number of samples actually copied (0 when empty).
     */
    fun pop(destination: FloatArray, count: Int): Int {
        val n = minOf(count, length)
        if (n <= 0) return 0
        System.arraycopy(buffer, 0, destination, 0, n)
        val remaining = length - n
        if (remaining > 0) System.arraycopy(buffer, n, buffer, 0, remaining)
        length = remaining
        return n
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= buffer.size) return
        if (needed > maxCapacity) {
            // Keep the newest maxCapacity samples, drop the oldest.
            val drop = needed - maxCapacity
            val keep = length - drop
            if (keep > 0) System.arraycopy(buffer, drop, buffer, 0, keep)
            length = maxOf(keep, 0)
            overflowDrops += drop
            return
        }
        var size = buffer.size
        while (size < needed) size *= 2
        buffer = buffer.copyOf(minOf(size, maxCapacity))
    }
}
