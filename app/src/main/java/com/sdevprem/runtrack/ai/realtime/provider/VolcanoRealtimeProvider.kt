package com.sdevprem.runtrack.ai.realtime.provider

import android.util.Base64
import com.sdevprem.runtrack.ai.audio.WebSocketAudioRecorder
import com.sdevprem.runtrack.ai.config.AIProviderConfig
import com.sdevprem.runtrack.ai.realtime.AIMessageEvent
import com.sdevprem.runtrack.ai.realtime.AIRealtimeClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolcanoRealtimeProvider @Inject constructor(
    private val client: AIRealtimeClient,
    private val providerConfig: AIProviderConfig
) : AIRealtimeProvider {
    override val isConfigured: Boolean
        get() = providerConfig.isWebSocketConfigured()

    override val localVadEnabled: Boolean
        get() = true

    override suspend fun connect(): Result<Unit> {
        val result = client.connect()
        if (result.isSuccess) {
            client.sendEvent(
                AIMessageEvent(
                    type = "session.start",
                    payload = mapOf(
                        "format" to "pcm16",
                        "sample_rate" to WebSocketAudioRecorder.SAMPLE_RATE
                    )
                )
            )
        }
        return result
    }

    override fun disconnect() {
        client.disconnect()
    }

    override fun sendText(text: String) {
        client.sendTextMessage(text)
    }

    override fun sendAudioFrame(frame: ByteArray) {
        val payload = mapOf(
            "format" to "pcm16",
            "sample_rate" to WebSocketAudioRecorder.SAMPLE_RATE,
            "data" to Base64.encodeToString(frame, Base64.NO_WRAP)
        )
        client.sendEvent(
            AIMessageEvent(
                type = "audio.input",
                payload = payload
            )
        )
    }

    override fun observeEvents(): Flow<AIRealtimeProviderEvent> {
        return client.observeEvents().mapNotNull { event ->
            when (event.type) {
                "text.delta" -> {
                    val text = event.payload["text"] as? String
                    text?.let { AIRealtimeProviderEvent.AssistantTextDelta(it) }
                }
                "audio.output" -> {
                    val data = event.payload["data"] as? String
                    data?.let {
                        val audio = Base64.decode(it, Base64.DEFAULT)
                        AIRealtimeProviderEvent.AssistantAudioDelta(audio)
                    }
                }
                "conversation.message.completed", "audio.completed" -> {
                    AIRealtimeProviderEvent.AssistantCompleted(event.type)
                }
                "error" -> {
                    AIRealtimeProviderEvent.Error()
                }
                else -> null
            }
        }
    }
}
