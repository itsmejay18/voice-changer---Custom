package com.vicechanger.app.voice

import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.dsp.SpectrumAnalysis
import com.vicechanger.app.settings.AppSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The answer to "I pressed START and it just sounds like me".
 *
 * This runs the *real* DSP chain - the same [OfflineRenderer] the voice message path uses - over
 * a synthetic vowel, on the device, and reports the numbers:
 *
 *   input pitch 180 Hz -> output pitch X Hz (expected +N semitones)
 *   envelope centroid A -> B Hz
 *
 * It also hands back the processed audio so the UI can play it: no microphone, no acoustic loop,
 * no echo reduction - just what the chain produces. If the tone comes out shifted, the
 * transformation works and any "no change" is a monitoring-path problem; if it does not, the bug
 * is in the chain itself.
 */
object DspSelfTest {

    const val TEST_FUNDAMENTAL_HZ = 180f
    private const val TEST_SECONDS = 2.5f

    /** Peak amplitude of the synthesised vowel handed to the chain. */
    private const val TARGET_PEAK = 0.25f

    data class Result(
        val presetName: String,
        val expectedSemitones: Float,
        val expectedRatio: Float,
        val inputPitchHz: Float,
        val outputPitchHz: Float,
        val measuredRatio: Float,
        val measuredSemitones: Float,
        val inputCentroidHz: Float,
        val outputCentroidHz: Float,
        val inputPeakDbfs: Float,
        val outputPeakDbfs: Float,
        val pipelineLatencyMs: Float,
        val renderMillis: Long,
        val passed: Boolean,
    ) {
        /** One line the UI can show verbatim. */
        fun summary(): String = buildString {
            append("Voice: ${presetName}\n")
            append("Pitch: %.1f Hz -> %.1f Hz".format(inputPitchHz, outputPitchHz))
            append("  (%.1f semitones measured, %.1f requested)\n".format(measuredSemitones, expectedSemitones))
            append("Vocal tract envelope: %.0f Hz -> %.0f Hz\n".format(inputCentroidHz, outputCentroidHz))
            append("Peak: %.1f dBFS -> %.1f dBFS\n".format(inputPeakDbfs, outputPeakDbfs))
            append("Pipeline delay: %.0f ms   (rendered in %d ms)\n".format(pipelineLatencyMs, renderMillis))
            append(if (passed) "RESULT: the voice chain is transforming audio on this device." else
                "RESULT: the chain did NOT shift the pitch - this is a real bug, report it.")
        }
    }

    data class Outcome(val result: Result, val processed: FloatArray, val sampleRate: Int)

    /** Synthesises a vowel, runs it through the chain for [preset], and measures the result. */
    fun run(
        preset: VoicePreset,
        settings: AppSettings,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
    ): Outcome {
        val input = syntheticVowel(sampleRate, TEST_SECONDS)
        // Echo reduction and the noise gate are *monitoring/steady-noise* concerns. Leaving them on
        // would duck a steady test tone and hide the transformation this test exists to measure.
        val params = VoiceTransformer.transform(
            preset,
            settings.copy(echoReduction = false, noiseSuppression = 0f),
        )
        val started = System.currentTimeMillis()
        val rendered = OfflineRenderer.render(
            input = input,
            sampleRate = sampleRate,
            params = params,
            inputGainDb = settings.inputGainDb,
        )
        val renderMillis = System.currentTimeMillis() - started

        val expectedRatio = VoiceTransformer.pitchRatio(preset)
        val inputPitch = SpectrumAnalysis.fundamentalHz(input, sampleRate)
        val outputPitch = SpectrumAnalysis.fundamentalHz(rendered.samples, sampleRate)
        val measuredRatio = if (inputPitch > 1f) outputPitch / inputPitch else 0f
        val measuredSemitones = if (measuredRatio > 0f) {
            12f * (kotlin.math.ln(measuredRatio.toDouble()) / kotlin.math.ln(2.0)).toFloat()
        } else {
            0f
        }
        val tolerance = 0.12f * expectedRatio
        return Outcome(
            result = Result(
                presetName = preset.name,
                expectedSemitones = preset.clamped().pitch,
                expectedRatio = expectedRatio,
                inputPitchHz = inputPitch,
                outputPitchHz = outputPitch,
                measuredRatio = measuredRatio,
                measuredSemitones = measuredSemitones,
                inputCentroidHz = SpectrumAnalysis.spectralCentroid(input, sampleRate),
                outputCentroidHz = SpectrumAnalysis.spectralCentroid(rendered.samples, sampleRate),
                inputPeakDbfs = SpectrumAnalysis.peakDbfs(input),
                outputPeakDbfs = SpectrumAnalysis.peakDbfs(rendered.samples),
                pipelineLatencyMs = rendered.measuredLatencySamples * 1000f / sampleRate,
                renderMillis = renderMillis,
                passed = kotlin.math.abs(measuredRatio - expectedRatio) <= tolerance,
            ),
            processed = rendered.samples,
            sampleRate = sampleRate,
        )
    }

    /**
     * A steady "ahh": a harmonic stack shaped by three formants with a slow vibrato, so the
     * pitch tracker and the formant warp both have something realistic to work on.
     */
    fun syntheticVowel(sampleRate: Int, seconds: Float): FloatArray {
        val count = (seconds * sampleRate).toInt()
        val output = FloatArray(count)
        val formants = listOf(Triple(700f, 1.0f, 90f), Triple(1220f, 0.55f, 120f), Triple(2600f, 0.25f, 160f))
        // The fundamental's phase is integrated on purpose. Evaluating sin(2*pi*f*t) with a *moving*
        // f does not give vibrato, it gives a chirp (instantaneous frequency f + t*df/dt), which
        // smears the periodicity so badly that no pitch tracker can measure it.
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val fundamental = TEST_FUNDAMENTAL_HZ * (1.0 + 0.012 * sin(2.0 * PI * 5.2 * t))
            phase += 2.0 * PI * fundamental / sampleRate
            val fade = minOf(1.0, t / 0.08, (seconds - t) / 0.12).coerceAtLeast(0.0)
            var sample = 0.0
            for (harmonic in 1..30) {
                val frequency = fundamental * harmonic
                if (frequency > sampleRate * 0.45) break
                var amplitude = 0.0
                for ((centre, gain, bandwidth) in formants) {
                    val distance = (frequency - centre) / bandwidth
                    amplitude += gain / (1.0 + distance * distance)
                }
                if (amplitude < 1e-3) continue
                // A real glottal pulse rolls off about 12 dB per octave, so the upper harmonics are
                // weak. A flat spectrum swamps the pitch tracker with broadband energy and makes the
                // fundamental unmeasurable.
                sample += amplitude * sin(harmonic * phase) / (harmonic * harmonic).toDouble()
            }
            output[i] = (sample * fade * cos(2.0 * PI * 0.7 * t)).toFloat()
        }
        // Normalise to a known peak so the chain sees a realistic, audible level.
        var peak = 0f
        for (value in output) {
            val magnitude = kotlin.math.abs(value)
            if (magnitude > peak) peak = magnitude
        }
        if (peak > 1e-6f) {
            val scale = TARGET_PEAK / peak
            for (i in output.indices) output[i] *= scale
        }
        return output
    }
}
