package com.vicechanger.app.audio.dsp

/**
 * Iterative in-place radix-2 complex FFT with precomputed twiddles and a bit-reversal
 * table. Size is fixed per instance, which lets the real-time audio thread run with zero
 * allocation, and lets the unit tests compare it against a naive DFT.
 */
class Fft(val size: Int) {

    init {
        require(size >= 2 && (size and (size - 1)) == 0) { "FFT size must be a power of two, got $size" }
    }

    private val cosTable = FloatArray(size / 2) { kotlin.math.cos(2.0 * Math.PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { kotlin.math.sin(2.0 * Math.PI * it / size).toFloat() }
    private val reversed = IntArray(size) { Integer.reverse(it) ushr (32 - Integer.numberOfTrailingZeros(size)) }

    fun forward(re: FloatArray, im: FloatArray) = transform(re, im, inverse = false)

    fun inverse(re: FloatArray, im: FloatArray) {
        transform(re, im, inverse = true)
        val scale = 1f / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        require(re.size >= size && im.size >= size) { "buffers smaller than FFT size" }
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var i = 0
            while (i < size) {
                var j = 0
                var k = 0
                while (j < half) {
                    val wr = cosTable[k]
                    val wi = if (inverse) sinTable[k] else -sinTable[k]
                    val idxA = i + j
                    val idxB = idxA + half
                    val xr = re[idxB] * wr - im[idxB] * wi
                    val xi = re[idxB] * wi + im[idxB] * wr
                    re[idxB] = re[idxA] - xr
                    im[idxB] = im[idxA] - xi
                    re[idxA] += xr
                    im[idxA] += xi
                    j++
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        /** Magnitude of the real-input spectrum for bins 0..size/2 (inclusive). */
        fun magnitudes(re: FloatArray, im: FloatArray, size: Int, out: FloatArray) {
            for (k in 0..size / 2) out[k] = kotlin.math.hypot(re[k], im[k])
        }
    }
}
