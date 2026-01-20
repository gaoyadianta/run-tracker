package com.sdevprem.runtrack.ai.config

import android.content.Context
import com.sdevprem.runtrack.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolcanoV3Config @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modeRaw: String by lazy {
        context.getString(R.string.ai_volcano_ws_mode).trim()
    }

    val wsMode: VolcanoWsMode
        get() = VolcanoWsMode.fromValue(modeRaw)

    val appId: String by lazy {
        context.getString(R.string.ai_volcano_app_id).trim()
    }

    val accessKey: String by lazy {
        context.getString(R.string.ai_volcano_access_key).trim()
    }

    val asrResourceId: String by lazy {
        context.getString(R.string.ai_volcano_asr_resource_id).trim()
    }

    val ttsResourceId: String by lazy {
        context.getString(R.string.ai_volcano_tts_resource_id).trim()
    }

    val asrUrl: String by lazy {
        context.getString(R.string.ai_volcano_asr_url).trim()
            .ifBlank { DEFAULT_ASR_URL }
    }

    val ttsUrl: String by lazy {
        context.getString(R.string.ai_volcano_tts_url).trim()
            .ifBlank { DEFAULT_TTS_URL }
    }

    val ttsVoice: String by lazy {
        context.getString(R.string.ai_volcano_tts_voice).trim()
    }

    val ttsModel: String by lazy {
        context.getString(R.string.ai_volcano_tts_model).trim()
    }

    val ttsSampleRate: Int
        get() = context.getString(R.string.ai_volcano_tts_sample_rate).trim()
            .toIntOrNull()
            ?: DEFAULT_TTS_SAMPLE_RATE

    val arkApiKey: String by lazy {
        context.getString(R.string.ai_volcano_ark_api_key).trim()
    }

    val arkBaseUrl: String by lazy {
        context.getString(R.string.ai_volcano_ark_base_url).trim()
            .ifBlank { DEFAULT_ARK_BASE_URL }
    }

    val arkModel: String by lazy {
        context.getString(R.string.ai_volcano_ark_model).trim()
    }

    val asrSampleRate: Int
        get() = DEFAULT_ASR_SAMPLE_RATE

    fun isConfigured(): Boolean {
        return appId.isNotBlank() &&
            accessKey.isNotBlank() &&
            asrResourceId.isNotBlank() &&
            ttsResourceId.isNotBlank() &&
            ttsVoice.isNotBlank() &&
            arkApiKey.isNotBlank() &&
            arkModel.isNotBlank()
    }

    enum class VolcanoWsMode {
        JSON,
        V3;

        companion object {
            fun fromValue(value: String): VolcanoWsMode {
                return when (value.lowercase()) {
                    "v3", "binary" -> V3
                    else -> JSON
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_ASR_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
        private const val DEFAULT_TTS_URL = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"
        private const val DEFAULT_ASR_SAMPLE_RATE = 16000
        private const val DEFAULT_TTS_SAMPLE_RATE = 24000
        private const val DEFAULT_ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
    }
}
