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
class VolcanoV3AsrClient @Inject constructor(
    private val config: VolcanoV3Config
) {
    private val mapper = ObjectMapper()
    private var session: VolcanoBinaryWebSocketSession? = null

    suspend fun connect(): Result<Unit> {
        if (!config.isConfigured()) {
            return Result.failure(IllegalStateException("Volcano V3 ASR config is missing"))
        }

        session = VolcanoBinaryWebSocketSession(
            url = config.asrUrl,
            headers = mapOf(
                "X-Api-App-Key" to config.appId,
                "X-Api-Access-Key" to config.accessKey,
                "X-Api-Resource-Id" to config.asrResourceId,
                "X-Api-Connect-Id" to UUID.randomUUID().toString()
            )
        )

        val result = session?.connect() ?: Result.failure(IllegalStateException("ASR session init failed"))
        if (result.isSuccess) {
            sendStartRequest()
        }
        return result
    }

    fun disconnect() {
        session?.close()
        session = null
    }

    fun sendAudioFrame(frame: ByteArray, isFinal: Boolean = false) {
        val header = VolcanoBinaryProtocol.buildHeader(
            messageType = VolcanoBinaryProtocol.MESSAGE_TYPE_AUDIO_ONLY_REQUEST,
            flags = if (isFinal) VolcanoBinaryProtocol.FLAG_FINAL else VolcanoBinaryProtocol.FLAG_NONE,
            serialization = VolcanoBinaryProtocol.SERIALIZATION_RAW,
            compression = VolcanoBinaryProtocol.COMPRESSION_NONE
        )
        val payloadSize = VolcanoBinaryProtocol.uint32(frame.size.toLong())
        val packet = ByteArray(header.size + payloadSize.size + frame.size)
        System.arraycopy(header, 0, packet, 0, header.size)
        System.arraycopy(payloadSize, 0, packet, header.size, payloadSize.size)
        System.arraycopy(frame, 0, packet, header.size + payloadSize.size, frame.size)
        session?.send(packet)
    }

    fun finish() {
        sendAudioFrame(ByteArray(0), isFinal = true)
    }

    fun observeResults(): Flow<VolcanoV3AsrResult> {
        val activeSession = session ?: return emptyFlow()
        return activeSession.incomingBinary.mapNotNull { bytes ->
            parseAsrResponse(bytes)
        }
    }

    private fun sendStartRequest() {
        val payload = mapOf(
            "user" to mapOf(
                "uid" to "runtrack"
            ),
            "audio" to mapOf(
                "format" to "pcm",
                "rate" to config.asrSampleRate,
                "bits" to 16,
                "channel" to 1
            ),
            "request" to mapOf(
                "model_name" to "bigmodel",
                "enable_itn" to true,
                "enable_punc" to true,
                "show_utterances" to true,
                "result_type" to "full"
            )
        )

        val json = mapper.writeValueAsBytes(payload)
        val header = VolcanoBinaryProtocol.buildHeader(
            messageType = VolcanoBinaryProtocol.MESSAGE_TYPE_FULL_REQUEST,
            flags = VolcanoBinaryProtocol.FLAG_NONE,
            serialization = VolcanoBinaryProtocol.SERIALIZATION_JSON,
            compression = VolcanoBinaryProtocol.COMPRESSION_NONE
        )
        val payloadSize = VolcanoBinaryProtocol.uint32(json.size.toLong())
        val packet = ByteArray(header.size + payloadSize.size + json.size)
        System.arraycopy(header, 0, packet, 0, header.size)
        System.arraycopy(payloadSize, 0, packet, header.size, payloadSize.size)
        System.arraycopy(json, 0, packet, header.size + payloadSize.size, json.size)
        session?.send(packet)
    }

    private fun parseAsrResponse(bytes: ByteArray): VolcanoV3AsrResult? {
        if (bytes.size < 12) return null
        val header = VolcanoBinaryProtocol.parseHeader(bytes)
        if (header.messageType == VolcanoBinaryProtocol.MESSAGE_TYPE_ERROR) {
            Timber.w("Volcano ASR error frame")
            return null
        }
        if (header.messageType != VolcanoBinaryProtocol.MESSAGE_TYPE_FULL_RESPONSE) {
            return null
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.position(4)
        buffer.int // sequence
        val payloadSize = VolcanoBinaryProtocol.readUint32(buffer).toInt()
        if (payloadSize <= 0 || buffer.remaining() < payloadSize) {
            return null
        }

        val payloadBytes = ByteArray(payloadSize)
        buffer.get(payloadBytes)

        return try {
            val node = mapper.readTree(payloadBytes)
            val resultNode = node.get("result") ?: return null
            val text = resultNode.get("text")?.asText().orEmpty()
            if (text.isBlank()) {
                return null
            }
            val utterances = resultNode.get("utterances")
            val isFinal = utterances?.any { it.get("definite")?.asBoolean() == true } == true
            VolcanoV3AsrResult(text = text, isFinal = isFinal)
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse ASR response payload")
            null
        }
    }
}
