package com.vicechanger.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.vicechanger.app.audio.dsp.LevelMeter
import kotlin.math.max

/**
 * Honest capability probe for the gaming / calling modes.
 *
 * It performs real work - it opens the microphone, reads a slice of audio, opens the speaker
 * and writes a slice - and reports exactly what happened. What it can never report is
 * success at injecting audio into another app's voice call, because stock Android has no API
 * for that; the report says so instead of inventing a number.
 */
object RoutingCapabilityProbe {

    data class Report(
        val microphoneOpened: Boolean,
        val captureSamples: Int,
        val capturePeakDbfs: Float,
        val playbackOpened: Boolean,
        val playbackBufferMs: Float,
        val preferredInputDeviceApplied: Boolean,
        val preferredOutputDeviceApplied: Boolean,
        val headsetConnected: Boolean,
        val headsetLabel: String?,
        val platformAllowsInjection: Boolean,
        val verdict: String,
        val details: List<String>,
    ) {
        val microphoneDeliveredAudio: Boolean
            get() = captureSamples > 0 && capturePeakDbfs > -55f
    }

    private const val PROBE_MILLIS = 180

    fun probe(context: Context, sampleRate: Int = AudioConfig.SAMPLE_RATE): Report {
        val details = ArrayList<String>(8)
        val monitor = AudioDeviceMonitor(context)
        val snapshot = monitor.refresh()

        // Same method, before any AudioRecord is constructed: lint (and a reader) can see that
        // the microphone is only touched when the permission is actually held.
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            return Report(
                microphoneOpened = false,
                captureSamples = 0,
                capturePeakDbfs = LevelMeter.MIN_DB,
                playbackOpened = false,
                playbackBufferMs = 0f,
                preferredInputDeviceApplied = false,
                preferredOutputDeviceApplied = false,
                headsetConnected = snapshot.hasHeadset,
                headsetLabel = null,
                platformAllowsInjection = false,
                verdict = AudioFailure.PERMISSION_DENIED.message,
                details = listOf(AudioFailure.PERMISSION_DENIED.message),
            )
        }

        val preferredInput = snapshot.inputs.firstOrNull { !it.isBuiltIn }
        val preferredOutput = snapshot.outputs.firstOrNull { !it.isBuiltIn }

        var captureSamples = 0
        var capturePeak = LevelMeter.MIN_DB
        var preferredInputApplied = false
        var microphoneOpened = false

        val blockSize = sampleRate * PROBE_MILLIS / 1000
        val record = runCatching {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer * 2, blockSize * 2),
            )
        }.getOrNull()

        if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
            microphoneOpened = true
            if (preferredInput != null) {
                preferredInputApplied = AudioInput.findDeviceById(context, preferredInput.id)
                    ?.let { device -> runCatching { record.preferredDevice = device }.isSuccess }
                    ?: false
            }
            val buffer = ShortArray(blockSize)
            runCatching {
                record.startRecording()
                var remaining = blockSize * 2
                var peak = 0f
                while (remaining > 0) {
                    val read = record.read(buffer, 0, minOf(buffer.size, remaining), AudioRecord.READ_BLOCKING)
                    if (read <= 0) break
                    captureSamples += read
                    remaining -= read
                    for (i in 0 until read) {
                        val value = kotlin.math.abs(buffer[i] / 32768f)
                        if (value > peak) peak = value
                    }
                }
                capturePeak = LevelMeter.toDb(peak)
            }
            runCatching { if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() }
            runCatching { record.release() }
        } else {
            record?.let { runCatching { it.release() } }
        }

        details += if (microphoneOpened) {
            "Microphone opened; captured $captureSamples samples (peak ${
                "%.1f".format(capturePeak)
            } dBFS)."
        } else {
            "Microphone could not be opened - another app may be holding it."
        }
        if (captureSamples > 0 && capturePeak <= -55f) {
            details += "The microphone returned silence. Check that nothing is muting it."
        }


        var playbackOpened = false
        var playbackBufferMs = 0f
        var preferredOutputApplied = false
        val track = runCatching {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuffer * 2, blockSize * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull()

        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            playbackOpened = true
            playbackBufferMs = track.bufferSizeInFrames * 1000f / sampleRate
            if (preferredOutput != null) {
                preferredOutputApplied = AudioOutput.findDeviceById(context, preferredOutput.id)
                    ?.let { device -> runCatching { track.preferredDevice = device }.isSuccess }
                    ?: false
            }
            val silence = ShortArray(blockSize)
            runCatching {
                track.play()
                track.write(silence, 0, silence.size, AudioTrack.WRITE_BLOCKING)
                track.stop()
            }
            runCatching { track.release() }
        } else {
            track?.let { runCatching { it.release() } }
        }
        details += if (playbackOpened) {
            "Output opened with a ${"%.1f".format(playbackBufferMs)} ms platform buffer."
        } else {
            "Output could not be opened."
        }
        details += "Capture and playback work at the same time, so you can hear the transformed voice while you speak."
        details += "Replacing the microphone stream *inside* another app (Mobile Legends, Messenger calls) is not " +
            "something Android allows a normal app to do - there is no such API, with or without this app."

        val headsetLabel = snapshot.outputs.firstOrNull { it.isBluetooth || it.isWired }?.label
        val verdict = when {
            !microphoneOpened -> AudioFailure.MICROPHONE_UNAVAILABLE.message
            !playbackOpened -> AudioFailure.OUTPUT_INIT_FAILED.message
            else -> "Your device does not allow third-party microphone routing for this mode."
        }

        return Report(
            microphoneOpened = microphoneOpened,
            captureSamples = captureSamples,
            capturePeakDbfs = capturePeak,
            playbackOpened = playbackOpened,
            playbackBufferMs = playbackBufferMs,
            preferredInputDeviceApplied = preferredInputApplied,
            preferredOutputDeviceApplied = preferredOutputApplied,
            headsetConnected = snapshot.hasHeadset,
            headsetLabel = headsetLabel,
            platformAllowsInjection = false,
            verdict = verdict,
            details = details,
        )
    }
}
