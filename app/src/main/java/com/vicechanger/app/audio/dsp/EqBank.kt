package com.vicechanger.app.audio.dsp

/** A chain of biquads applied in order: rumble filter, warmth, presence, brightness. */
class EqBank {
    private val stages = ArrayList<Biquad>(6)
    private val labels = ArrayList<String>(6)

    fun add(kind: Biquad.Kind, sampleRate: Int, freqHz: Float, q: Float = 0.707f, gainDb: Float = 0f): EqBank {
        val bq = Biquad()
        bq.configure(kind, sampleRate, freqHz, q, gainDb)
        stages.add(bq)
        labels.add(kind.name + "@" + freqHz.toInt())
        return this
    }

    fun clear() {
        stages.clear()
        labels.clear()
    }

    /** Names of the configured stages - used by the unit tests to assert preset wiring. */
    fun stageLabels(): List<String> = labels.toList()

    val isEmpty: Boolean get() = stages.isEmpty()

    fun process(buffer: FloatArray, length: Int = buffer.size) {
        if (stages.isEmpty()) return
        for (i in 0 until length) {
            var x = buffer[i]
            for (s in stages) x = s.process(x)
            buffer[i] = x
        }
    }

    fun reset() {
        for (s in stages) s.reset()
    }
}
