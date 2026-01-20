package com.sdevprem.runtrack.ai.realtime.provider

import com.sdevprem.runtrack.ai.config.VolcanoV3Config
import com.sdevprem.runtrack.ai.volcano.ark.VolcanoArkLlmClient
import com.sdevprem.runtrack.ai.volcano.v3.VolcanoV3AsrClient
import com.sdevprem.runtrack.ai.volcano.v3.VolcanoV3TtsClient
import com.sdevprem.runtrack.di.ApplicationScope
import com.sdevprem.runtrack.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolcanoV3RealtimeProvider @Inject constructor(
    private val config: VolcanoV3Config,
    private val asrClient: VolcanoV3AsrClient,
    private val ttsClient: VolcanoV3TtsClient,
    private val llmClient: VolcanoArkLlmClient,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AIRealtimeProvider {
    override val isConfigured: Boolean
        get() = config.isConfigured()

    override val localVadEnabled: Boolean
        get() = true

    private val events = MutableSharedFlow<AIRealtimeProviderEvent>(extraBufferCapacity = 64)
    private var asrJob: Job? = null
    private var ttsJob: Job? = null
    private var llmJob: Job? = null
    private val messages = mutableListOf<Map<String, String>>()
    private var lastFinalTranscript: String = ""

    override suspend fun connect(): Result<Unit> {
        val asrResult = asrClient.connect()
        if (asrResult.isFailure) return asrResult

        val ttsResult = ttsClient.connect()
        if (ttsResult.isFailure) return ttsResult

        startAsrCollection()
        startTtsCollection()
        return Result.success(Unit)
    }

    override fun disconnect() {
        asrJob?.cancel()
        asrJob = null
        ttsJob?.cancel()
        ttsJob = null
        llmJob?.cancel()
        llmJob = null
        asrClient.finish()
        asrClient.disconnect()
        ttsClient.disconnect()
        synchronized(messages) {
            messages.clear()
        }
        lastFinalTranscript = ""
    }

    override fun sendText(text: String) {
        if (text.isBlank()) return
        val messagesSnapshot = synchronized(messages) {
            messages.add(mapOf("role" to "user", "content" to text))
            messages.toList()
        }
        llmJob?.cancel()
        llmJob = scope.launch(ioDispatcher) {
            val assistantBuffer = StringBuilder()
            try {
                llmClient.streamChatCompletion(messagesSnapshot).collectLatest { delta ->
                    assistantBuffer.append(delta)
                    events.tryEmit(AIRealtimeProviderEvent.AssistantTextDelta(delta))
                    ttsClient.appendText(delta)
                }
            } catch (e: Exception) {
                Timber.w(e, "Ark LLM stream failed")
                events.tryEmit(AIRealtimeProviderEvent.Error(e.message))
            } finally {
                val assistantText = assistantBuffer.toString().trim()
                if (assistantText.isNotEmpty()) {
                    synchronized(messages) {
                        messages.add(mapOf("role" to "assistant", "content" to assistantText))
                    }
                }
                ttsClient.finishSession()
                events.tryEmit(AIRealtimeProviderEvent.AssistantCompleted("tts.finish"))
            }
        }
    }

    override fun sendAudioFrame(frame: ByteArray) {
        asrClient.sendAudioFrame(frame)
    }

    override fun observeEvents(): Flow<AIRealtimeProviderEvent> = events

    private fun startAsrCollection() {
        asrJob?.cancel()
        asrJob = scope.launch(ioDispatcher) {
            asrClient.observeResults().collectLatest { result ->
                events.tryEmit(AIRealtimeProviderEvent.UserTranscript(result.text, result.isFinal))
                if (result.isFinal && result.text.isNotBlank()) {
                    val newText = if (lastFinalTranscript.isNotEmpty() &&
                        result.text.startsWith(lastFinalTranscript)) {
                        result.text.substring(lastFinalTranscript.length)
                    } else {
                        result.text
                    }
                    lastFinalTranscript = result.text
                    if (newText.isNotBlank()) {
                        sendText(newText.trim())
                    }
                }
            }
        }
    }

    private fun startTtsCollection() {
        ttsJob?.cancel()
        ttsJob = scope.launch(ioDispatcher) {
            ttsClient.observeAudio().collectLatest { audio ->
                events.tryEmit(AIRealtimeProviderEvent.AssistantAudioDelta(audio))
            }
        }
    }
}
