package com.sdevprem.runtrack.ai.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioStreamPlayer @Inject constructor() {
    private var audioTrack: AudioTrack? = null

    fun start(sampleRate: Int = WebSocketAudioRecorder.SAMPLE_RATE) {
        if (audioTrack != null) return

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            Timber.e("无法获取AudioTrack缓冲区大小")
            return
        }

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBuffer * 2,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack?.play()
    }

    fun play(data: ByteArray) {
        if (audioTrack == null) {
            start()
        }
        audioTrack?.write(data, 0, data.size)
    }

    fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
