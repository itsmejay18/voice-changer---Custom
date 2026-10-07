package com.vicechanger.app.audio

/**
 * Everything that can go wrong on the audio path, mapped to the exact message the UI shows.
 * The messages are the contract the UI renders, so they live here next to the failure they
 * describe instead of being scattered across screens.
 */
enum class AudioFailure(val message: String) {
    PERMISSION_DENIED("Microphone permission is required for voice processing."),
    MICROPHONE_UNAVAILABLE(
        "The microphone is currently unavailable. Close other applications using the " +
            "microphone and try again.",
    ),
    ROUTING_UNSUPPORTED("Your device does not support external microphone routing for this mode."),
    AUDIO_INIT_FAILED("Voice processing failed. Try another voice preset or restart the audio engine."),
    RECORD_INIT_FAILED("Recording could not start. Check that no other app is using the microphone."),
    OUTPUT_INIT_FAILED("Audio output could not be opened. Check the selected output device."),
    AUDIO_FOCUS_LOST("Another app took over audio (call or media). Voice processing stopped."),
    PROCESSING_FAILED("Voice processing failed. Try another voice preset or restart the audio engine."),
    RECORDING_EMPTY("Nothing was recorded. Hold the phone closer and try again."),
    SAVE_FAILED("The processed voice could not be saved. Free some storage and try again."),
    SHARE_FAILED("No application accepted the audio file."),
    FILE_UNREADABLE("The saved recording could not be read back."),
    ;
}
