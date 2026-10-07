package com.vicechanger.app.audio

import com.vicechanger.app.Signals
import com.vicechanger.app.audio.dsp.Biquad
import com.vicechanger.app.audio.dsp.EqBank
import com.vicechanger.app.audio.dsp.LevelMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Error-message contract and metering/filter maths. Every message the user can see must exist,
 * be specific, and tell them what to do - these tests are what keeps that true.
 */
class AudioFailureTest {

    @Test
    fun `every failure carries a non-empty user facing message`() {
        for (failure in AudioFailure.entries) {
            assertTrue("${failure.name} has no message", failure.message.isNotBlank())
            assertTrue("${failure.name} message is too short", failure.message.length > 20)
            assertTrue("${failure.name} should end with a period", failure.message.endsWith("."))
        }
    }

    @Test
    fun `the specification's messages are present`() {
        assertTrue(AudioFailure.PERMISSION_DENIED.message.startsWith("Microphone permission is required"))
        assertTrue(AudioFailure.MICROPHONE_UNAVAILABLE.message.contains("microphone is currently unavailable"))
        assertTrue(AudioFailure.ROUTING_UNSUPPORTED.message.contains("external microphone routing"))
        assertTrue(AudioFailure.PROCESSING_FAILED.message.contains("Try another voice preset"))
        assertTrue(AudioFailure.AUDIO_FOCUS_LOST.message.contains("call"))
    }

    @Test
    fun `an audio exception exposes the same message`() {
        val exception = AudioException(AudioFailure.PERMISSION_DENIED)
        assertEquals(AudioFailure.PERMISSION_DENIED.message, exception.message)
    }

    @Test
    fun `meter decibel maths is correct`() {
        assertEquals(0f, LevelMeter.toDb(1f), 0.01f)
        assertEquals(-6.02f, LevelMeter.toDb(0.5f), 0.05f)
        assertEquals(-20f, LevelMeter.toDb(0.1f), 0.05f)
        assertEquals(LevelMeter.MIN_DB, LevelMeter.toDb(0f), 0f)
        assertEquals(LevelMeter.MIN_DB, LevelMeter.toDb(1e-9f), 0f)
    }

    @Test
    fun `meter tracks a loud block and a silent block`() {
        val meter = LevelMeter(48_000)
        val loud = Signals.sine(400f, 0.2f, 48_000, amplitude = 0.8f)
        repeat(5) { meter.process(loud, loud.size) }
        assertTrue("loud input must read high: ${meter.rmsDb}", meter.rmsDb > -6f)
        assertTrue(meter.level01() > 0.8f)

        val quiet = Signals.silence(0.5f, 48_000)
        repeat(20) { meter.process(quiet, quiet.size) }
        assertTrue("silence must fall back towards the floor: ${meter.rmsDb}", meter.rmsDb < -30f)
    }

    @Test
    fun `meter reports peak hold`() {
        val meter = LevelMeter(48_000)
        val loud = Signals.sine(300f, 0.1f, 48_000, amplitude = 0.9f)
        meter.process(loud, loud.size)
        assertTrue(meter.peakDb > -2f)
        assertTrue(meter.peakHoldDb >= meter.peakDb - 1f)
    }

    @Test
    fun `level bar mapping spans the meter floor to full scale`() {
        val meter = LevelMeter(48_000)
        assertEquals(0f, meter.level01(LevelMeter.MIN_DB), 0f)
        assertEquals(1f, meter.level01(0f), 0f)
        assertEquals(0.5f, meter.level01(-30f), 0.01f)
        assertEquals(0f, meter.level01(-200f), 0f)
        assertEquals(1f, meter.level01(12f), 0f)
    }

    @Test
    fun `highpass filter removes dc and keeps speech band`() {
        val filter = Biquad()
        filter.configure(Biquad.Kind.HIGHPASS, 48_000, 100f, 0.707f)
        val dc = FloatArray(4_800) { 1f }
        filter.process(dc, dc.size)
        val tail = Signals.rms(dc, 3_000, dc.size)
        assertTrue("dc must be removed, residual $tail", tail < 0.05f)

        val speech = Biquad()
        speech.configure(Biquad.Kind.HIGHPASS, 48_000, 100f, 0.707f)
        val tone = Signals.sine(1_000f, 0.5f, 48_000, amplitude = 0.5f)
        val before = Signals.rms(tone, 12_000, tone.size)
        speech.process(tone, tone.size)
        val after = Signals.rms(tone, 12_000, tone.size)
        assertEquals(before, after, before * 0.05f)
    }

    @Test
    fun `eq bank applies its stages in order and reports them`() {
        val bank = EqBank()
        bank.add(Biquad.Kind.HIGHSHELF, 48_000, 6_500f, gainDb = 6f)
        bank.add(Biquad.Kind.PEAKING, 48_000, 320f, 0.9f, 3f)
        assertEquals(2, bank.stageLabels().size)
        assertTrue(bank.stageLabels()[0].startsWith("HIGHSHELF"))

        val tone = Signals.sine(6_000f, 0.5f, 48_000, amplitude = 0.3f)
        val before = Signals.rms(tone, 12_000, tone.size)
        bank.process(tone, tone.size)
        val after = Signals.rms(tone, 12_000, tone.size)
        assertTrue("high shelf must boost 6 kHz ($before -> $after)", after > before)
    }

    @Test
    fun `empty eq bank is a bypass`() {
        val bank = EqBank()
        assertTrue(bank.isEmpty)
        val tone = Signals.sine(500f, 0.1f, 48_000, amplitude = 0.3f)
        val original = tone.copyOf()
        bank.process(tone, tone.size)
        for (i in tone.indices) assertEquals(original[i], tone[i], 0f)
    }

    @Test
    fun `preset parameter flags describe the chain that will be built`() {
        val plain = ProcessorParams(pitchSemitones = 0f, formantRatio = 1f, noiseSuppression = 0f)
        assertTrue(!plain.needsPitch)
        assertTrue(!plain.needsSpectral)
        assertTrue(!plain.isRobotEffect)

        val full = ProcessorParams(pitchSemitones = 4f, formantRatio = 1.1f, ringModDepth = 0.5f)
        assertTrue(full.needsPitch)
        assertTrue(full.needsSpectral)
        assertTrue(full.isRobotEffect)
        assertNotNull(full.eqStages)
    }
}
