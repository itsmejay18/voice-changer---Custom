package com.vicechanger.app.recording

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.vicechanger.app.audio.AudioException
import com.vicechanger.app.audio.AudioConfig
import com.vicechanger.app.audio.AudioFailure
import com.vicechanger.app.audio.dsp.LevelMeter
import com.vicechanger.app.audio.dsp.LevelMeter.Companion.toDb
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Records the raw microphone to a WAV file for the voice message path. Runs its own thread,
 * publishes a live level and a level history (the waveform the UI draws) and stops itself at
 * [maxSeconds] so a forgotten recording cannot fill the storage.
 */
class VoiceRecorder(private val sampleRate: Int = AudioConfig.SAMPLE_RATE) {

    data class Recording(
        val file: File,
        val seconds: Float,
        val peakDb: Float,
        val sampleCount: Long,
    )

    @Volatile private var running = false
    @Volatile private var level = 0f
    @Volatile private var peakDb = LevelMeter.MIN_DB
    @Volatile private var seconds = 0f
    private var worker: Thread? = null
    private var writer: StreamingWavWriter? = null
    private var record: AudioRecord? = null
    private var targetFile: File? = null
    private val history = ArrayDeque<Float>()
    private val stopped = AtomicBoolean(true)

    val isRecording: Boolean get() = running
    val level01: Float get() = level
    val durationSeconds: Float get() = seconds
    val peakDbfs: Float get() = peakDb

    /** Newest-last level history, normalised 0..1, for the waveform view. */
    fun levelHistory(): List<Float> = synchronized(history) { history.toList() }

    fun start(file: File, maxSeconds: Float = MAX_SECONDS): Result<Unit> {
        if (running) return Result.success(Unit)
        targetFile = file
        return try {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) return Result.failure(AudioException(AudioFailure.RECORD_INIT_FAILED))
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer * 2, AudioConfig.BLOCK_SIZE * 8),
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return Result.failure(AudioException(AudioFailure.RECORD_INIT_FAILED))
            }
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                recorder.release()
                return Result.failure(AudioException(AudioFailure.RECORD_INIT_FAILED))
            }
            record = recorder
            writer = StreamingWavWriter(file, sampleRate)
            synchronized(history) { history.clear() }
            stopped.set(false)
            val thread = Thread({ loop(maxSeconds) }, "ViceChanger-Recorder")
            worker = thread
            running = true
            thread.start()
            Result.success(Unit)
        } catch (security: SecurityException) {
            Result.failure(AudioException(AudioFailure.PERMISSION_DENIED))
        } catch (illegal: IllegalArgumentException) {
            Result.failure(AudioException(AudioFailure.RECORD_INIT_FAILED))
        }
    }

    private fun loop(maxSeconds: Float) {
        val recorder = record ?: return
        val sink = writer ?: return
        val block = ShortArray(AudioConfig.BLOCK_SIZE)
        val monitorShorts = ShortArray(AudioConfig.BLOCK_SIZE)
        var peakLinear = 0f
        var blocksRead = 0
        try {
            while (running) {
                val read = recorder.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                if (read < 0) break
                if (read == 0) continue
                sink.append(block, read)
                for (i in 0 until read) monitorShorts[i] = block[i]
                var blockPeak = 0f
                for (i in 0 until read) {
                    val value = kotlin.math.abs(WavCodec.toFloat(monitorShorts[i]))
                    if (value > blockPeak) blockPeak = value
                }
                if (blockPeak > peakLinear) peakLinear = blockPeak
                peakDb = toDb(peakLinear)
                blocksRead++
                seconds = sink.durationSeconds
                val normalised = ((toDb(blockPeak) - LevelMeter.MIN_DB) / -LevelMeter.MIN_DB).coerceIn(0f, 1f)
                level = normalised
                synchronized(history) {
                    history.addLast(normalised)
                    while (history.size > HISTORY_SIZE) history.removeFirst()
                }
                if (seconds >= maxSeconds) break
            }
        } finally {
            stopped.set(true)
            running = false
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
            runCatching { recorder.release() }
            record = null
            runCatching { sink.close() }
            writer = null
        }
    }

    /** Stops and waits for the file to be finalised. */
    fun stop(): Result<Recording> {
        val file = targetFile
        running = false
        val thread = worker
        if (thread != null && thread !== Thread.currentThread()) runCatching { thread.join(3000) }
        worker = null
        val count = file?.length() ?: 0L
        if (file == null || !file.exists() || file.length() <= WavCodec.HEADER_SIZE) {
            return Result.failure(AudioException(AudioFailure.RECORDING_EMPTY))
        }
        val seconds = (count - WavCodec.HEADER_SIZE) / (2f * sampleRate)
        return Result.success(
            Recording(
                file = file,
                seconds = seconds,
                peakDb = peakDb,
                sampleCount = ((count - WavCodec.HEADER_SIZE) / 2).coerceAtLeast(0),
            ),
        )
    }

    fun cancel() {
        running = false
        worker?.let { if (it !== Thread.currentThread()) it.join(1500) }
        worker = null
        targetFile?.let { runCatching { it.delete() } }
        targetFile = null
        level = 0f
        seconds = 0f
    }

    companion object {
        const val MAX_SECONDS = 60f
        private const val HISTORY_SIZE = 180
    }
}
