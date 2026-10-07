package com.vicechanger.app.audio

import com.vicechanger.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The monitoring decision is the fix for a reported failure ("I pressed START and it still sounds
 * like me"): on the phone speaker the user also hears their own voice directly, and echo reduction
 * ducks the microphone exactly while they speak. These tests pin the rules.
 */
class MonitoringPolicyTest {

    @Test
    fun `a headset disables echo reduction and runs the monitor at full level`() {
        val decision = MonitoringPolicy.decide(
            settings = AppSettings(echoReduction = true, echoReductionAmountDb = 18f, outputVolume = 0.6f),
            headsetDetected = true,
            headsetLabel = "Wired headset (Headset)",
        )
        assertTrue(decision.headsetDetected)
        assertFalse("echo reduction must be off with a headset", decision.echoReduction)
        assertEquals(0f, decision.echoReductionDb, 0f)
        assertTrue("monitor level must be raised", decision.outputVolume >= 0.95f)
        assertEquals("Wired headset (Headset)", decision.headsetLabel)
        assertFalse(decision.boostedForSpeaker)
        assertTrue(decision.headline.contains("headset", ignoreCase = true))
        assertTrue(decision.advice.length > 40)
    }

    @Test
    fun `the speaker keeps echo reduction but bounds it so it cannot choke the voice`() {
        val decision = MonitoringPolicy.decide(
            settings = AppSettings(echoReduction = true, echoReductionAmountDb = 30f, outputVolume = 0.5f),
            headsetDetected = false,
        )
        assertFalse(decision.headsetDetected)
        assertTrue("the user's echo reduction setting is respected", decision.echoReduction)
        assertEquals(
            "bounded for speaker monitoring",
            MonitoringPolicy.MAX_SPEAKER_ECHO_REDUCTION_DB,
            decision.echoReductionDb,
            0.001f,
        )
        assertTrue(decision.outputVolume >= 0.9f)
        assertTrue(decision.boostedForSpeaker)
        assertTrue(decision.advice.contains("headphone", ignoreCase = true))
    }

    @Test
    fun `a user who turned echo reduction off keeps it off on the speaker`() {
        val decision = MonitoringPolicy.decide(
            settings = AppSettings(echoReduction = false, echoReductionAmountDb = 18f),
            headsetDetected = false,
        )
        assertFalse(decision.echoReduction)
        assertEquals(0f, decision.echoReductionDb, 0f)
    }

    @Test
    fun `the speaker advice tells the user what to do`() {
        val speaker = MonitoringPolicy.decide(AppSettings(), headsetDetected = false)
        assertTrue(speaker.advice.contains("speaker", ignoreCase = true))
        assertTrue(speaker.advice.contains("test tone", ignoreCase = true) ||
            speaker.advice.contains("headphone", ignoreCase = true))
    }

    @Test
    fun `the decision never lowers the volume the user asked for`() {
        val loud = MonitoringPolicy.decide(
            settings = AppSettings(outputVolume = 1f),
            headsetDetected = false,
        )
        assertEquals(1f, loud.outputVolume, 0f)
        val quiet = MonitoringPolicy.decide(
            settings = AppSettings(outputVolume = 0.2f),
            headsetDetected = true,
        )
        assertTrue(quiet.outputVolume >= 0.2f)
    }
}
