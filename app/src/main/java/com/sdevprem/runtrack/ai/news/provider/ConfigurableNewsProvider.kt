package com.sdevprem.runtrack.ai.news.provider

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sdevprem.runtrack.ai.news.NewsTextUtils
import com.sdevprem.runtrack.ai.news.config.NewsProgramConfig
import com.sdevprem.runtrack.ai.news.config.NewsProviderMode
import com.sdevprem.runtrack.ai.news.generator.NewsBriefGenerator
import com.sdevprem.runtrack.ai.news.model.NewsArticle
import com.sdevprem.runtrack.ai.news.model.NewsBrief
import com.sdevprem.runtrack.ai.news.model.NewsBriefBatch
import com.sdevprem.runtrack.ai.news.model.NewsBriefRequest
import com.sdevprem.runtrack.ai.news.model.NewsFailure
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigurableNewsProvider @Inject constructor(
    private val config: NewsProgramConfig,
    private val briefGenerator: NewsBriefGenerator,
    private val httpClient: NewsHttpClient,
    private val productionProvider: ProductionNewsProvider
) : NewsProvider {
    private val mapper = ObjectMapper()

    override suspend fun fetchBriefs(request: NewsBriefRequest): Result<NewsBriefBatch> = runCatching {
        when (config.providerMode) {
            NewsProviderMode.MOCK -> buildMockBatch(request)
            NewsProviderMode.NEWS_API -> fetchNewsApiBatch(request)
            NewsProviderMode.BACKEND -> productionProvider.fetchBriefs(request).getOrThrow()
        }
    }

    private suspend fun fetchNewsApiBatch(request: NewsBriefRequest): NewsBriefBatch {
        if (!config.isProviderConfigured()) throw NewsFailure.NotConfigured

        val now = System.currentTimeMillis()
        val oldestAllowed = now - request.normalizedMaxAgeHours * 60L * 60L * 1_000L
        val response = executeWithRetry(
            NewsApiRequestFactory.create(
                request = request,
                apiKey = config.newsApiKey,
                oldestAllowedEpochMs = oldestAllowed
            )
        )
        when (response.code) {
            200 -> Unit
            401, 403 -> throw NewsFailure.Unauthorized
            429 -> throw NewsFailure.RateLimited
            else -> throw NewsFailure.Provider("新闻接口请求失败：${response.code}")
        }

        val root = mapper.readTree(response.body)
        val articles = root.path("articles")
            .takeIf { it.isArray }
            ?.asSequence()
            ?: emptySequence()
        val mappedArticles = articles
            .mapNotNull(::mapArticle)
            .filter { it.publishedAtEpochMs == null || it.publishedAtEpochMs >= oldestAllowed }
            .distinctBy { canonicalArticleKey(it) }
            .take(request.normalizedLimit)
            .toList()
        if (mappedArticles.isEmpty()) throw NewsFailure.NoContent

        val briefs = mappedArticles.map { article ->
            NewsBrief(
                id = article.id,
                title = article.title,
                sourceName = article.sourceName,
                publishedAtEpochMs = article.publishedAtEpochMs,
                articleUrl = article.url,
                spokenText = briefGenerator.generate(article, request.language)
            )
        }.filter { it.spokenText.isNotBlank() }
        if (briefs.isEmpty()) throw NewsFailure.NoContent
        return NewsBriefBatch(briefs = briefs, fetchedAtEpochMs = now)
    }

    private suspend fun executeWithRetry(request: Request): NewsHttpResponse {
        val delays = longArrayOf(1_000L, 3_000L)
        var lastNetworkError: IOException? = null
        repeat(3) { attempt ->
            val response = try {
                httpClient.execute(request)
            } catch (error: IOException) {
                lastNetworkError = error
                if (attempt < delays.size) {
                    delay(delays[attempt])
                    return@repeat
                }
                throw NewsFailure.Network(error)
            }
            val retryable = response.code == 408 || response.code == 429 || response.code >= 500
            if (retryable && attempt < delays.size) {
                delay(delays[attempt])
            } else {
                return response
            }
        }
        throw NewsFailure.Network(lastNetworkError)
    }

    private fun mapArticle(node: JsonNode): NewsArticle? {
        val url = node.readText("url")
        val title = node.readText("title")
        if (url.isBlank() || title.isBlank()) return null
        return NewsArticle(
            id = url,
            title = title,
            sourceName = node.path("source").readText("name").ifBlank { "未知来源" },
            publishedAtEpochMs = NewsTextUtils.parseEpochMs(node.readText("publishedAt")),
            url = url,
            language = null,
            description = NewsTextUtils.cleanSnippet(node.readText("description"))
                .take(200)
                .ifBlank { null },
            contentSnippet = NewsTextUtils.cleanSnippet(node.readText("content"))
                .take(200)
                .ifBlank { null }
        )
    }

    private fun buildMockBatch(request: NewsBriefRequest): NewsBriefBatch {
        if (!config.isProviderConfigured()) throw NewsFailure.NotConfigured
        val now = System.currentTimeMillis()
        val chinese = listOf(
            NewsBrief(
                id = "mock-cn-interval",
                title = "三分钟快跑配合两分钟慢跑更容易坚持",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 15 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/interval",
                spokenText = "间歇训练可以先热身十分钟，再完成四到六组三分钟快跑和两分钟慢跑。快跑阶段保持能够说短句但明显吃力，恢复阶段不要停下，最后用五分钟慢跑和拉伸结束训练。"
            ),
            NewsBrief(
                id = "mock-cn-hydration",
                title = "长距离跑每二十分钟小口补水更稳定",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 45 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/hydration",
                spokenText = "长距离训练建议每二十分钟小口补水，炎热或潮湿天气可以适当前移补给时间。训练超过一小时可补充少量电解质，跑后半小时内完成碳水和蛋白质恢复餐。"
            ),
            NewsBrief(
                id = "mock-cn-recovery",
                title = "恢复跑应保持可以连续对话的强度",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 90 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/recovery",
                spokenText = "恢复跑的目标是促进循环，而不是再次刺激强度。把速度控制在能够连续对话的区间，高强度训练后的恢复跑以二十到四十分钟为宜，并优先保证睡眠。"
            ),
            NewsBrief(
                id = "mock-cn-cadence",
                title = "步频调整应循序渐进避免刻意迈小步",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 120 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/cadence",
                spokenText = "调整步频时不必追求统一数字。先观察轻松跑的自然步频，再用节拍器提高百分之三到五，并保持落脚点接近身体重心。出现小腿紧张时应恢复原节奏。"
            ),
            NewsBrief(
                id = "mock-cn-sleep",
                title = "稳定睡眠比临时增加训练量更有利于恢复",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 150 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/sleep",
                spokenText = "连续训练阶段应优先保持规律睡眠。若早晨静息心率明显升高并伴随疲劳，可把当天强度课改为轻松跑或休息，避免用额外训练弥补状态波动。"
            )
        )
        val english = listOf(
            NewsBrief(
                id = "mock-en-tempo",
                title = "Tempo sessions improve lactate tolerance",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 30 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/tempo",
                spokenText = "A tempo session is a sustained effort just below race intensity. Warm up for ten minutes, run twenty minutes at controlled discomfort, and finish with an easy eight-minute cool down."
            ),
            NewsBrief(
                id = "mock-en-hydration",
                title = "Small regular drinks support long-run hydration",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 60 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/en-hydration",
                spokenText = "Small, regular drinks are easier to tolerate than a large amount at once. Start before you feel very thirsty, adjust for heat and humidity, and include electrolytes when a run lasts longer than one hour."
            ),
            NewsBrief(
                id = "mock-en-recovery",
                title = "Recovery runs should stay conversational",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 90 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/en-recovery",
                spokenText = "A recovery run supports circulation without adding another hard stimulus. Keep the pace easy enough for continuous conversation, limit the session to twenty to forty minutes, and prioritize sleep after demanding workouts."
            ),
            NewsBrief(
                id = "mock-en-cadence",
                title = "Cadence changes work best in small steps",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 120 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/en-cadence",
                spokenText = "There is no universal cadence target. Measure your natural rhythm during an easy run, increase it by only three to five percent, and return to your normal rhythm if your calves become unusually tight."
            ),
            NewsBrief(
                id = "mock-en-sleep",
                title = "Consistent sleep supports training recovery",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 150 * 60_000L,
                articleUrl = "https://example.com/runmate/mock/en-sleep",
                spokenText = "Regular sleep can be more useful than adding another workout. When morning resting heart rate rises alongside persistent fatigue, replace the planned hard session with an easy run or a rest day."
            )
        )
        val selected = if (request.language.startsWith("en", ignoreCase = true)) english else chinese
        return NewsBriefBatch(selected.take(request.normalizedLimit), now)
    }

    private fun canonicalArticleKey(article: NewsArticle): String =
        article.url.substringBefore('#').substringBefore('?').trimEnd('/').lowercase(Locale.ROOT)

    private fun JsonNode.readText(field: String): String {
        val value = path(field)
        return if (value.isMissingNode || value.isNull) "" else value.asText().trim()
    }
}

internal object NewsApiRequestFactory {
    fun create(
        request: NewsBriefRequest,
        apiKey: String,
        oldestAllowedEpochMs: Long
    ): Request {
        val from = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(oldestAllowedEpochMs))
        val url = "https://newsapi.org/v2/everything".toHttpUrl().newBuilder()
            .addQueryParameter("q", request.keyword)
            .addQueryParameter("language", request.language)
            .addQueryParameter("from", from)
            .addQueryParameter("sortBy", "publishedAt")
            .addQueryParameter("page", "1")
            .addQueryParameter("pageSize", (request.normalizedLimit * 2).coerceAtMost(20).toString())
            .build()
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Api-Key", apiKey)
            .get()
            .build()
    }
}
