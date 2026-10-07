package com.vicechanger.app.voice

import com.vicechanger.app.audio.EqStage
import com.vicechanger.app.audio.ProcessorParams
import com.vicechanger.app.audio.dsp.Biquad
import com.vicechanger.app.settings.AppSettings

/**
 * Maps a [VoicePreset] onto DSP parameters. This is the only place that decides what a
 * preset *does* to the audio, which is why the picker, the custom sliders and the tests all
 * agree on the result.
 *
 * The mapping follows three rules:
 *  1. pitch carries the register, formant ratio carries the vocal-tract size, and the two are
 *     kept independent - a pure pitch multiply sounds like a chipmunk and is never used here.
 *  2. brightness drives a high shelf and a presence peak; resonance drives a chest peak.
 *  3. effect strength scales saturation, compression and (for the robot voice) modulation,
 *     and is compensated with an automatic output trim so voices stay at a similar loudness.
 */
object VoiceTransformer {

    const val PRESENCE_HZ = 3_200f
    const val CHEST_RESONANCE_HZ = 320f
    const val BRIGHTNESS_HZ = 6_500f
    const val ROBOT_MODULATION_HZ = 58f

    fun transform(preset: VoicePreset, settings: AppSettings = AppSettings()): ProcessorParams {
        val clamped = preset.clamped()
        val effect = clamped.effectStrength
        val isRobot = clamped.id == BuiltInVoices.ROBOT_GIRL.id

        val eq = buildList {
            // Chest resonance: the "warmth" band that makes a voice read as female or as deep.
            add(
                EqStage(
                    kind = Biquad.Kind.PEAKING,
                    freqHz = CHEST_RESONANCE_HZ,
                    q = 0.9f,
                    gainDb = clamped.resonance,
                ),
            )
            // Presence: consonant intelligibility, which is what keeps the voice understandable
            // when it is squeezed through a game voice chat codec.
            add(
                EqStage(
                    kind = Biquad.Kind.PEAKING,
                    freqHz = PRESENCE_HZ,
                    q = 0.8f,
                    gainDb = clamped.brightness * 0.45f,
                ),
            )
            // Brightness shelf.
            add(
                EqStage(
                    kind = Biquad.Kind.HIGHSHELF,
                    freqHz = BRIGHTNESS_HZ,
                    q = 0.707f,
                    gainDb = clamped.brightness,
                ),
            )
        }

        val noiseSuppression = (clamped.noiseSuppression * 0.6f + settings.noiseSuppression * 0.6f)
            .coerceIn(0f, 1f)

        return ProcessorParams(
            pitchSemitones = clamped.pitch,
            formantRatio = clamped.formant,
            noiseSuppression = noiseSuppression,
            highpassHz = clamped.highpassHz,
            eqStages = eq,
            saturationDrive = 1f + effect * 3f,
            saturationMix = if (isRobot) effect * 0.55f else effect * 0.35f,
            ringModHz = if (isRobot) ROBOT_MODULATION_HZ else 0f,
            ringModDepth = if (isRobot) (0.2f + effect * 0.5f).coerceAtMost(0.75f) else 0f,
            combEnabled = isRobot,
            compressorThresholdDb = -20f - effect * 8f,
            compressorRatio = clamped.compressionRatio,
            compressorAttackMs = 4f,
            compressorReleaseMs = 140f - effect * 40f,
            compressorMakeupDb = 5f + effect * 4f,
            outputGainDb = automaticTrim(clamped),
            echoReductionEnabled = settings.echoReduction,
            echoReductionDb = settings.echoReductionAmountDb,
        )
    }

    /**
     * Loudness compensation: everything that shifts the spectrum up also adds energy, so without
     * this the bright presets would be noticeably louder than the deep ones. The limiter still
     * guarantees the ceiling; this only keeps the presets comparable.
     *
     * The base is deliberately positive: a voice changer that is quieter than the user's own voice
     * is unusable when monitoring on the phone speaker, which is a real failure that was reported
     * from the device.
     */
    fun automaticTrim(preset: VoicePreset): Float {
        val trim = 4.0f -
            0.35f * preset.pitch -
            0.30f * preset.brightness -
            8f * (preset.formant - 1f) -
            1.5f * preset.effectStrength
        return trim.coerceIn(-4f, 6f)
    }

    /** Pitch ratio the DSP will actually use - exposed so the UI and tests can show it. */
    fun pitchRatio(preset: VoicePreset): Float =
        Math.pow(2.0, preset.clamped().pitch / 12.0).toFloat()

    /** Human readable description of what a preset does, used on the voice cards. */
    fun describe(preset: VoicePreset): String {
        val ratio = pitchRatio(preset)
        val percent = ((ratio - 1f) * 100f).toInt()
        val formant = "%.2f".format(preset.clamped().formant)
        return "+$percent% pitch, formant x$formant"
    }
}
