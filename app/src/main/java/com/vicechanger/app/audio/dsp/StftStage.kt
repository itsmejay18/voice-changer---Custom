package com.vicechanger.app.audio.dsp

import kotlin.math.cos

/**
 * Shared streaming STFT machinery for the two spectral stages (pitch shifter and
 * formant/noise stage).
 *
 * Overlap-add is normalised by a precomputed window-product table instead of a magic
 * constant, which is what keeps the output level flat even when the synthesis hop differs
 * from the analysis hop (the pitch shifter stretches time, so it does).
 *
 * Subclasses only implement [processFrame]: they receive the complex spectrum of one
 * windowed frame in place and modify it.
 */
abstract class StftStage(
    protected val sampleRate: Int,
    protected val frameSize: Int,
    protected val analysisHop: Int,
    protected val synthesisHop: Int = analysisHop,
    maxInputQueueCapacity: Int = 48000,
) {

    init {
        require(frameSize > 0 && (frameSize and (frameSize - 1)) == 0) { "frame size must be a power of two" }
        require(analysisHop > 0 && analysisHop <= frameSize) { "hop must be in (0, frameSize]" }
        require(synthesisHop > 0) { "synthesis hop must be positive" }
    }

    protected val fft = Fft(frameSize)

    /** Periodic Hann: satisfies the overlap-add (COLA) condition at hop = frameSize/4. */
    protected val window = FloatArray(frameSize) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / frameSize).toFloat() }

    private val spectrumRe = FloatArray(frameSize)
    private val spectrumIm = FloatArray(frameSize)

    private var inputQueue = FloatArray(maxOf(frameSize * 4, 4096))
    private var inputLength = 0
    private var inputReadPos = 0

    private var accumulator = FloatArray(frameSize * 4)
    /** Absolute output index of accumulator[0]. */
    private var accumulatorBase = 0L
    /** Absolute output index one past the newest accumulated sample. */
    private var accumulatorFill = 0L
    /** Absolute index of the next sample handed to the consumer. */
    private var outputIndex = 0L

    private var frameCounter = 0L
    /** Absolute index up to which every contributing frame has been accumulated (exclusive). */
    private var completedUntil = 0L
    private var denTable = FloatArray(synthesisHop)

    /** Samples still buffered inside this stage (used to report real pipeline latency). */
    val bufferedSamples: Int
        get() = (accumulatorFill - outputIndex).toInt().coerceAtLeast(0) + (inputLength - inputReadPos)

    /** Number of frames processed since the last [reset] - useful for tests and diagnostics. */
    val framesProcessed: Long get() = frameCounter

    /** Analysis window applied per frame; pulled forward in time. */
    protected var analysisMilliseconds: Float = 0f
        private set

    init {
        buildDenTable()
    }

    /** Modify the complex spectrum of one frame in place. */
    protected abstract fun processFrame(re: FloatArray, im: FloatArray, frameIndex: Long)

    /** Optional hook called with the raw windowed frame before the FFT. */
    protected open fun onAnalysisWindow(windowedFrame: FloatArray, length: Int) {}

    /**
     * Push [count] input samples and pull up to [out.size] processed samples.
     * @return number of samples written to [out]
     */
    fun process(input: FloatArray, count: Int, out: FloatArray): Int {
        appendInput(input, count)
        var frames = 0
        while (inputLength - inputReadPos >= frameSize) {
            runFrame()
            frames++
            // Bound the work per call so a huge push cannot stall the audio thread.
            if (frames > 64) break
        }
        compactInput()
        return drain(out)
    }

    private fun appendInput(input: FloatArray, count: Int) {
        if (count <= 0) return
        if (inputLength + count > inputQueue.size) {
            var size = inputQueue.size
            while (size < inputLength + count) size *= 2
            inputQueue = inputQueue.copyOf(size)
        }
        System.arraycopy(input, 0, inputQueue, inputLength, count)
        inputLength += count
    }

    private fun compactInput() {
        if (inputReadPos == 0) return
        val remaining = inputLength - inputReadPos
        if (remaining > 0) System.arraycopy(inputQueue, inputReadPos, inputQueue, 0, remaining)
        inputLength = remaining
        inputReadPos = 0
    }

    private fun runFrame() {
        for (n in 0 until frameSize) {
            spectrumRe[n] = window[n] * inputQueue[inputReadPos + n]
            spectrumIm[n] = 0f
        }
        onAnalysisWindow(spectrumRe, frameSize)
        fft.forward(spectrumRe, spectrumIm)
        processFrame(spectrumRe, spectrumIm, frameCounter)
        ensureSpectrumSymmetry(spectrumRe, spectrumIm)
        fft.inverse(spectrumRe, spectrumIm)

        val writeStart = frameCounter * synthesisHop
        ensureAccumulator(writeStart + frameSize)
        for (n in 0 until frameSize) {
            val absolute = writeStart + n
            val index = (absolute - accumulatorBase).toInt()
            if (index < 0) continue
            accumulator[index] += window[n] * spectrumRe[n]
        }
        if (writeStart + frameSize > accumulatorFill) accumulatorFill = writeStart + frameSize

        inputReadPos += analysisHop
        frameCounter++
        // A sample is finished once every frame overlapping it has been added: the last frame
        // touching sample p is floor(p / synthesisHop), so samples below this index are final.
        // Emitting earlier would drop 3 of the 4 overlapping contributions and smear the output.
        completedUntil = frameCounter * synthesisHop
        analysisMilliseconds = (frameCounter * analysisHop * 1000f) / sampleRate
    }

    /** Force Hermitian symmetry so the inverse transform is real. */
    private fun ensureSpectrumSymmetry(re: FloatArray, im: FloatArray) {
        im[0] = 0f
        im[frameSize / 2] = 0f
        for (k in frameSize / 2 + 1 until frameSize) {
            re[k] = re[frameSize - k]
            im[k] = -im[frameSize - k]
        }
    }

    private fun buildDenTable() {
        val w2 = FloatArray(frameSize) { window[it] * window[it] }
        for (i in 0 until synthesisHop) {
            var sum = 0f
            var frame = 0
            while (frame * synthesisHop < frameSize + i) {
                val idx = i + frame * synthesisHop
                if (idx < frameSize) sum += w2[idx]
                frame++
                if (frame > frameSize / synthesisHop + 2) break
            }
            denTable[i] = sum
        }
    }

    private fun ensureAccumulator(upToAbsolute: Long) {
        val needed = (upToAbsolute - accumulatorBase).toInt()
        if (needed <= accumulator.size) return
        var size = accumulator.size
        while (size < needed) size *= 2
        accumulator = accumulator.copyOf(size)
    }

    private fun drain(out: FloatArray): Int {
        var written = 0
        while (written < out.size && outputIndex < completedUntil && outputIndex < accumulatorFill) {
            val index = (outputIndex - accumulatorBase).toInt()
            if (index >= accumulator.size) break
            val den = denTable[(outputIndex % synthesisHop).toInt()]
            val sample = accumulator[index]
            out[written++] = if (den > 1e-6f) sample / den else 0f
            outputIndex++
        }
        compactAccumulator()
        return written
    }

    private fun compactAccumulator() {
        val drop = (outputIndex - accumulatorBase).toInt()
        if (drop <= 0) return
        val written = (accumulatorFill - accumulatorBase).toInt()
        val remaining = written - drop
        if (remaining > 0) System.arraycopy(accumulator, drop, accumulator, 0, remaining)
        // Everything above the shifted prefix now holds partial sums that belonged to a
        // different absolute range. All of it must be cleared, not just the first few samples:
        // the next frames add onto those positions, and leftovers there corrupt the output.
        val clearFrom = maxOf(remaining, 0)
        val clearTo = minOf(accumulator.size, maxOf(written, clearFrom))
        if (clearTo > clearFrom) java.util.Arrays.fill(accumulator, clearFrom, clearTo, 0f)
        accumulatorBase += drop
        accumulatorFill = accumulatorBase + clearFrom
    }

    /**
     * Number of output samples this stage delays the signal by at steady state: the first
     * full analysis window plus whatever is queued. Reported, never assumed.
     */
    fun pipelineDelaySamples(): Int = (bufferedSamples + frameSize - analysisHop)

    open fun reset() {
        inputLength = 0
        inputReadPos = 0
        accumulator.fill(0f)
        accumulatorBase = 0L
        accumulatorFill = 0L
        outputIndex = 0L
        frameCounter = 0L
        completedUntil = 0L
        analysisMilliseconds = 0f
    }
}
