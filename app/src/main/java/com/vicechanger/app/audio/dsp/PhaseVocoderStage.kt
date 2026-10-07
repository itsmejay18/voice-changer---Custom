package com.vicechanger.app.audio.dsp

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Streaming phase vocoder with identity phase locking (Laroche & Dolson): the phase of
 * every bin in a spectral peak's region is locked to that peak's accumulated phase, which
 * is what removes most of the classic "phasiness" of a plain vocoder.
 *
 * [stretch] > 1 makes the signal longer, which the resampler downstream then plays back
 * faster - the combination is a pitch shift that keeps the original duration.
 */
class PhaseVocoderStage(
    sampleRate: Int,
    frameSize: Int = 1024,
    analysisHop: Int = 256,
    val stretch: Float,
) : StftStage(sampleRate, frameSize, analysisHop, synthesisHop = synthesisHopFor(analysisHop, stretch)) {

    private val binCount = frameSize / 2 + 1
    private val previousPhase = FloatArray(binCount)
    private val accumulatedPhase = FloatArray(binCount)
    private val magnitude = FloatArray(binCount)
    private val phase = FloatArray(binCount)
    private val deviation = FloatArray(binCount)
    private val peakRegion = IntArray(binCount)
    private val peaks = IntArray(binCount)
    private var previousMaxMagnitude = 0f
    private var firstFrame = true

    /** Bins whose magnitude was below the peak floor in the last frame - phase unlocked. */
    var lastUnlockedBins: Int = 0
        private set
    /** Bins identified as spectral peaks in the last frame. */
    var lastPeakCount: Int = 0
        private set

    private val hopScale = (2.0 * PI / frameSize).toFloat()

    override fun processFrame(re: FloatArray, im: FloatArray, frameIndex: Long) {
        var maxMagnitude = 0f
        for (k in 0 until binCount) {
            val r = re[k]
            val i = im[k]
            val mag = hypot(r, i)
            magnitude[k] = mag
            phase[k] = atan2(i, r)
            if (mag > maxMagnitude) maxMagnitude = mag
        }
        val floor = maxOf(maxMagnitude * 0.0005f, 1e-6f)

        for (k in 0 until binCount) {
            val expected = hopScale * analysisHop * k
            var delta = phase[k] - previousPhase[k] - expected
            delta -= (2.0 * PI * kotlin.math.floor((delta + PI) / (2.0 * PI))).toFloat()
            deviation[k] = (delta / hopScale) / analysisHop
        }

        buildPeakRegions(floor)

        var unlocked = 0
        for (k in 0 until binCount) {
            val region = peakRegion[k]
            if (region >= 0) {
                val peak = peaks[region]
                if (peak == k) accumulatedPhase[k] += hopScale * (k + deviation[k]) * synthesisHop
                val locked = accumulatedPhase[peak] + (phase[k] - phase[peak])
                re[k] = magnitude[k] * cos(locked)
                im[k] = magnitude[k] * sin(locked)
            } else {
                unlocked++
                accumulatedPhase[k] += hopScale * (k + deviation[k]) * synthesisHop
                re[k] = magnitude[k] * cos(accumulatedPhase[k])
                im[k] = magnitude[k] * sin(accumulatedPhase[k])
            }
        }
        lastUnlockedBins = unlocked
        lastPeakCount = peaksCount

        System.arraycopy(phase, 0, previousPhase, 0, binCount)
        previousMaxMagnitude = maxMagnitude
        firstFrame = false
    }

    private var peaksCount = 0

    private fun buildPeakRegions(floor: Float) {
        peaksCount = 0
        for (k in 1 until binCount - 1) {
            val mag = magnitude[k]
            if (mag > floor && mag > magnitude[k - 1] && mag >= magnitude[k + 1]) {
                peaks[peaksCount++] = k
            }
        }
        if (peaksCount == 0) {
            java.util.Arrays.fill(peakRegion, -1)
            return
        }
        java.util.Arrays.fill(peakRegion, -1)
        for (p in 0 until peaksCount) {
            val peak = peaks[p]
            val lower = if (p == 0) 0 else (peaks[p - 1] + peak) / 2
            val upper = if (p == peaksCount - 1) binCount - 1 else (peak + peaks[p + 1]) / 2
            for (k in lower..upper) peakRegion[k] = p
            // The peak itself must be locked to its own region even if it sits on a boundary.
            peakRegion[peak] = p
        }
    }

    /** True until the first processed frame, when no reliable phase history exists yet. */
    val isWarmingUp: Boolean get() = firstFrame

    override fun reset() {
        super.reset()
        previousPhase.fill(0f)
        accumulatedPhase.fill(0f)
        peakRegion.fill(-1)
        peaksCount = 0
        firstFrame = true
        previousMaxMagnitude = 0f
    }

    companion object {
        fun synthesisHopFor(analysisHop: Int, stretch: Float): Int =
            maxOf(1, Math.round(analysisHop * stretch).toInt())

        /** Semitone offset to linear pitch ratio. */
        fun ratioFor(semitones: Float): Float = Math.pow(2.0, semitones / 12.0).toFloat()
    }
}
