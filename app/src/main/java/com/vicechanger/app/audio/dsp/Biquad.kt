package com.vicechanger.app.audio.dsp

/**
 * RBJ cookbook biquad in transposed direct form II. Used for the rumble filter, the
 * warmth/resonance shaping and the brightness shelf - never for anything that needs
 * sample-accurate automation, so float state is fine.
 */
class Biquad {
    enum class Kind { LOWPASS, HIGHPASS, PEAKING, LOWSHELF, HIGHSHELF, BANDPASS, NOTCH }

    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun configure(kind: Kind, sampleRate: Int, freqHz: Float, q: Float = 0.707f, gainDb: Float = 0f) {
        val f = freqHz.coerceIn(10f, sampleRate * 0.45f)
        val w0 = 2.0 * Math.PI * f / sampleRate
        val cosW = kotlin.math.cos(w0)
        val sinW = kotlin.math.sin(w0)
        val alpha = sinW / (2.0 * q)
        val a = Math.pow(10.0, gainDb / 40.0)
        var nb0: Double
        var nb1: Double
        var nb2: Double
        var na0: Double
        var na1: Double
        var na2: Double
        when (kind) {
            Kind.LOWPASS -> {
                nb0 = (1 - cosW) / 2; nb1 = 1 - cosW; nb2 = nb0
                na0 = 1 + alpha; na1 = -2 * cosW; na2 = 1 - alpha
            }
            Kind.HIGHPASS -> {
                nb0 = (1 + cosW) / 2; nb1 = -(1 + cosW); nb2 = nb0
                na0 = 1 + alpha; na1 = -2 * cosW; na2 = 1 - alpha
            }
            Kind.BANDPASS -> {
                nb0 = alpha; nb1 = 0.0; nb2 = -alpha
                na0 = 1 + alpha; na1 = -2 * cosW; na2 = 1 - alpha
            }
            Kind.NOTCH -> {
                nb0 = 1.0; nb1 = -2 * cosW; nb2 = 1.0
                na0 = 1 + alpha; na1 = -2 * cosW; na2 = 1 - alpha
            }
            Kind.PEAKING -> {
                nb0 = 1 + alpha * a; nb1 = -2 * cosW; nb2 = 1 - alpha * a
                na0 = 1 + alpha / a; na1 = -2 * cosW; na2 = 1 - alpha / a
            }
            Kind.LOWSHELF -> {
                val twoSqrtAAlpha = 2 * kotlin.math.sqrt(a) * alpha
                nb0 = a * ((a + 1) - (a - 1) * cosW + twoSqrtAAlpha)
                nb1 = 2 * a * ((a - 1) - (a + 1) * cosW)
                nb2 = a * ((a + 1) - (a - 1) * cosW - twoSqrtAAlpha)
                na0 = (a + 1) + (a - 1) * cosW + twoSqrtAAlpha
                na1 = -2 * ((a - 1) + (a + 1) * cosW)
                na2 = (a + 1) + (a - 1) * cosW - twoSqrtAAlpha
            }
            Kind.HIGHSHELF -> {
                val twoSqrtAAlpha = 2 * kotlin.math.sqrt(a) * alpha
                nb0 = a * ((a + 1) + (a - 1) * cosW + twoSqrtAAlpha)
                nb1 = -2 * a * ((a - 1) + (a + 1) * cosW)
                nb2 = a * ((a + 1) + (a - 1) * cosW - twoSqrtAAlpha)
                na0 = (a + 1) - (a - 1) * cosW + twoSqrtAAlpha
                na1 = 2 * ((a - 1) - (a + 1) * cosW)
                na2 = (a + 1) - (a - 1) * cosW - twoSqrtAAlpha
            }
        }
        b0 = (nb0 / na0).toFloat()
        b1 = (nb1 / na0).toFloat()
        b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat()
        a2 = (na2 / na0).toFloat()
    }

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun process(buffer: FloatArray, length: Int = buffer.size) {
        for (i in 0 until length) buffer[i] = process(buffer[i])
    }

    fun reset() {
        z1 = 0f
        z2 = 0f
    }
}
