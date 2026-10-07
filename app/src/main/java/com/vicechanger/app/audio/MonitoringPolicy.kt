package com.vicechanger.app.audio

/**
 * Decides how to monitor, given whether a headset is plugged in.
 *
 * This exists because of a real, reported failure: on the phone speaker the user hears their own
 * voice directly through their head, so the transformed voice has to be *loud* to be convincing -
 * but "echo reduction" ducks the microphone exactly when the speaker is loud, which is exactly
 * while the user is speaking. The combination produced "it just sounds like me, nothing
 * happened". With a headset the acoustic loop is closed, so echo reduction must be off and the
 * monitor can run at full level.
 *
 * Pure logic on purpose: it is unit tested, and the live engine only applies the decision.
 */
object MonitoringPolicy {

    /** Echo reduction is bounded when monitoring on the speaker, so it can never choke the voice. */
    const val MAX_SPEAKER_ECHO_REDUCTION_DB = 12f

    data class Decision(
        val headsetDetected: Boolean,
        val headsetLabel: String?,
        val echoReduction: Boolean,
        val echoReductionDb: Float,
        val outputVolume: Float,
        val inputGainDb: Float,
        val headline: String,
        val advice: String,
        /** True when the monitor level was raised because nothing is closing the acoustic loop. */
        val boostedForSpeaker: Boolean,
    )

    fun decide(
        settings: com.vicechanger.app.settings.AppSettings,
        headsetDetected: Boolean,
        headsetLabel: String? = null,
    ): Decision {
        if (headsetDetected) {
            return Decision(
                headsetDetected = true,
                headsetLabel = headsetLabel,
                echoReduction = false,
                echoReductionDb = 0f,
                outputVolume = settings.outputVolume.coerceAtLeast(0.95f),
                inputGainDb = settings.inputGainDb,
                headline = "Monitoring through headset",
                advice = "Headset detected: echo reduction is off and the monitor runs at full " +
                    "level. This is the configuration where the transformed voice is clearest.",
                boostedForSpeaker = false,
            )
        }
        return Decision(
            headsetDetected = false,
            headsetLabel = null,
            echoReduction = settings.echoReduction,
            echoReductionDb = if (settings.echoReduction) {
                settings.echoReductionAmountDb.coerceAtMost(MAX_SPEAKER_ECHO_REDUCTION_DB)
            } else {
                0f
            },
            outputVolume = settings.outputVolume.coerceAtLeast(0.9f),
            inputGainDb = settings.inputGainDb,
            headline = "Monitoring through the phone speaker",
            advice = "On the speaker you also hear your own voice directly through your head, so " +
                "the change is harder to notice and echo reduction has to hold the level down. " +
                "Plug in headphones (or a Bluetooth headset) and press START again for the clear " +
                "result - or use \"Play transformed test tone\" below, which needs no microphone.",
            boostedForSpeaker = true,
        )
    }
}
