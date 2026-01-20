package com.sdevprem.runtrack.ai.realtime

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sdevprem.runtrack.ai.config.AIProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRealtimeClient @Inject constructor(
    private val providerConfig: AIProviderConfig,
    private val transport: WebSocketAITransport,
    private val volcanoAdapter: VolcanoProviderAdapter,
    private val bailianAdapter: BailianProviderAdapter
) {
    private val mapper = ObjectMapper()

    private val adapter: AIProviderAdapter
        get() = when (providerConfig.provider) {
            AIProvider.VOLCANO -> volcanoAdapter
            AIProvider.BAILIAN -> bailianAdapter
        }

    suspend fun connect(): Result<Unit> {
        val connectionConfig = adapter.buildConnectionConfig(providerConfig)
            ?: return Result.failure(IllegalStateException("WebSocket配置不完整"))
        return transport.connect(connectionConfig)
    }

    fun disconnect() {
        transport.disconnect()
    }

    fun sendEvent(event: AIMessageEvent) {
        val mapped = adapter.mapOutgoingEvent(event)
        val payload = buildMap<String, Any?> {
            put("type", mapped.type)
            mapped.sessionId?.let { put("session_id", it) }
            mapped.seq?.let { put("seq", it) }
            put("payload", mapped.payload)
        }

        val text = mapper.writeValueAsString(payload)
        transport.send(text)
    }

    fun sendTextMessage(message: String) {
        val event = AIMessageEvent(
            type = "text.input",
            payload = mapOf("text" to message)
        )
        sendEvent(event)
    }

    fun observeEvents(): Flow<AIMessageEvent> {
        return transport.incomingMessages.mapNotNull { message ->
            parseIncomingEvent(message)?.let { event -> adapter.mapIncomingEvent(event) }
        }
    }

    private fun parseIncomingEvent(message: String): AIMessageEvent? {
        return try {
            val node = mapper.readTree(message)
            val type = node.get("type")?.asText() ?: return null
            val sessionId = node.get("session_id")?.asText()
            val seq = node.get("seq")?.asLong()
            val payloadNode = node.get("payload")
            val payload = if (payloadNode != null && !payloadNode.isNull) {
                mapper.convertValue(payloadNode, object : TypeReference<Map<String, Any?>>() {})
            } else {
                emptyMap()
            }
            AIMessageEvent(type = type, payload = payload, sessionId = sessionId, seq = seq)
        } catch (e: Exception) {
            Timber.w(e, "解析WebSocket事件失败")
            null
        }
    }
}
