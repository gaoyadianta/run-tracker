package com.sdevprem.runtrack.ai.bailian

import com.fasterxml.jackson.databind.ObjectMapper
import com.sdevprem.runtrack.ai.config.BailianConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BailianLlmClient @Inject constructor(
    private val config: BailianConfig
) {
    private val client = OkHttpClient()
    private val mapper = ObjectMapper()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun streamChatCompletion(messages: List<Map<String, String>>): Flow<String> = callbackFlow {
        if (!config.isConfigured()) {
            close(IllegalStateException("Bailian API key is missing"))
            return@callbackFlow
        }

        val requestBody = mapOf(
            "model" to config.llmModel,
            "messages" to messages,
            "stream" to true
        )
        val bodyText = mapper.writeValueAsString(requestBody)
        val request = Request.Builder()
            .url("${config.llmBaseUrl}/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(bodyText.toRequestBody(jsonMediaType))
            .build()

        val call = client.newCall(request)

        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        close(IllegalStateException("LLM request failed: ${response.code}"))
                        return@use
                    }
                    val source = response.body?.source() ?: run {
                        close(IllegalStateException("LLM response body is empty"))
                        return@use
                    }

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break

                        val delta = parseDelta(data)
                        if (!delta.isNullOrBlank()) {
                            trySend(delta)
                        }
                    }
                    close()
                }
            } catch (e: Exception) {
                Timber.w(e, "LLM stream failed")
                close(e)
            }
        }

        awaitClose {
            call.cancel()
        }
    }

    private fun parseDelta(data: String): String? {
        return try {
            val node = mapper.readTree(data)
            node.get("choices")
                ?.get(0)
                ?.get("delta")
                ?.get("content")
                ?.asText()
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse LLM delta")
            null
        }
    }
}
