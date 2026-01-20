package com.sdevprem.runtrack.ai.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketAudioRecorder @Inject constructor(
) {
    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val vad = SimpleVad()
    private var useLocalVad = true

    private var audioRecord: AudioRecord? = null
    private var recordingJob: kotlinx.coroutines.Job? = null

    private val _audioFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
    val audioFrames: SharedFlow<ByteArray> = _audioFrames.asSharedFlow()

    fun start() {
        if (recordingJob != null) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Timber.e("无法获取AudioRecord缓冲区大小")
            return
        }

        val bufferSize = minBufferSize * 2
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Timber.e("AudioRecord初始化失败")
            audioRecord?.release()
            audioRecord = null
            return
        }

        audioRecord?.startRecording()
        val buffer = ByteArray(bufferSize)
        recordingJob = scope.launch {
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val shouldEmit = if (useLocalVad) vad.isSpeech(buffer, read) else true
                    if (shouldEmit) {
                        _audioFrames.emit(buffer.copyOf(read))
                    }
                }
            }
        }
        Timber.d("WebSocket音频采集已启动")
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Timber.d("WebSocket音频采集已停止")
    }

    fun setUseLocalVad(enabled: Boolean) {
        useLocalVad = enabled
    }
}
