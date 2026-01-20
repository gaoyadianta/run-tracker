package com.sdevprem.runtrack.ai.config

import android.content.Context
import com.sdevprem.runtrack.R
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BailianConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val apiKeyOverride: String by lazy {
        context.getString(R.string.ai_bailian_api_key).trim()
    }

    private val apiKeyFallback: String by lazy {
        context.getString(R.string.ai_ws_bailian_token).trim()
    }

    val apiKey: String
        get() = if (apiKeyOverride.isNotBlank()) apiKeyOverride else apiKeyFallback

    val realtimeBaseUrl: String by lazy {
        context.getString(R.string.ai_bailian_realtime_url).trim()
            .ifBlank { DEFAULT_REALTIME_URL }
    }

    val llmBaseUrl: String by lazy {
        context.getString(R.string.ai_bailian_llm_base_url).trim()
            .ifBlank { DEFAULT_LLM_BASE_URL }
    }

    val asrModel: String by lazy {
        context.getString(R.string.ai_bailian_asr_model).trim()
            .ifBlank { DEFAULT_ASR_MODEL }
    }

    val llmModel: String by lazy {
        context.getString(R.string.ai_bailian_llm_model).trim()
            .ifBlank { DEFAULT_LLM_MODEL }
    }

    val ttsModel: String by lazy {
        context.getString(R.string.ai_bailian_tts_model).trim()
            .ifBlank { DEFAULT_TTS_MODEL }
    }

    val ttsVoice: String by lazy {
        context.getString(R.string.ai_bailian_tts_voice).trim()
            .ifBlank { DEFAULT_TTS_VOICE }
    }

    val ttsResponseFormat: String by lazy {
        context.getString(R.string.ai_bailian_tts_format).trim()
            .ifBlank { DEFAULT_TTS_FORMAT }
    }

    private val vadModeRaw: String by lazy {
        context.getString(R.string.ai_bailian_vad_mode).trim()
    }

    val vadMode: BailianVadMode
        get() = BailianVadMode.fromValue(vadModeRaw)

    val vadSilenceMs: Long
        get() = context.getString(R.string.ai_bailian_vad_silence_ms).trim()
            .toLongOrNull()
            ?.coerceAtLeast(200L)
            ?: DEFAULT_VAD_SILENCE_MS

    val asrSampleRate: Int
        get() = DEFAULT_ASR_SAMPLE_RATE

    fun isConfigured(): Boolean {
        return apiKey.isNotBlank()
    }

    fun buildRealtimeUrl(model: String): String {
        val httpUrl = realtimeBaseUrl.toHttpUrlOrNull()
        return httpUrl?.newBuilder()
            ?.addQueryParameter("model", model)
            ?.build()
            ?.toString()
            ?: "$realtimeBaseUrl?model=$model"
    }

    companion object {
        private const val DEFAULT_REALTIME_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime"
        private const val DEFAULT_LLM_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        private const val DEFAULT_ASR_MODEL = "qwen3-asr-flash-realtime"
        private const val DEFAULT_LLM_MODEL = "qwen-plus"
        private const val DEFAULT_TTS_MODEL = "qwen3-tts-flash-realtime"
        private const val DEFAULT_TTS_VOICE = "Cherry"
        private const val DEFAULT_TTS_FORMAT = "PCM_24000HZ_MONO_16BIT"
        private const val DEFAULT_ASR_SAMPLE_RATE = 16000
        private const val DEFAULT_VAD_SILENCE_MS = 500L
    }

    enum class BailianVadMode {
        SERVER,
        CLIENT,
        BOTH;

        companion object {
            fun fromValue(value: String): BailianVadMode {
                return when (value.lowercase()) {
                    "client", "local", "device" -> CLIENT
                    "both", "hybrid" -> BOTH
                    else -> SERVER
                }
            }
        }
    }
}
