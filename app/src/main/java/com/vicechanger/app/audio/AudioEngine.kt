package com.vicechanger.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Process
import kotlin.math.abs
import kotlin.math.pow

/**
 * The live engine: one dedicated thread owns the microphone, the DSP chain and the output.
 * Nothing here runs on the main thread, and nothing here allocates per block.
 *
 * Design rules that the rest of the app relies on:
 *  - [start] / [stop] are safe to call from the main thread at any time.
 *  - A failure never crashes: it stops the loop and is published in [lastFailure] with the
 *    exact UI message from [AudioFailure].
 *  - Latency is measured from the real buffer occupancies of the three stages plus the
 *    platform's own mixer buffer, not assumed.
 */
class AudioEngine(
    private val context: Context,
    private val sampleRate: Int = AudioConfig.SAMPLE_RATE,
) {

    data class EngineConfig(
        val blockSize: Int = AudioConfig.BLOCK_SIZE,
        val lowLatencyMode: Boolean = true,
        val outputBufferBlocks: Int = 4,
        val params: ProcessorParams = ProcessorParams(),
        val inputGainDb: Float = 0f,
        val outputVolume: Float = 1f,
        val preferredInputDeviceId: Int = -1,
        val preferredOutputDeviceId: Int = -1,
    )

    data class Snapshot(
        val running: Boolean,
        val muted: Boolean,
        val inputLevel01: Float,
        val outputLevel01: Float,
        val outputPeakDb: Float,
        val latencyMs: Float,
        val blocksProcessed: Long,
        val underruns: Long,
        val inputDeviceLabel: String,
        val outputDeviceLabel: String,
    )

    private val processor = AudioProcessor(sampleRate)
    private var input: AudioInput? = null
    private var output: AudioOutput? = null
    private var worker: Thread? = null

    @Volatile private var running = false
    @Volatile private var muted = false
    @Volatile private var inputGainLinear = 1f
    @Volatile private var masterVolume = 1f
    @Volatile private var pendingParams: ProcessorParams? = null
    @Volatile private var pendingInputDeviceId = -1
    @Volatile private var pendingOutputDeviceId = -1
    @Volatile private var appliedInputDeviceId = -2
    @Volatile private var appliedOutputDeviceId = -2
    @Volatile private var blocks = 0L
    @Volatile private var latencyMs = 0f
    @Volatile private var inputLevel = 0f
    @Volatile private var outputLevel = 0f
    @Volatile private var outputPeak = -60f
    @Volatile private var deviceSummaryIn = "Default mic"
    @Volatile private var deviceSummaryOut = "Default output"
    @Volatile private var blockSize = AudioConfig.BLOCK_SIZE

    @Volatile var lastFailure: AudioFailure? = null
        private set

    val isRunning: Boolean get() = running
    val isMuted: Boolean get() = muted

    fun start(config: EngineConfig): Result<Unit> {
        if (running) return Result.success(Unit)
        lastFailure = null
        blockSize = if (config.lowLatencyMode) config.blockSize else AudioConfig.BLOCK_SIZE
        inputGainLinear = dbToLinear(config.inputGainDb)
        masterVolume = config.outputVolume.coerceIn(0f, 1f)
        muted = false

        val capture = AudioInput(sampleRate, blockSize)
        val playback = AudioOutput(sampleRate, blockSize)
        val inDevice = if (config.preferredInputDeviceId >= 0) {
            AudioInput.findDeviceById(context, config.preferredInputDeviceId)
        } else {
            null
        }
        val outDevice = if (config.preferredOutputDeviceId >= 0) {
            AudioOutput.findDeviceById(context, config.preferredOutputDeviceId)
        } else {
            null
        }

        capture.open(inDevice, config.lowLatencyMode).onFailure { return finishStartFailure(it) }
        playback.open(config.outputBufferBlocks, config.lowLatencyMode, outDevice)
            .onFailure {
                capture.close()
                return finishStartFailure(it)
            }
        capture.start().onFailure {
            capture.close()
            playback.close()
            return finishStartFailure(it)
        }
        playback.play().onFailure {
            capture.stop(); capture.close(); playback.close()
            return finishStartFailure(it)
        }

        input = capture
        output = playback
        appliedInputDeviceId = config.preferredInputDeviceId
        appliedOutputDeviceId = config.preferredOutputDeviceId
        deviceSummaryIn = inDevice?.let { AudioInput.describe(it) } ?: "Default mic"
        deviceSummaryOut = outDevice?.let { AudioOutput.describe(it) } ?: "Default output"

        processor.configure(config.params)
        pendingParams = null

        running = true
        val thread = Thread({ runLoop() }, "ViceChanger-Audio")
        worker = thread
        thread.start()
        return Result.success(Unit)
    }

    private fun finishStartFailure(error: Throwable): Result<Unit> {
        val failure = (error as? AudioException)?.failure ?: AudioFailure.AUDIO_INIT_FAILED
        lastFailure = failure
        return Result.failure(error)
    }

    private fun runLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val capture = input ?: return
        val playback = output ?: return
        val shortsIn = ShortArray(blockSize)
        val shortsOut = ShortArray(blockSize)
        val floatsIn = FloatArray(blockSize)
        val floatsOut = FloatArray(blockSize)
        var fadeGain = 1f
        var pendingFadeBlock = false

        while (running) {
            val read = capture.read(shortsIn, 0, blockSize)
            if (read < 0) {
                lastFailure = AudioFailure.MICROPHONE_UNAVAILABLE
                break
            }
            if (read == 0) continue

            applyPendingDevices(capture, playback)

            for (i in 0 until read) floatsIn[i] = shortsIn[i] * (1f / 32768f) * inputGainLinear

            // Reconfigure only on a block boundary, with a one-block fade so switching
            // presets mid-sentence does not click.
            val incoming = pendingParams
            val reconfiguring = incoming != null
            if (reconfiguring) {
                fadeGain = 0f
            }

            if (!reconfiguring && pendingFadeBlock) {
                processor.configure(pendingParamsTaken!!)
                pendingFadeBlock = false
                fadeGain = 0f
            }

            processor.process(floatsIn, read, floatsOut)

            if (reconfiguring) {
                pendingParamsTaken = incoming
                pendingParams = null
                pendingFadeBlock = true
            }

            val ceiling = AudioConfig.OUTPUT_CEILING
            for (i in 0 until read) {
                if (fadeGain < 1f) fadeGain = (fadeGain + FADE_STEP).coerceAtMost(1f)
                var sample = if (muted) 0f else floatsOut[i] * masterVolume * fadeGain
                if (sample > ceiling) sample = ceiling
                if (sample < -ceiling) sample = -ceiling
                shortsOut[i] = (sample * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }

            if (!playback.write(shortsOut, read)) {
                lastFailure = AudioFailure.OUTPUT_INIT_FAILED
                break
            }

            publishMeters(capture, playback)
        }

        // Loop exited: tear the platform resources down on this thread.
        capture.stop()
        capture.close()
        playback.stop()
        playback.close()
        input = null
        output = null
        running = false
        worker = null
    }

    private var pendingParamsTaken: ProcessorParams? = null

    private fun applyPendingDevices(capture: AudioInput, playback: AudioOutput) {
        if (pendingInputDeviceId != appliedInputDeviceId) {
            val device = if (pendingInputDeviceId >= 0) {
                AudioInput.findDeviceById(context, pendingInputDeviceId)
            } else {
                null
            }
            capture.setPreferredDevice(device)
            appliedInputDeviceId = pendingInputDeviceId
            deviceSummaryIn = device?.let { AudioInput.describe(it) } ?: "Default mic"
        }
        if (pendingOutputDeviceId != appliedOutputDeviceId) {
            val device = if (pendingOutputDeviceId >= 0) {
                AudioOutput.findDeviceById(context, pendingOutputDeviceId)
            } else {
                null
            }
            playback.setPreferredDevice(device)
            appliedOutputDeviceId = pendingOutputDeviceId
            deviceSummaryOut = device?.let { AudioOutput.describe(it) } ?: "Default output"
        }
    }

    private var meterTick = 0

    private fun publishMeters(capture: AudioInput, playback: AudioOutput) {
        blocks++
        meterTick++
        if (meterTick % 4 != 0) return
        inputLevel = processor.inputLevelDb.let { db -> ((db - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f) }
        outputLevel = processor.outputLevelDb.let { db -> ((db - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f) }
        outputPeak = processor.outputPeakDb
        val buffered = (capture.bufferedFrames() + processor.bufferedSamples + playback.bufferedFrames())
        latencyMs = buffered * 1000f / sampleRate
    }

    fun stop() {
        running = false
        val thread = worker
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(1500) }
        }
        // If the worker refused to finish, force the platform objects closed so a stuck
        // AudioRecord can never keep the microphone open.
        runCatching { input?.stop() }
        runCatching { input?.close() }
        runCatching { output?.close() }
        input = null
        output = null
        worker = null
        inputLevel = 0f
        outputLevel = 0f
    }

    fun setParams(params: ProcessorParams) {
        pendingParams = params
    }

    fun setMuted(value: Boolean) {
        muted = value
    }

    fun setInputGainDb(db: Float) {
        inputGainLinear = dbToLinear(db)
    }

    fun setOutputVolume(value: Float) {
        masterVolume = value.coerceIn(0f, 1f)
        output?.setVolume(masterVolume)
    }

    fun setPreferredDevices(inputDeviceId: Int, outputDeviceId: Int) {
        pendingInputDeviceId = inputDeviceId
        pendingOutputDeviceId = outputDeviceId
    }

    fun snapshot(): Snapshot = Snapshot(
        running = running,
        muted = muted,
        inputLevel01 = inputLevel,
        outputLevel01 = outputLevel,
        outputPeakDb = outputPeak,
        latencyMs = latencyMs,
        blocksProcessed = blocks,
        underruns = processor.underruns,
        inputDeviceLabel = deviceSummaryIn,
        outputDeviceLabel = deviceSummaryOut,
    )

    /** Frames the engine is currently holding back, straight from the stage buffers. */
    fun bufferedSamples(): Int = processor.bufferedSamples

    companion object {
        private const val METER_FLOOR_DB = 60f
        private const val FADE_STEP = 0.02f

        fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

        fun linearToDb(linear: Float): Float =
            if (linear <= 1e-6f) -60f else 20f * (kotlin.math.ln(linear.toDouble()) / kotlin.math.ln(10.0)).toFloat()

        /** Devices that exist and could be chosen from the settings screen. */
        fun describeDevices(context: Context): Pair<List<AudioDeviceInfo>, List<AudioDeviceInfo>> =
            AudioInput.availableDevices(context) to AudioOutput.availableDevices(context)

        fun levelDifference(a: Float, b: Float): Boolean = abs(a - b) > 0.005f
    }
}
