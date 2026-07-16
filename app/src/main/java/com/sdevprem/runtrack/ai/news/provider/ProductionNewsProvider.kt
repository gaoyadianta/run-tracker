package com.sdevprem.runtrack.ai.news.provider

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sdevprem.runtrack.BuildConfig
import com.sdevprem.runtrack.ai.news.config.NewsProgramConfig
import com.sdevprem.runtrack.ai.news.model.NewsBrief
import com.sdevprem.runtrack.ai.news.model.NewsBriefBatch
import com.sdevprem.runtrack.ai.news.model.NewsBriefRequest
import com.sdevprem.runtrack.ai.news.model.NewsFailure
import com.sdevprem.runtrack.data.repository.NewsProgramSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionNewsProvider @Inject constructor(
    private val config: NewsProgramConfig,
    private val settingsRepository: NewsProgramSettingsRepository,
    private val httpClient: NewsHttpClient
) {
    private val mapper = ObjectMapper()
    private val sessionMutex = Mutex()
    private var sessionToken: String? = null
    private var sessionExpiresAtEpochMs: Long = 0L

    suspend fun fetchBriefs(request: NewsBriefRequest): Result<NewsBriefBatch> = runCatching {
        if (!config.isProviderConfigured()) throw NewsFailure.NotConfigured
        var token = ensureSession()
        var response = executeBriefRequest(request, token)
        if (response.code == 401) {
            invalidateSession()
            token = ensureSession()
            response = executeBriefRequest(request, token)
        }
        when (response.code) {
            200 -> parseBatch(response.body, request.normalizedLimit)
            401, 403 -> throw NewsFailure.Unauthorized
            429 -> throw NewsFailure.RateLimited
            in 500..599 -> throw NewsFailure.Network()
            else -> throw NewsFailure.Provider("新闻后端请求失败：${response.code}")
        }
    }

    private suspend fun ensureSession(): String = sessionMutex.withLock {
        val now = System.currentTimeMillis()
        sessionToken?.takeIf { now < sessionExpiresAtEpochMs - 30_000L }?.let { return@withLock it }

        val installationId = settingsRepository.getOrCreateInstallationId()
        val payload = mapper.writeValueAsString(
            mapOf(
                "installationId" to installationId,
                "appVersion" to BuildConfig.VERSION_NAME
            )
        )
        val request = Request.Builder()
            .url("${config.backendBaseUrl}/v1/mobile/session")
            .header("Accept", "application/json")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val response = executeWithRetry(request)
        if (response.code !in 200..299) {
            if (response.code == 401 || response.code == 403) throw NewsFailure.Unauthorized
            if (response.code == 429) throw NewsFailure.RateLimited
            if (response.code == 408 || response.code >= 500) throw NewsFailure.Network()
            throw NewsFailure.Provider("新闻会话创建失败：${response.code}")
        }
        val root = mapper.readTree(response.body)
        val token = root.path("accessToken").asText().trim()
        if (token.isBlank()) throw NewsFailure.Provider("新闻会话响应缺少令牌")
        val absoluteExpiry = root.path("expiresAtEpochMs").asLong(0L)
        val relativeExpirySeconds = root.path("expiresInSeconds").asLong(15 * 60L)
        val expiresAt = absoluteExpiry.takeIf { it > now }
            ?: now + relativeExpirySeconds.coerceIn(60L, 15 * 60L) * 1_000L
        sessionToken = token
        sessionExpiresAtEpochMs = expiresAt
        token
    }

    private suspend fun executeBriefRequest(request: NewsBriefRequest, token: String): NewsHttpResponse {
        val base = "${config.backendBaseUrl}/v1/news/briefs".toHttpUrlOrNull()
            ?: throw NewsFailure.NotConfigured
        val url = base.newBuilder()
            .addQueryParameter("keyword", request.keyword)
            .addQueryParameter("language", request.language)
            .addQueryParameter("limit", request.normalizedLimit.toString())
            .build()
        val call = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return executeWithRetry(call)
    }

    private suspend fun executeWithRetry(request: Request): NewsHttpResponse {
        val retryDelays = longArrayOf(1_000L, 3_000L)
        repeat(3) { attempt ->
            val response = try {
                httpClient.execute(request)
            } catch (error: IOException) {
                if (attempt < retryDelays.size) {
                    delay(retryDelays[attempt])
                    return@repeat
                }
                throw NewsFailure.Network(error)
            }
            val retryable = response.code == 408 || response.code == 429 || response.code >= 500
            if (retryable && attempt < retryDelays.size) {
                delay(retryDelays[attempt])
            } else {
                return response
            }
        }
        throw NewsFailure.Network()
    }

    private fun parseBatch(body: String, limit: Int): NewsBriefBatch {
        val root = mapper.readTree(body)
        val items = root.path("briefs").takeIf { it.isArray } ?: root.path("items")
        if (!items.isArray) throw NewsFailure.Provider("新闻后端响应格式错误")
        val briefs = items.asSequence().mapNotNull(::parseBrief).take(limit).toList()
        if (briefs.isEmpty()) throw NewsFailure.NoContent
        return NewsBriefBatch(
            briefs = briefs,
            fetchedAtEpochMs = root.path("fetchedAtEpochMs").asLong(System.currentTimeMillis())
        )
    }

    private fun parseBrief(node: JsonNode): NewsBrief? {
        val id = node.path("id").asText().trim()
        val title = node.path("title").asText().trim()
        val source = node.path("sourceName").asText().trim()
        val url = node.path("articleUrl").asText().trim()
        val spokenText = node.path("spokenText").asText().trim()
        if (id.isBlank() || title.isBlank() || url.isBlank() || spokenText.isBlank()) return null
        return NewsBrief(
            id = id,
            title = title,
            sourceName = source.ifBlank { "未知来源" },
            publishedAtEpochMs = node.path("publishedAtEpochMs")
                .takeUnless { it.isMissingNode || it.isNull }
                ?.asLong(),
            articleUrl = url,
            spokenText = spokenText
        )
    }

    private suspend fun invalidateSession() = sessionMutex.withLock {
        sessionToken = null
        sessionExpiresAtEpochMs = 0L
    }
}
