package com.vicechanger.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import android.util.Log
import kotlin.math.abs

/**
 * Microphone capture. Owns exactly one [AudioRecord] and is the only place that knows how to
 * open one, so a failure can be classified instead of crashed on.
 *
 * The capture source is tried from "least platform interference" first:
 *  - `MIC` is the raw signal, which is what a voice changer wants (the app does its own
 *    filtering, gating and compression);
 *  - `VOICE_RECOGNITION` is the usual fallback;
 *  - `VOICE_COMMUNICATION` is last: it brings the platform's AGC and echo canceller, and on
 *    several devices (Huawei included) it puts the audio path into a call-like context where
 *    the app's own playback can be routed to the earpiece - which makes live monitoring sound
 *    like the user's own voice coming back at them.
 */
class AudioInput(private val sampleRate: Int, private val blockSize: Int) {

    private var record: AudioRecord? = null
    private var framesRead: Long = 0L

    /** Which capture source actually opened; reported in the UI and in logcat. */
    var sourceLabel: String = "not opened"
        private set

    val initialized: Boolean get() = record?.state == AudioRecord.STATE_INITIALIZED

    /** @throws SecurityException when RECORD_AUDIO is missing. */
    fun open(preferredDevice: AudioDeviceInfo? = null, lowLatency: Boolean = true): Result<Unit> {
        close()
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            return Result.failure(AudioException(AudioFailure.MICROPHONE_UNAVAILABLE))
        }
        val bufferBytes = maxOf(minBuffer * 2, blockSize * 2 * 4)

        var lastError: Throwable? = null
        for ((source, label) in SOURCES) {
            try {
                val candidate = AudioRecord(
                    source,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                )
                if (candidate.state != AudioRecord.STATE_INITIALIZED) {
                    runCatching { candidate.release() }
                    lastError = AudioException(AudioFailure.MICROPHONE_UNAVAILABLE)
                    Log.w(TAG, "microphone source $label did not initialize")
                    continue
                }
                if (preferredDevice != null) {
                    runCatching { candidate.preferredDevice = preferredDevice }
                }
                record = candidate
                sourceLabel = label
                Log.i(TAG, "microphone opened: source=$label buffer=$bufferBytes bytes")
                return Result.success(Unit)
            } catch (security: SecurityException) {
                return Result.failure(AudioException(AudioFailure.PERMISSION_DENIED))
            } catch (illegal: IllegalArgumentException) {
                lastError = illegal
                Log.w(TAG, "microphone source $label rejected: ${illegal.message}")
            }
        }
        return Result.failure(lastError ?: AudioException(AudioFailure.MICROPHONE_UNAVAILABLE))
    }

    fun start(): Result<Unit> {
        val r = record ?: return Result.failure(AudioException(AudioFailure.MICROPHONE_UNAVAILABLE))
        return try {
            r.startRecording()
            if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Result.failure(AudioException(AudioFailure.MICROPHONE_UNAVAILABLE))
            } else {
                Result.success(Unit)
            }
        } catch (e: IllegalStateException) {
            Result.failure(AudioException(AudioFailure.MICROPHONE_UNAVAILABLE))
        }
    }

    /** @return samples read, or -1 on a hard error. */
    fun read(buffer: ShortArray, offset: Int, count: Int): Int {
        val r = record ?: return -1
        val read = r.read(buffer, offset, count, AudioRecord.READ_BLOCKING)
        if (read > 0) framesRead += read
        return if (read < 0) -1 else read
    }

    /** Apply a device change while the engine is running (called on the audio thread). */
    fun setPreferredDevice(device: AudioDeviceInfo?) {
        val r = record ?: return
        runCatching { r.preferredDevice = device }
    }

    /** Frames the platform's capture buffer currently holds - part of the latency report. */
    fun bufferedFrames(): Int = record?.let { runCatching { it.bufferSizeInFrames }.getOrDefault(0) } ?: 0

    fun stop() {
        runCatching {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) record?.stop()
        }
    }

    fun close() {
        runCatching { record?.release() }
        record = null
        framesRead = 0L
    }

    /** Capture progress, counted from the samples this instance actually pulled. */
    fun framesCaptured(): Long = framesRead

    companion object {
        const val TAG = "ViceChangerAudio"

        /** Capture sources, in the order they are tried. */
        private val SOURCES = listOf(
            MediaRecorder.AudioSource.MIC to "MIC (raw)",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
        )

        /** Input devices the platform is willing to offer this app. */
        fun availableDevices(context: Context): List<AudioDeviceInfo> {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
            return manager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        }

        fun findDeviceById(context: Context, id: Int): AudioDeviceInfo? =
            availableDevices(context).firstOrNull { it.id == id }

        fun describe(device: AudioDeviceInfo): String {
            val type = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth audio"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
                AudioDeviceInfo.TYPE_TELEPHONY -> "Phone"
                else -> "Audio input"
            }
            return "$type (${device.productName})"
        }
    }
}

/** Wraps a classified audio failure so callers can show [AudioFailure.message] verbatim. */
class AudioException(val failure: AudioFailure) : Exception(failure.message)

/** True when two levels differ enough to be worth re-rendering the meter. */
fun levelChanged(previous: Float, current: Float): Boolean = abs(previous - current) > 0.005f
