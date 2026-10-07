package com.vicechanger.app.audio

import com.vicechanger.app.audio.dsp.Biquad

/**
 * One filter stage of the preset's EQ curve.
 */
data class EqStage(
    val kind: Biquad.Kind,
    val freqHz: Float,
    val q: Float = 0.707f,
    val gainDb: Float = 0f,
)

/**
 * Everything the DSP chain needs to sound like one particular voice. Produced by
 * [com.vicechanger.app.voice.VoiceTransformer] from a [com.vicechanger.app.voice.VoicePreset],
 * consumed by [AudioProcessor] and asserted by the unit tests - so the preset table, the UI
 * sliders and the audio path can never drift apart.
 */
data class ProcessorParams(
    val pitchSemitones: Float = 0f,
    val formantRatio: Float = 1f,
    val noiseSuppression: Float = 0.35f,
    val highpassHz: Float = 75f,
    val eqStages: List<EqStage> = emptyList(),
    val saturationDrive: Float = 1f,
    val saturationMix: Float = 0f,
    val ringModHz: Float = 0f,
    val ringModDepth: Float = 0f,
    val combEnabled: Boolean = false,
    val compressorThresholdDb: Float = -22f,
    val compressorRatio: Float = 4f,
    val compressorAttackMs: Float = 4f,
    val compressorReleaseMs: Float = 140f,
    val compressorMakeupDb: Float = 6f,
    val outputGainDb: Float = 0f,
    val echoReductionEnabled: Boolean = false,
    val echoReductionDb: Float = 18f,
) {
    /** True when the pitch stage has to run at all. */
    val needsPitch: Boolean get() = kotlin.math.abs(pitchSemitones) > 0.05f

    /** True when the spectral stage has real work to do. */
    val needsSpectral: Boolean
        get() = kotlin.math.abs(formantRatio - 1f) > 0.004f || noiseSuppression > 0.01f

    val isRobotEffect: Boolean get() = ringModDepth > 0.01f || combEnabled

    /** One-line description of the whole chain, for logcat and the diagnostics screen. */
    fun summary(): String = StringBuilder()
        .append("pitch=").append("%+.1fst".format(pitchSemitones))
        .append("(x").append("%.3f".format(if (needsPitch) Math.pow(2.0, pitchSemitones / 12.0).toFloat() else 1f)).append(")")
        .append(" formant=x").append("%.2f".format(formantRatio))
        .append(" noise=").append("%.2f".format(noiseSuppression))
        .append(" hp=").append("%.0fHz".format(highpassHz))
        .append(" sat=").append("%.1f/%.2f".format(saturationDrive, saturationMix))
        .append(if (isRobotEffect) " ring=%.0fHz@%.2f".format(ringModHz, ringModDepth) else "")
        .append(" comp=").append("%.0fdB/%.1f:1".format(compressorThresholdDb, compressorRatio))
        .append(" eq=").append(eqStages.size)
        .append(" out=").append("%+.1fdB".format(outputGainDb))
        .append(" echo=").append(if (echoReductionEnabled) "on/%.0fdB".format(echoReductionDb) else "off")
        .toString()
}
