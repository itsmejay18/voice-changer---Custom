package com.vicechanger.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Playback of the processed voice. Low latency mode requests the platform's fast mixer path
 * and a small buffer; the buffer size is reported back so the engine can add it to the
 * measured pipeline latency instead of guessing.
 */
class AudioOutput(private val sampleRate: Int, private val blockSize: Int) {

    private var track: AudioTrack? = null
    private var bufferBytes: Int = 0
    private var volume: Float = 1f

    val initialized: Boolean get() = track?.state == AudioTrack.STATE_INITIALIZED

    fun open(
        blockCount: Int = 4,
        lowLatency: Boolean = true,
        preferredDevice: AudioDeviceInfo? = null,
    ): Result<Unit> {
        close()
        return try {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) return Result.failure(AudioException(AudioFailure.OUTPUT_INIT_FAILED))
            val wanted = maxOf(minBuffer, blockSize * 2 * blockCount)
            bufferBytes = wanted
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(wanted)
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (lowLatency) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            val candidate = builder.build()
            if (candidate.state != AudioTrack.STATE_INITIALIZED) {
                candidate.release()
                return Result.failure(AudioException(AudioFailure.OUTPUT_INIT_FAILED))
            }
            if (preferredDevice != null) runCatching { candidate.preferredDevice = preferredDevice }
            candidate.setVolume(volume)
            track = candidate
            Result.success(Unit)
        } catch (illegal: IllegalArgumentException) {
            Result.failure(AudioException(AudioFailure.OUTPUT_INIT_FAILED))
        } catch (state: IllegalStateException) {
            Result.failure(AudioException(AudioFailure.OUTPUT_INIT_FAILED))
        }
    }

    fun play(): Result<Unit> {
        val t = track ?: return Result.failure(AudioException(AudioFailure.OUTPUT_INIT_FAILED))
        return try {
            t.play()
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Result.failure(AudioException(AudioFailure.OUTPUT_INIT_FAILED))
        }
    }

    /**
     * Blocking write. Returns false when the track died, which the engine treats as a hard
     * output failure rather than silently dropping audio forever.
     */
    fun write(buffer: ShortArray, count: Int): Boolean {
        val t = track ?: return false
        var offset = 0
        while (offset < count) {
            val written = t.write(buffer, offset, count - offset, AudioTrack.WRITE_BLOCKING)
            if (written < 0) return false
            offset += written
        }
        return true
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        runCatching { track?.setVolume(volume) }
    }

    /** Frames the platform still holds in its mixer buffer - real, measured latency. */
    fun bufferedFrames(): Int = track?.let { runCatching { it.bufferSizeInFrames }.getOrDefault(0) } ?: 0

    /** Apply a device change while the engine is running (called on the audio thread). */
    fun setPreferredDevice(device: AudioDeviceInfo?) {
        val t = track ?: return
        runCatching { t.preferredDevice = device }
    }

    fun stop() {
        runCatching {
            if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) track?.pause()
            track?.flush()
        }
    }

    fun close() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    companion object {
        fun availableDevices(context: Context): List<AudioDeviceInfo> {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
            return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }

        fun findDeviceById(context: Context, id: Int): AudioDeviceInfo? =
            availableDevices(context).firstOrNull { it.id == id }

        fun describe(device: AudioDeviceInfo): String = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth audio"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            else -> "Audio output"
        }
    }
}
