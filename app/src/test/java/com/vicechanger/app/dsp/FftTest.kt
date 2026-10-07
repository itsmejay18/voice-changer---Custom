package com.vicechanger.app.dsp

import com.vicechanger.app.audio.dsp.Fft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class FftTest {

    @Test
    fun `forward transform matches a naive DFT`() {
        val size = 64
        val random = Random(7)
        val re = FloatArray(size) { random.nextFloat() - 0.5f }
        val im = FloatArray(size) { random.nextFloat() - 0.5f }
        val originalRe = re.copyOf()
        val originalIm = im.copyOf()

        Fft(size).forward(re, im)

        for (k in 0 until size) {
            var sumRe = 0.0
            var sumIm = 0.0
            for (n in 0 until size) {
                val angle = -2.0 * Math.PI * k * n / size
                sumRe += originalRe[n] * cos(angle) - originalIm[n] * sin(angle)
                sumIm += originalRe[n] * sin(angle) + originalIm[n] * cos(angle)
            }
            assertEquals(sumRe.toFloat(), re[k], 1e-3f)
            assertEquals(sumIm.toFloat(), im[k], 1e-3f)
        }
    }

    @Test
    fun `inverse undoes the forward transform`() {
        val size = 128
        val random = Random(11)
        val re = FloatArray(size) { random.nextFloat() - 0.5f }
        val im = FloatArray(size)
        val expected = re.copyOf()

        val fft = Fft(size)
        fft.forward(re, im)
        fft.inverse(re, im)

        for (i in 0 until size) assertEquals(expected[i], re[i], 1e-4f)
        for (i in 0 until size) assertEquals(0f, im[i], 1e-4f)
    }

    @Test
    fun `a pure tone lands in the expected bin`() {
        val size = 256
        val bin = 12
        val re = FloatArray(size) { cos(2.0 * Math.PI * bin * it / size).toFloat() }
        val im = FloatArray(size)
        Fft(size).forward(re, im)

        val magnitudes = FloatArray(size / 2 + 1)
        Fft.magnitudes(re, im, size, magnitudes)
        var bestBin = 0
        for (k in magnitudes.indices) if (magnitudes[k] > magnitudes[bestBin]) bestBin = k
        assertEquals(bin, bestBin)
        assertTrue("peak magnitude should dominate", magnitudes[bin] > 50f)
    }

    @Test
    fun `rejects a non power of two size`() {
        val failure = runCatching { Fft(100) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `dc signal produces a single bin`() {
        val size = 64
        val re = FloatArray(size) { 1f }
        val im = FloatArray(size)
        Fft(size).forward(re, im)
        assertEquals(size.toFloat(), re[0], 1e-3f)
        for (k in 1 until size) assertTrue(abs(re[k]) < 1e-3f)
    }
}
