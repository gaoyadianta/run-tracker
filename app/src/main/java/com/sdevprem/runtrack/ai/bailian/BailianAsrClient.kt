package com.sdevprem.runtrack.ai.bailian

import android.util.Base64
import com.fasterxml.jackson.databind.ObjectMapper
import com.sdevprem.runtrack.ai.config.BailianConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BailianAsrClient @Inject constructor(
    private val config: BailianConfig
) {
    private val mapper = ObjectMapper()
    private var session: BailianWebSocketSession? = null

    suspend fun connect(): Result<Unit> {
        if (!config.isConfigured()) {
            return Result.failure(IllegalStateException("Bailian API key is missing"))
        }

        val url = config.buildRealtimeUrl(config.asrModel)
        session = BailianWebSocketSession(
            url = url,
            headers = mapOf(
                "Authorization" to "Bearer ${config.apiKey}",
                "OpenAI-Beta" to "realtime=v1"
            )
        )

        val result = session?.connect() ?: Result.failure(IllegalStateException("ASR session init failed"))
        if (result.isSuccess) {
            sendSessionUpdate()
        }
        return result
    }

    fun disconnect() {
        session?.close()
        session = null
    }

    fun sendAudioFrame(frame: ByteArray) {
        val encoded = Base64.encodeToString(frame, Base64.NO_WRAP)
        val payload = mapOf(
            "event_id" to buildEventId("audio"),
            "type" to "input_audio_buffer.append",
            "audio" to encoded
        )
        send(payload)
    }

    fun commitAudio() {
        val payload = mapOf(
            "event_id" to buildEventId("commit"),
            "type" to "input_audio_buffer.commit"
        )
        send(payload)
    }

    fun finish() {
        val payload = mapOf(
            "event_id" to buildEventId("finish"),
            "type" to "session.finish"
        )
        send(payload)
    }

    fun observeResults(): Flow<BailianAsrResult> {
        val activeSession = session ?: return emptyFlow()
        return activeSession.incomingMessages.mapNotNull { message ->
            try {
                val node = mapper.readTree(message)
                val type = node.get("type")?.asText()
                val transcript = node.get("transcript")?.asText()
                if (!transcript.isNullOrBlank()) {
                    val isFinal = type == "session.finished"
                    BailianAsrResult(transcript, isFinal)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse ASR message")
                null
            }
        }
    }

    private fun sendSessionUpdate() {
        val sessionPayload = mutableMapOf<String, Any?>(
            "modalities" to listOf("text"),
            "input_audio_format" to "pcm",
            "sample_rate" to config.asrSampleRate,
            "input_audio_transcription" to mapOf(
                "language" to "zh"
            )
        )

        if (config.vadMode != BailianConfig.BailianVadMode.CLIENT) {
            sessionPayload["turn_detection"] = mapOf(
                "type" to "server_vad",
                "threshold" to 0.0,
                "silence_duration_ms" to config.vadSilenceMs
            )
        }

        val payload = mapOf(
            "event_id" to buildEventId("session"),
            "type" to "session.update",
            "session" to sessionPayload
        )
        send(payload)
    }

    private fun send(payload: Map<String, Any?>) {
        val text = mapper.writeValueAsString(payload)
        if (session?.send(text) != true) {
            Timber.w("ASR message send failed")
        }
    }

    private fun buildEventId(prefix: String): String {
        return "bailian_${prefix}_${System.currentTimeMillis()}"
    }
}
