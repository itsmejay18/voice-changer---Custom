package com.vicechanger.app.viewmodel

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vicechanger.app.audio.AudioFailure
import com.vicechanger.app.recording.AudioFileStore
import com.vicechanger.app.recording.VoiceRecorder
import com.vicechanger.app.recording.WavCodec
import com.vicechanger.app.settings.AppSettings
import com.vicechanger.app.voice.OfflineRenderer
import com.vicechanger.app.voice.VoicePreset
import com.vicechanger.app.voice.VoiceTransformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * The recorded-voice path: record -> pick a voice -> process -> preview -> save -> share.
 *
 * Processing runs the same DSP chain as Live mode through [OfflineRenderer] on a background
 * dispatcher, so nothing here can block the UI, and no work happens on the main thread.
 */
class MessageViewModel(application: Application) : AndroidViewModel(application) {

    enum class Stage { IDLE, RECORDING, RECORDED, PROCESSING, PROCESSED }

    data class UiState(
        val stage: Stage = Stage.IDLE,
        val seconds: Float = 0f,
        val level: Float = 0f,
        val waveform: List<Float> = emptyList(),
        val peakDb: Float = -60f,
        val failure: AudioFailure? = null,
        val processedFile: File? = null,
        val processedName: String = "",
        val processedSeconds: Float = 0f,
        val processedPeakDb: Float = -60f,
        val measuredLatencyMs: Float = 0f,
        val processingVoiceName: String = "",
        val savedUri: String? = null,
        val savedToLibrary: Boolean = false,
        val playing: Boolean = false,
        val playbackFinished: Boolean = false,
        val rawFileSizeBytes: Long = 0L,
    ) {
        val hasRecording: Boolean get() = stage == Stage.RECORDED || stage == Stage.PROCESSED
        val hasProcessed: Boolean get() = processedFile != null
        val busy: Boolean get() = stage == Stage.PROCESSING
    }

    private val recorder = VoiceRecorder()
    private val fileStore = AudioFileStore(application)
    private var player: MediaPlayer? = null
    private var rawFile: File? = null
    private var levelJob: Job? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun startRecording() {
        if (_state.value.stage == Stage.RECORDING) return
        stopPlayback()
        val target = fileStore.newRawFile()
        val result = recorder.start(target)
        result.onSuccess {
            rawFile = target
            _state.value = UiState(
                stage = Stage.RECORDING,
                rawFileSizeBytes = 0L,
            )
            startLevelPolling()
        }
        result.onFailure { error ->
            _state.update {
                it.copy(
                    stage = Stage.IDLE,
                    failure = (error as? com.vicechanger.app.audio.AudioException)?.failure
                        ?: AudioFailure.RECORD_INIT_FAILED,
                )
            }
        }
    }

    fun stopRecording() {
        if (_state.value.stage != Stage.RECORDING) return
        levelJob?.cancel()
        levelJob = null
        val result = recorder.stop()
        result.onSuccess { recording ->
            _state.update {
                it.copy(
                    stage = Stage.RECORDED,
                    seconds = recording.seconds,
                    peakDb = recording.peakDb,
                    waveform = recorder.levelHistory(),
                    rawFileSizeBytes = recording.file.length(),
                    failure = null,
                )
            }
        }
        result.onFailure { error ->
            _state.update {
                it.copy(
                    stage = Stage.IDLE,
                    failure = (error as? com.vicechanger.app.audio.AudioException)?.failure
                        ?: AudioFailure.RECORDING_EMPTY,
                )
            }
        }
    }

    fun cancelRecording() {
        levelJob?.cancel()
        levelJob = null
        recorder.cancel()
        rawFile = null
        _state.value = UiState()
    }

    fun clearAll() {
        stopPlayback()
        recorder.cancel()
        rawFile = null
        _state.value = UiState()
    }

    /** Records a short sample and processes it immediately - used by "Test Voice". */
    fun startTestRecording() = startRecording()

    fun process(preset: VoicePreset, settings: AppSettings) {
        val source = rawFile
        if (source == null || !source.exists()) {
            _state.update { it.copy(failure = AudioFailure.FILE_UNREADABLE) }
            return
        }
        _state.update {
            it.copy(
                stage = Stage.PROCESSING,
                failure = null,
                savedUri = null,
                savedToLibrary = false,
                processingVoiceName = preset.name,
            )
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    val data = WavCodec.read(source) ?: error("unreadable")
                    val params = VoiceTransformer.transform(preset, settings)
                    val rendered = OfflineRenderer.render(
                        input = data.samples,
                        sampleRate = data.sampleRate,
                        params = params,
                        inputGainDb = settings.inputGainDb,
                    )
                    val date = LocalDate.now()
                    val name = AudioFileStore.buildFileName(date, fileStore.nextIndexFor(date))
                    val file = fileStore.newProcessedFile(name)
                    WavCodec.write(file, rendered.samples, rendered.sampleRate)
                    Processed(file, name, rendered)
                }
            }
            outcome.fold(
                onSuccess = { processed ->
                    _state.update {
                        it.copy(
                            stage = Stage.PROCESSED,
                            processedFile = processed.file,
                            processedName = processed.name,
                            processedSeconds = processed.rendered.seconds,
                            processedPeakDb = processed.rendered.peakDb,
                            measuredLatencyMs = processed.rendered.measuredLatencySamples * 1000f /
                                processed.rendered.sampleRate,
                            failure = null,
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(stage = Stage.RECORDED, failure = AudioFailure.PROCESSING_FAILED)
                    }
                },
            )
        }
    }

    /** Send the processed file to the device's music library. */
    fun saveToLibrary() {
        val file = _state.value.processedFile ?: return
        val name = _state.value.processedName
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                fileStore.saveToMediaStore(file, name)
            }
            if (uri != null) {
                _state.update { it.copy(savedToLibrary = true, savedUri = uri.toString()) }
            } else {
                _state.update { it.copy(failure = AudioFailure.SAVE_FAILED) }
            }
        }
    }

    /** File handed to the share sheet, copied into the FileProvider share directory. */
    fun shareableFile(): File? {
        val file = _state.value.processedFile ?: return null
        return runCatching {
            fileStore.shareableCopy(file, _state.value.processedName)
        }.getOrElse { file }
    }

    fun play() {
        val file = _state.value.processedFile ?: return
        stopPlayback()
        val mediaPlayer = MediaPlayer()
        playResult(file, mediaPlayer)
    }

    private fun playResult(file: File, mediaPlayer: MediaPlayer) {
        runCatching {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnCompletionListener {
                _state.update { state -> state.copy(playing = false, playbackFinished = true) }
                releasePlayer()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                _state.update { state -> state.copy(playing = false, failure = AudioFailure.PROCESSING_FAILED) }
                releasePlayer()
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            _state.update { it.copy(playing = true, playbackFinished = false) }
        }.onFailure {
            runCatching { mediaPlayer.release() }
            _state.update { state -> state.copy(playing = false, failure = AudioFailure.FILE_UNREADABLE) }
        }
    }

    fun stopPlayback() {
        releasePlayer()
        _state.update { it.copy(playing = false) }
    }

    private fun releasePlayer() {
        player?.let { runCatching { if (it.isPlaying) it.stop() } }
        player?.let { runCatching { it.release() } }
        player = null
    }

    fun clearFailure() {
        _state.update { it.copy(failure = null) }
    }

    private fun startLevelPolling() {
        levelJob?.cancel()
        levelJob = viewModelScope.launch {
            while (isActive) {
                delay(80)
                val recording = recorder.isRecording
                _state.update {
                    it.copy(
                        stage = if (recording) Stage.RECORDING else it.stage,
                        seconds = recorder.durationSeconds,
                        level = recorder.level01,
                        waveform = recorder.levelHistory(),
                        peakDb = recorder.peakDbfs,
                    )
                }
                if (!recording) break
            }
        }
    }

    override fun onCleared() {
        levelJob?.cancel()
        recorder.cancel()
        releasePlayer()
        super.onCleared()
    }

    private data class Processed(
        val file: File,
        val name: String,
        val rendered: OfflineRenderer.Rendered,
    )
}
