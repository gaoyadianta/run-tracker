package com.sdevprem.runtrack.ai.bailian

import com.fasterxml.jackson.databind.ObjectMapper
import com.sdevprem.runtrack.ai.config.BailianConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BailianTtsClient @Inject constructor(
    private val config: BailianConfig
) {
    private val mapper = ObjectMapper()
    private var session: BailianWebSocketSession? = null

    suspend fun connect(): Result<Unit> {
        if (!config.isConfigured()) {
            return Result.failure(IllegalStateException("Bailian API key is missing"))
        }

        session = BailianWebSocketSession(
            url = config.realtimeBaseUrl,
            headers = mapOf(
                "Authorization" to "Bearer ${config.apiKey}",
                "OpenAI-Beta" to "realtime=v1"
            )
        )

        val result = session?.connect() ?: Result.failure(IllegalStateException("TTS session init failed"))
        if (result.isSuccess) {
            sendSessionUpdate()
        }
        return result
    }

    fun disconnect() {
        session?.close()
        session = null
    }

    fun appendText(text: String) {
        if (text.isBlank()) return
        val payload = mapOf(
            "event_id" to buildEventId("text"),
            "type" to "input_text_buffer.append",
            "text" to text
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

    fun observeAudioDeltas(): Flow<String> {
        val activeSession = session ?: return emptyFlow()
        return activeSession.incomingMessages.mapNotNull { message ->
            try {
                val node = mapper.readTree(message)
                val type = node.get("type")?.asText()
                if (type != "response.audio.delta") {
                    return@mapNotNull null
                }
                node.get("delta")?.asText()
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse TTS message")
                null
            }
        }
    }

    private fun sendSessionUpdate() {
        val payload = mapOf(
            "event_id" to buildEventId("session"),
            "type" to "session.update",
            "session" to mapOf(
                "model" to config.ttsModel,
                "voice" to config.ttsVoice,
                "response_format" to config.ttsResponseFormat,
                "mode" to "server_commit"
            )
        )
        send(payload)
    }

    private fun send(payload: Map<String, Any?>) {
        val text = mapper.writeValueAsString(payload)
        if (session?.send(text) != true) {
            Timber.w("TTS message send failed")
        }
    }

    private fun buildEventId(prefix: String): String {
        return "bailian_${prefix}_${System.currentTimeMillis()}"
    }
}
