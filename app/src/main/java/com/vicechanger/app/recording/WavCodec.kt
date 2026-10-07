package com.vicechanger.app.recording

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Minimal, dependency-free WAV support: a streaming writer that patches its header on close,
 * a reader that tolerates the chunk layouts other apps produce, and an in-memory encoder used
 * by the unit tests to prove the header and the sample round-trip are correct.
 */
object WavCodec {

    const val HEADER_SIZE = 44
    const val BITS_PER_SAMPLE = 16

    data class WavData(
        val samples: FloatArray,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
    ) {
        val durationSeconds: Float get() = samples.size.toFloat() / (sampleRate * max(channels, 1))
        val peak: Float get() = samples.maxOfOrNull { abs(it) } ?: 0f
    }

    fun encode(samples: FloatArray, sampleRate: Int, channels: Int = 1): ByteArray {
        val dataBytes = samples.size * 2
        val buffer = ByteBuffer.allocate(HEADER_SIZE + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(buffer, sampleRate, channels, dataBytes)
        for (sample in samples) buffer.putShort(toPcm16(sample))
        return buffer.array()
    }

    fun write(file: File, samples: FloatArray, sampleRate: Int, channels: Int = 1) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            writeHeader(header, sampleRate, channels, samples.size * 2)
            raf.write(header.array())
            val chunk = ByteArray(4096)
            val chunkBuffer = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            var index = 0
            while (index < samples.size) {
                chunkBuffer.clear()
                var count = 0
                while (count < chunk.size / 2 && index < samples.size) {
                    chunkBuffer.putShort(toPcm16(samples[index]))
                    index++
                    count++
                }
                raf.write(chunk, 0, count * 2)
            }
        }
    }

    /** @return null when the file is missing, empty or not a readable PCM/float WAV. */
    fun read(file: File): WavData? {
        if (!file.exists() || file.length() <= HEADER_SIZE) return null
        val bytes = file.readBytes()
        return runCatching { parse(bytes) }.getOrNull()
    }

    fun parse(bytes: ByteArray): WavData? {
        if (bytes.size < HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4)
        buffer.get(riff)
        if (String(riff) != "RIFF") return null
        buffer.int // riff chunk size
        val wave = ByteArray(4)
        buffer.get(wave)
        if (String(wave) != "WAVE") return null

        var channels = 1
        var sampleRate = 44_100
        var bits = 16
        var format = 1
        var dataOffset = -1
        var dataLength = 0

        while (buffer.remaining() >= 8) {
            val id = ByteArray(4)
            buffer.get(id)
            val size = buffer.int
            val name = String(id)
            when (name) {
                "fmt " -> {
                    val end = minOf(buffer.position() + size, bytes.size)
                    format = buffer.short.toInt()
                    channels = buffer.short.toInt().coerceAtLeast(1)
                    sampleRate = buffer.int
                    buffer.int // byte rate
                    buffer.short // block align
                    bits = buffer.short.toInt()
                    if (format == 0xFFFE && buffer.remaining() >= 2) {
                        // WAVE_FORMAT_EXTENSIBLE: the real format follows the extension header.
                        buffer.short // cbSize
                        buffer.short // valid bits
                        buffer.int // channel mask
                        format = buffer.short.toInt()
                    }
                    buffer.position(minOf(end, bytes.size))
                }
                "data" -> {
                    dataOffset = buffer.position()
                    dataLength = minOf(size, bytes.size - dataOffset)
                    buffer.position(minOf(dataOffset + size, bytes.size))
                }
                else -> buffer.position(minOf(buffer.position() + size, bytes.size))
            }
        }
        if (dataOffset < 0 || dataLength <= 0) return null

        val floatSamples = when {
            format == 3 && bits == 32 -> FloatArray(dataLength / 4) { i ->
                ByteBuffer.wrap(bytes, dataOffset + i * 4, 4).order(ByteOrder.LITTLE_ENDIAN).float
            }
            bits == 16 -> FloatArray(dataLength / 2) { i ->
                val raw = ((bytes[dataOffset + i * 2].toInt() and 0xFF) or
                    (bytes[dataOffset + i * 2 + 1].toInt() shl 8)).toShort()
                raw / 32768f
            }
            bits == 8 -> FloatArray(dataLength) { i ->
                ((bytes[dataOffset + i].toInt() and 0xFF) - 128) / 128f
            }
            else -> return null
        }
        return WavData(floatSamples, sampleRate, channels, bits)
    }

    private fun writeHeader(buffer: ByteBuffer, sampleRate: Int, channels: Int, dataBytes: Int) {
        val byteRate = sampleRate * channels * BITS_PER_SAMPLE / 8
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * BITS_PER_SAMPLE / 8).toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(dataBytes)
    }

    /**
     * Float to 16-bit PCM with rounding. Full scale is +32767/-32767 rather than -32768: the
     * engine's ceiling is below 1.0 anyway, and staying inside keeps the mapping symmetric.
     */
    fun toPcm16(sample: Float): Short {
        val clamped = sample.coerceIn(-1f, 1f)
        val scaled = Math.round(clamped * 32767f)
        return scaled.coerceIn(-32767, 32767).toShort()
    }

    fun toFloat(sample: Short): Float = sample / 32768f
}

/**
 * Streaming WAV writer used while recording: PCM16 goes out as it arrives and the RIFF sizes
 * are patched once at the end, so a long recording never has to sit in memory.
 */
class StreamingWavWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int = 1,
) {
    private val raf: RandomAccessFile
    private var samplesWritten = 0L
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        raf = RandomAccessFile(file, "rw")
        raf.setLength(0)
        val header = ByteBuffer.allocate(WavCodec.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(0)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * WavCodec.BITS_PER_SAMPLE / 8)
        header.putShort((channels * WavCodec.BITS_PER_SAMPLE / 8).toShort())
        header.putShort(WavCodec.BITS_PER_SAMPLE.toShort())
        header.put("data".toByteArray())
        header.putInt(0)
        raf.write(header.array())
    }

    fun append(samples: ShortArray, count: Int) {
        if (closed || count <= 0) return
        val bytes = ByteArray(count * 2)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buffer.putShort(samples[i])
        raf.write(bytes)
        samplesWritten += count
    }

    val sampleCount: Long get() = samplesWritten

    val durationSeconds: Float get() = samplesWritten.toFloat() / (sampleRate * channels)

    fun close() {
        if (closed) return
        closed = true
        val dataBytes = (samplesWritten * 2).toInt()
        raf.seek(4)
        raf.write(littleEndianInt(36 + dataBytes))
        raf.seek(WavCodec.HEADER_SIZE.toLong() - 4L)
        raf.write(littleEndianInt(dataBytes))
        raf.close()
    }

    private fun littleEndianInt(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
