package com.sdevprem.runtrack.ai.volcano.v3

import com.fasterxml.jackson.databind.ObjectMapper
import com.sdevprem.runtrack.ai.config.VolcanoV3Config
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolcanoV3TtsClient @Inject constructor(
    private val config: VolcanoV3Config
) {
    private val mapper = ObjectMapper()
    private var session: VolcanoBinaryWebSocketSession? = null
    private var sessionId: String? = null
    private var connected = false

    suspend fun connect(): Result<Unit> {
        if (!config.isConfigured()) {
            return Result.failure(IllegalStateException("Volcano V3 TTS config is missing"))
        }

        session = VolcanoBinaryWebSocketSession(
            url = config.ttsUrl,
            headers = mapOf(
                "X-Api-App-Key" to config.appId,
                "X-Api-Access-Key" to config.accessKey,
                "X-Api-Resource-Id" to config.ttsResourceId,
                "X-Api-Connect-Id" to UUID.randomUUID().toString()
            )
        )

        val result = session?.connect() ?: Result.failure(IllegalStateException("TTS session init failed"))
        if (result.isSuccess) {
            sendStartConnection()
            connected = true
            startSession()
        }
        return result
    }

    fun disconnect() {
        if (connected) {
            sendFinishSession()
            sendFinishConnection()
        }
        session?.close()
        session = null
        connected = false
    }

    fun appendText(text: String) {
        if (text.isBlank()) return
        if (sessionId == null) {
            startSession()
        }
        val payload = mapOf(
            "namespace" to "BidirectionalTTS",
            "req_params" to mapOf(
                "text" to text
            )
        )
        sendWithEvent(
            event = EventCode.TASK_REQUEST,
            sessionId = sessionId,
            payload = payload
        )
    }

    fun finishSession() {
        sendFinishSession()
        sessionId = null
    }

    fun observeAudio(): Flow<ByteArray> {
        val activeSession = session ?: return emptyFlow()
        return activeSession.incomingBinary.mapNotNull { bytes ->
            parseAudioResponse(bytes)
        }
    }

    private fun startSession() {
        val newSessionId = UUID.randomUUID().toString().replace("-", "").take(12)
        sessionId = newSessionId
        val payload = mapOf(
            "namespace" to "BidirectionalTTS",
            "user" to mapOf(
                "uid" to "runtrack"
            ),
            "req_params" to mapOf(
                "speaker" to config.ttsVoice,
                "model" to config.ttsModel,
                "audio_params" to mapOf(
                    "format" to "pcm",
                    "sample_rate" to config.ttsSampleRate
                )
            )
        )
        sendWithEvent(
            event = EventCode.START_SESSION,
            sessionId = newSessionId,
            payload = payload
        )
    }

    private fun sendStartConnection() {
        sendWithEvent(EventCode.START_CONNECTION, null, emptyMap<String, Any>())
    }

    private fun sendFinishConnection() {
        sendWithEvent(EventCode.FINISH_CONNECTION, null, emptyMap<String, Any>())
    }

    private fun sendFinishSession() {
        val currentSession = sessionId ?: return
        sendWithEvent(EventCode.FINISH_SESSION, currentSession, emptyMap<String, Any>())
    }

    private fun sendWithEvent(
        event: Int,
        sessionId: String?,
        payload: Any
    ) {
        val payloadBytes = mapper.writeValueAsBytes(payload)
        val header = VolcanoBinaryProtocol.buildHeader(
            messageType = VolcanoBinaryProtocol.MESSAGE_TYPE_FULL_REQUEST,
            flags = VolcanoBinaryProtocol.FLAG_WITH_EVENT,
            serialization = VolcanoBinaryProtocol.SERIALIZATION_JSON,
            compression = VolcanoBinaryProtocol.COMPRESSION_NONE
        )

        val sessionBytes = sessionId?.toByteArray(Charsets.UTF_8)
        val sessionLen = sessionBytes?.size ?: 0
        val extraLen = if (sessionBytes != null) 4 + sessionLen else 0
        val totalLen = header.size + 4 + extraLen + 4 + payloadBytes.size
        val buffer = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        buffer.put(header)
        buffer.putInt(event)

        if (sessionBytes != null) {
            buffer.putInt(sessionLen)
            buffer.put(sessionBytes)
        }

        buffer.putInt(payloadBytes.size)
        buffer.put(payloadBytes)

        session?.send(buffer.array())
    }

    private fun parseAudioResponse(bytes: ByteArray): ByteArray? {
        if (bytes.size < 12) return null
        val header = VolcanoBinaryProtocol.parseHeader(bytes)
        if (header.messageType == VolcanoBinaryProtocol.MESSAGE_TYPE_ERROR) {
            Timber.w("Volcano TTS error frame")
            return null
        }

        if (header.messageType != VolcanoBinaryProtocol.MESSAGE_TYPE_FULL_RESPONSE &&
            header.messageType != VolcanoBinaryProtocol.MESSAGE_TYPE_AUDIO_ONLY_RESPONSE) {
            return null
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.position(4)
        val event = VolcanoBinaryProtocol.readInt32(buffer)

        if (event != EventCode.TTS_RESPONSE) {
            return null
        }

        if (buffer.remaining() < 4) {
            return null
        }

        val sessionIdLen = VolcanoBinaryProtocol.readUint32(buffer).toInt()
        if (sessionIdLen > 0 && buffer.remaining() >= sessionIdLen) {
            buffer.position(buffer.position() + sessionIdLen)
        }

        if (buffer.remaining() < 4) {
            return null
        }

        val payloadSize = VolcanoBinaryProtocol.readUint32(buffer).toInt()
        if (payloadSize <= 0 || buffer.remaining() < payloadSize) {
            return null
        }

        val payload = ByteArray(payloadSize)
        buffer.get(payload)
        return payload
    }

    private object EventCode {
        const val START_CONNECTION = 1
        const val FINISH_CONNECTION = 2
        const val START_SESSION = 100
        const val FINISH_SESSION = 102
        const val TASK_REQUEST = 200
        const val TTS_RESPONSE = 352
    }
}
