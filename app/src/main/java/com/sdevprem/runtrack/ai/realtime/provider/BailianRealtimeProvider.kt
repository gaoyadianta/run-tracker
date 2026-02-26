package com.sdevprem.runtrack.ai.realtime.provider

import android.util.Base64
import com.sdevprem.runtrack.ai.bailian.BailianAsrClient
import com.sdevprem.runtrack.ai.bailian.BailianLlmClient
import com.sdevprem.runtrack.ai.bailian.BailianTtsClient
import com.sdevprem.runtrack.ai.config.BailianConfig
import com.sdevprem.runtrack.di.ApplicationScope
import com.sdevprem.runtrack.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BailianRealtimeProvider @Inject constructor(
    private val config: BailianConfig,
    private val asrClient: BailianAsrClient,
    private val llmClient: BailianLlmClient,
    private val ttsClient: BailianTtsClient,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AIRealtimeProvider {
    override val isConfigured: Boolean
        get() = config.isConfigured()

    override val localVadEnabled: Boolean
        get() = config.vadMode != BailianConfig.BailianVadMode.SERVER

    private val events = MutableSharedFlow<AIRealtimeProviderEvent>(extraBufferCapacity = 128)
    private val messages = mutableListOf<Map<String, String>>()
    private val ttsBuffer = StringBuilder()

    private var asrJob: Job? = null
    private var ttsJob: Job? = null
    private var llmJob: Job? = null
    private var vadMonitorJob: Job? = null
    @Volatile private var lastSpeechMs: Long = 0L
    @Volatile private var lastFinalTranscript: String = ""
    @Volatile private var lastFinalTranscriptAtMs: Long = 0L

    override suspend fun connect(): Result<Unit> {
        if (!config.isConfigured()) {
            return Result.failure(IllegalStateException("Bailian API key is missing"))
        }

        val asrResult = asrClient.connect()
        if (asrResult.isFailure) {
            return asrResult
        }

        val ttsResult = ttsClient.connect()
        if (ttsResult.isFailure) {
            return ttsResult
        }

        startAsrCollection()
        startTtsCollection()
        if (config.vadMode == BailianConfig.BailianVadMode.CLIENT) {
            startVadMonitor()
        }

        return Result.success(Unit)
    }

    override fun disconnect() {
        asrJob?.cancel()
        asrJob = null
        ttsJob?.cancel()
        ttsJob = null
        llmJob?.cancel()
        llmJob = null
        vadMonitorJob?.cancel()
        vadMonitorJob = null
        lastSpeechMs = 0L
        lastFinalTranscript = ""
        lastFinalTranscriptAtMs = 0L

        asrClient.finish()
        ttsClient.finish()
        asrClient.disconnect()
        ttsClient.disconnect()

        synchronized(messages) {
            messages.clear()
        }
        ttsBuffer.clear()
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
                llmClient.streamChatCompletion(messagesSnapshot).collect { delta ->
                    assistantBuffer.append(delta)
                    events.emit(AIRealtimeProviderEvent.AssistantTextDelta(delta))
                    appendTtsBuffer(delta)
                }
            } catch (e: Exception) {
                Timber.w(e, "Bailian LLM stream failed")
                events.emit(AIRealtimeProviderEvent.Error(e.message))
            } finally {
                val assistantText = assistantBuffer.toString().trim()
                if (assistantText.isNotEmpty()) {
                    synchronized(messages) {
                        messages.add(mapOf("role" to "assistant", "content" to assistantText))
                    }
                }
                flushTtsBuffer(force = true)
                events.emit(AIRealtimeProviderEvent.AssistantCompleted("llm.done"))
            }
        }
    }

    override fun sendAudioFrame(frame: ByteArray) {
        if (config.vadMode == BailianConfig.BailianVadMode.CLIENT) {
            lastSpeechMs = System.currentTimeMillis()
        }
        asrClient.sendAudioFrame(frame)
    }

    override fun observeEvents(): Flow<AIRealtimeProviderEvent> = events

    private fun startAsrCollection() {
        asrJob?.cancel()
        asrJob = scope.launch {
            asrClient.observeResults().collect { result ->
                events.emit(AIRealtimeProviderEvent.UserTranscript(result.text, result.isFinal))
                if (result.isFinal && result.text.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    val normalized = result.text.trim()
                    if (shouldIgnoreTranscript(normalized)) {
                        Timber.d("Ignore non-informative ASR final transcript: $normalized")
                        return@collect
                    }
                    val isImmediateDuplicate = normalized == lastFinalTranscript &&
                        now - lastFinalTranscriptAtMs <= FINAL_DUPLICATE_WINDOW_MS
                    if (normalized.isNotBlank() && !isImmediateDuplicate) {
                        lastFinalTranscript = normalized
                        lastFinalTranscriptAtMs = now
                        sendText(normalized)
                    }
                }
            }
        }
    }

    private fun startTtsCollection() {
        ttsJob?.cancel()
        ttsJob = scope.launch {
            ttsClient.observeAudioDeltas().collect { delta ->
                if (delta.isNotBlank()) {
                    val audioBytes = Base64.decode(delta, Base64.DEFAULT)
                    events.emit(AIRealtimeProviderEvent.AssistantAudioDelta(audioBytes))
                }
            }
        }
    }

    private fun startVadMonitor() {
        vadMonitorJob?.cancel()
        vadMonitorJob = scope.launch(ioDispatcher) {
            while (true) {
                val lastSpeech = lastSpeechMs
                if (lastSpeech > 0L) {
                    val idleMs = System.currentTimeMillis() - lastSpeech
                    if (idleMs >= config.vadSilenceMs) {
                        asrClient.commitAudio()
                        lastSpeechMs = 0L
                    }
                }
                delay(100)
            }
        }
    }

    private fun appendTtsBuffer(delta: String) {
        ttsBuffer.append(delta)
        if (shouldFlushTts(delta)) {
            flushTtsBuffer(force = false)
        }
    }

    private fun shouldFlushTts(delta: String): Boolean {
        if (ttsBuffer.length >= 24) return true
        return delta.any { ch ->
            ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?'
        }
    }

    private fun flushTtsBuffer(force: Boolean) {
        val text = ttsBuffer.toString().trim()
        if (text.isEmpty()) {
            ttsBuffer.clear()
            return
        }
        if (force || text.length >= 8) {
            ttsClient.appendText(text)
            ttsBuffer.clear()
        }
    }

    companion object {
        private const val FINAL_DUPLICATE_WINDOW_MS = 1500L
        private val FILLER_WORDS = setOf(
            "嗯",
            "嗯嗯",
            "嗯嗯嗯",
            "啊",
            "啊啊",
            "呃",
            "额",
            "哦",
            "噢",
            "唔",
            "哼",
            "诶",
            "欸",
            "唉",
            "哈",
            "是的",
            "好的",
            "好吧",
            "对",
            "对的"
        )
        private val FILLER_CHARS = setOf(
            '嗯', '啊', '呃', '额', '哦', '噢', '唔', '哼', '诶', '欸', '唉', '哈'
        )
        private val PUNCTUATION_CHARS = setOf(
            '。', '，', '！', '？', '、', '；', '：', '…', '—', '-', '~', '～',
            '.', ',', '!', '?', ';', ':', '(', ')', '[', ']', '{', '}', '"', '\'', '`'
        )
    }

    private fun shouldIgnoreTranscript(text: String): Boolean {
        if (text.isBlank()) return true
        if (isPunctuationOnly(text)) return true

        val canonical = text
            .trim()
            .replace(Regex("\\s+"), "")
            .trim { it in PUNCTUATION_CHARS }
            .lowercase()

        if (canonical.isBlank()) return true
        if (canonical in FILLER_WORDS) return true
        if (canonical.length <= 3 && canonical.all { it in FILLER_CHARS }) return true

        return false
    }

    private fun isPunctuationOnly(text: String): Boolean {
        val stripped = text.trim().replace(Regex("\\s+"), "")
        if (stripped.isBlank()) return true
        return stripped.all { it in PUNCTUATION_CHARS }
    }
}
