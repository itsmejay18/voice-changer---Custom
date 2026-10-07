package com.vicechanger.app.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class WavCodecTest {

    @Test
    fun `encode then parse round trips the samples`() {
        val samples = FloatArray(1000) { kotlin.math.sin(it * 0.05).toFloat() * 0.5f }
        val encoded = WavCodec.encode(samples, 48_000)

        assertEquals(44 + samples.size * 2, encoded.size)
        assertEquals("RIFF", String(encoded, 0, 4))
        assertEquals("WAVE", String(encoded, 8, 4))

        val parsed = WavCodec.parse(encoded)
        assertNotNull(parsed)
        assertEquals(48_000, parsed!!.sampleRate)
        assertEquals(1, parsed.channels)
        assertEquals(16, parsed.bitsPerSample)
        assertEquals(samples.size, parsed.samples.size)
        for (i in samples.indices) {
            assertEquals(samples[i], parsed.samples[i], 1f / 32767f)
        }
    }

    @Test
    fun `header carries the right sizes`() {
        val samples = FloatArray(500)
        val encoded = WavCodec.encode(samples, 44_100)
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4)
        assertEquals(36 + samples.size * 2, buffer.int)
        buffer.position(24)
        assertEquals(44_100, buffer.int)
        buffer.position(28)
        assertEquals(44_100 * 2, buffer.int) // byte rate for mono 16-bit
        buffer.position(32)
        assertEquals(2.toShort().toInt(), buffer.short.toInt()) // block align
        buffer.position(40)
        assertEquals(samples.size * 2, buffer.int) // data chunk size
    }

    @Test
    fun `streaming writer produces a readable file and patches its header`() {
        val file = File.createTempFile("vicechanger-test", ".wav")
        try {
            val writer = StreamingWavWriter(file, 48_000)
            val block = ShortArray(480) { (it % 100).toShort() }
            repeat(20) { writer.append(block, block.size) }
            assertEquals(9600L, writer.sampleCount)
            writer.close()

            val parsed = WavCodec.read(file)
            assertNotNull(parsed)
            assertEquals(9600, parsed!!.samples.size)
            assertEquals(48_000, parsed.sampleRate)
            assertEquals(0.2f, parsed.durationSeconds, 0.01f)
            assertEquals(0f, parsed.samples[0], 1e-4f)
            assertEquals(99f / 32768f, parsed.samples[99], 1e-4f)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `write writes a file that read can parse`() {
        val file = File.createTempFile("vicechanger-write", ".wav")
        try {
            val samples = FloatArray(2400) { kotlin.math.cos(it * 0.01).toFloat() * 0.25f }
            WavCodec.write(file, samples, 48_000)
            val parsed = WavCodec.read(file)
            assertNotNull(parsed)
            assertEquals(samples.size, parsed!!.samples.size)
            for (i in 0 until samples.size step 97) {
                assertEquals(samples[i], parsed.samples[i], 1f / 32767f)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `reading a missing or empty file returns null`() {
        assertNull(WavCodec.read(File("/tmp/definitely-not-here-${System.nanoTime()}.wav")))
        val empty = File.createTempFile("vicechanger-empty", ".wav")
        try {
            assertNull(WavCodec.read(empty))
        } finally {
            empty.delete()
        }
    }

    @Test
    fun `garbage is rejected instead of parsed`() {
        assertNull(WavCodec.parse(ByteArray(100) { 1 }))
        assertNull(WavCodec.parse(ByteArray(4)))
        assertNull(WavCodec.parse("RIFFxxxxWAVE".toByteArray()))
    }

    @Test
    fun `pcm conversion clamps and scales`() {
        assertEquals(0.toShort(), WavCodec.toPcm16(0f))
        assertEquals(32767.toShort(), WavCodec.toPcm16(1f))
        assertEquals(32767.toShort(), WavCodec.toPcm16(4f))
        // Full scale is symmetric at +-32767, so the limiter ceiling can never emit -32768.
        assertEquals((-32767).toShort(), WavCodec.toPcm16(-4f))
        assertEquals((-32767).toShort(), WavCodec.toPcm16(-1f))
        assertEquals(16384.toShort(), WavCodec.toPcm16(0.5f))
        assertEquals(0.5f, WavCodec.toFloat(WavCodec.toPcm16(0.5f)), 1e-4f)
    }

    @Test
    fun `a 32 bit float wav is also accepted`() {
        val samples = floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f)
        val bytes = ByteBuffer.allocate(44 + samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray())
        bytes.putInt(36 + samples.size * 4)
        bytes.put("WAVE".toByteArray())
        bytes.put("fmt ".toByteArray())
        bytes.putInt(16)
        bytes.putShort(3) // IEEE float
        bytes.putShort(1)
        bytes.putInt(48_000)
        bytes.putInt(48_000 * 4)
        bytes.putShort(4)
        bytes.putShort(32)
        bytes.put("data".toByteArray())
        bytes.putInt(samples.size * 4)
        samples.forEach { bytes.putFloat(it) }

        val parsed = WavCodec.parse(bytes.array())
        assertNotNull(parsed)
        assertEquals(32, parsed!!.bitsPerSample)
        for (i in samples.indices) assertEquals(samples[i], parsed.samples[i], 1e-6f)
    }

    @Test
    fun `streaming writer can be closed twice without corrupting the file`() {
        val file = File.createTempFile("vicechanger-double-close", ".wav")
        try {
            val writer = StreamingWavWriter(file, 48_000)
            writer.append(ShortArray(100) { 1 }, 100)
            writer.close()
            writer.close()
            val parsed = WavCodec.read(file)
            assertNotNull(parsed)
            assertEquals(100, parsed!!.samples.size)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `duration and peak are derived from the data`() {
        val data = WavCodec.WavData(FloatArray(48_000) { 0.5f }, 48_000, 1, 16)
        assertEquals(1f, data.durationSeconds, 1e-3f)
        assertEquals(0.5f, data.peak, 1e-6f)
        assertFalse(data.samples.isEmpty())
    }
}
