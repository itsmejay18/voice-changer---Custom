package com.vicechanger.app.audio

/**
 * Fixed engine configuration. Everything in the audio path derives from these numbers,
 * so the live engine, the offline (voice message) renderer and the unit tests all agree
 * on block sizes and sample rate.
 */
object AudioConfig {
    /** 48 kHz mono: the rate every Android device runs natively, so no resampling by the OS. */
    const val SAMPLE_RATE = 48_000
    const val CHANNEL_COUNT = 1

    /** Streaming block: 10 ms of audio at 48 kHz. */
    const val BLOCK_SIZE = 480

    /** Aggressive low-latency block: 5 ms. Used when "low latency mode" is on. */
    const val BLOCK_SIZE_LOW_LATENCY = 240

    /** STFT size for the pitch shifter and the spectral stage (~21 ms of history). */
    const val FFT_FRAME_SIZE = 1024

    /** STFT hop: 75 % overlap, ~5.3 ms of new audio per frame. */
    const val FFT_HOP_SIZE = 256

    const val MIN_PITCH_SEMITONES = -6f
    const val MAX_PITCH_SEMITONES = 12f
    const val MIN_FORMANT = 0.85f
    const val MAX_FORMANT = 1.35f

    /** Hard output ceiling. The limiter must never let a sample past this. */
    const val OUTPUT_CEILING = 0.97f

    /** Absolute input level below which the audio path treats the mic as silent. */
    const val SILENCE_FLOOR = 0.0005f
}
