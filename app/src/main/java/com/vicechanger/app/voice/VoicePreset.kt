package com.vicechanger.app.voice

import com.vicechanger.app.audio.AudioConfig

/**
 * One female voice. This is the single source of truth: the preset list feeds the picker
 * cards, the custom sliders, the DSP chain and the tests, so a preset can never behave
 * differently in two places.
 *
 * [pitch] is in semitones, [formant] is a frequency ratio, [brightness] and [resonance] are
 * shelving/peaking gains in dB, and [effectStrength] drives saturation and any character
 * effect the preset has.
 */
data class VoicePreset(
    val id: String,
    val name: String,
    val tagline: String,
    val pitch: Float,
    val formant: Float,
    val brightness: Float,
    val resonance: Float,
    val effectStrength: Float,
    val noiseSuppression: Float = 0.35f,
    val highpassHz: Float = 75f,
    val compressionRatio: Float = 4f,
    val isCustom: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "preset id must not be blank" }
    }

    fun clamped(): VoicePreset = copy(
        pitch = pitch.coerceIn(AudioConfig.MIN_PITCH_SEMITONES, AudioConfig.MAX_PITCH_SEMITONES),
        formant = formant.coerceIn(AudioConfig.MIN_FORMANT, AudioConfig.MAX_FORMANT),
        brightness = brightness.coerceIn(-8f, 8f),
        resonance = resonance.coerceIn(-6f, 8f),
        effectStrength = effectStrength.coerceIn(0f, 1f),
        noiseSuppression = noiseSuppression.coerceIn(0f, 1f),
        highpassHz = highpassHz.coerceIn(40f, 200f),
        compressionRatio = compressionRatio.coerceIn(1f, 12f),
    )

    /** Describes the voice in words, for the picker card. */
    fun summary(): String {
        val pitchWord = when {
            pitch >= 7f -> "very high"
            pitch >= 5f -> "high"
            pitch >= 3f -> "moderately raised"
            pitch >= 1f -> "slightly raised"
            else -> "low female"
        }
        val toneWord = when {
            brightness >= 4f -> "bright"
            brightness >= 1.5f -> "clear"
            brightness >= -0.5f -> "balanced"
            else -> "warm"
        }
        return "$pitchWord pitch, $toneWord tone"
    }
}

/**
 * The ten built-in voices. Values were chosen so the pitch shift carries the gender read and
 * the formant shift keeps the resonances human - a pure pitch multiply is what makes cheap
 * voice changers sound like a chipmunk, and that is exactly what these two knobs avoid.
 *
 * The pitch/formant values are deliberately assertive (roughly +2.5 to +9.5 semitones with a
 * 6-30% shorter vocal tract). A gentler setting reads as "slightly different" when the user also
 * hears their own voice through their head, which is the failure mode reported from a real
 * device: the user pressed START and concluded nothing had happened.
 */
object BuiltInVoices {

    val NATURAL_GIRL = VoicePreset(
        id = "natural_girl",
        name = "Natural Girl",
        tagline = "Everyday female voice, minimal processing",
        pitch = 5.5f,
        formant = 1.16f,
        brightness = 2.0f,
        resonance = 1.5f,
        effectStrength = 0.15f,
        noiseSuppression = 0.30f,
    )

    val SOFT_GIRL = VoicePreset(
        id = "soft_girl",
        name = "Soft Girl",
        tagline = "Gentle and warm, no harshness",
        pitch = 4.5f,
        formant = 1.12f,
        brightness = -1.5f,
        resonance = 2.5f,
        effectStrength = 0.22f,
        noiseSuppression = 0.40f,
        compressionRatio = 3f,
    )

    val CUTE_GIRL = VoicePreset(
        id = "cute_girl",
        name = "Cute Girl",
        tagline = "Higher and brighter with a light character",
        pitch = 7.5f,
        formant = 1.24f,
        brightness = 3.5f,
        resonance = 1.0f,
        effectStrength = 0.30f,
        noiseSuppression = 0.35f,
    )

    val GAMER_GIRL = VoicePreset(
        id = "gamer_girl",
        name = "Gamer Girl",
        tagline = "Clear mids and controlled bass for callouts",
        pitch = 6.0f,
        formant = 1.18f,
        brightness = 2.5f,
        resonance = 0.5f,
        effectStrength = 0.25f,
        noiseSuppression = 0.45f,
        highpassHz = 110f,
        compressionRatio = 6f,
    )

    val MATURE_WOMAN = VoicePreset(
        id = "mature_woman",
        name = "Mature Woman",
        tagline = "Lower female register, natural speech",
        pitch = 3.0f,
        formant = 1.09f,
        brightness = -1.0f,
        resonance = 3.0f,
        effectStrength = 0.12f,
        noiseSuppression = 0.30f,
        compressionRatio = 3f,
    )

    val BRIGHT_GIRL = VoicePreset(
        id = "bright_girl",
        name = "Bright Girl",
        tagline = "Energetic with crisp consonants",
        pitch = 6.5f,
        formant = 1.21f,
        brightness = 5.0f,
        resonance = 1.0f,
        effectStrength = 0.25f,
        noiseSuppression = 0.35f,
    )

    val DEEP_FEMALE = VoicePreset(
        id = "deep_female",
        name = "Deep Female",
        tagline = "Stronger resonance, still clearly female",
        pitch = 2.5f,
        formant = 1.06f,
        brightness = -2.0f,
        resonance = 3.5f,
        effectStrength = 0.10f,
        noiseSuppression = 0.30f,
        highpassHz = 65f,
        compressionRatio = 3.5f,
    )

    val ANIME_STYLE = VoicePreset(
        id = "anime_style",
        name = "Anime Style",
        tagline = "Stylised, high and bright - original character, nobody real",
        pitch = 9.5f,
        formant = 1.30f,
        brightness = 5.0f,
        resonance = 0.5f,
        effectStrength = 0.45f,
        noiseSuppression = 0.40f,
    )

    val ROBOT_GIRL = VoicePreset(
        id = "robot_girl",
        name = "Robot Girl",
        tagline = "Female pitch with a light synthetic modulation",
        pitch = 5.5f,
        formant = 1.16f,
        brightness = 2.0f,
        resonance = 0.5f,
        effectStrength = 0.70f,
        noiseSuppression = 0.50f,
        compressionRatio = 6f,
    )

    val CUSTOM_GIRL = VoicePreset(
        id = "custom_girl",
        name = "Custom Girl",
        tagline = "Your own pitch, formant, brightness, resonance and effect",
        pitch = 6.0f,
        formant = 1.18f,
        brightness = 2.0f,
        resonance = 1.5f,
        effectStrength = 0.25f,
        noiseSuppression = 0.35f,
        isCustom = true,
    )

    /** Exactly ten presets, in picker order. */
    val ALL: List<VoicePreset> = listOf(
        NATURAL_GIRL,
        SOFT_GIRL,
        CUTE_GIRL,
        GAMER_GIRL,
        MATURE_WOMAN,
        BRIGHT_GIRL,
        DEEP_FEMALE,
        ANIME_STYLE,
        ROBOT_GIRL,
        CUSTOM_GIRL,
    )

    const val CUSTOM_ID = "custom_girl"

    fun byId(id: String): VoicePreset = ALL.firstOrNull { it.id == id } ?: NATURAL_GIRL
}
