package com.sdevprem.runtrack.ai.news.provider

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sdevprem.runtrack.ai.news.NewsTextUtils
import com.sdevprem.runtrack.ai.news.config.NewsProgramConfig
import com.sdevprem.runtrack.ai.news.model.NewsArticle
import com.sdevprem.runtrack.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ConfigurableNewsProvider @Inject constructor(
    private val config: NewsProgramConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : NewsProvider {
    companion object {
        private const val MOCK_FEED_PREFIX = "mock://"
    }

    private val mapper = ObjectMapper()
    private val client by lazy {
        OkHttpClient.Builder().build()
    }

    override suspend fun fetchFeed(keyword: String, language: String): Result<List<NewsArticle>> = withContext(ioDispatcher) {
        runCatching {
            if (isMockFeedEnabled()) {
                return@runCatching buildMockArticles(keyword = keyword, language = language)
            }
            if (!config.isProviderConfigured()) {
                error("新闻 provider 未配置")
            }

            val url = buildFeedUrl(keyword = keyword, language = language)
            val responseText = executeRequest(url)
            val root = mapper.readTree(responseText)
            val itemsNode = findNodeByPath(root, config.feedItemsPath)
                ?: error("无法在响应中找到 feed 列表: ${config.feedItemsPath}")

            val articles = itemsNode.asSequence()
                .mapNotNull { item -> mapArticle(item) }
                .toList()

            articles
        }.onFailure { error ->
            Timber.w(error, "fetchFeed failed")
        }
    }

    override suspend fun fetchContent(article: NewsArticle): Result<String> = withContext(ioDispatcher) {
        runCatching {
            article.fullText?.takeIf { it.isNotBlank() }?.let { fromFeed ->
                return@runCatching NewsTextUtils.cleanText(fromFeed)
            }

            if (isMockFeedEnabled()) {
                return@runCatching NewsTextUtils.cleanText(
                    buildMockArticles(keyword = config.defaultKeyword, language = config.defaultLanguage)
                        .firstOrNull { it.id == article.id || it.url == article.url }
                        ?.fullText
                        .orEmpty()
                )
            }

            if (config.contentUrlTemplate.isBlank()) {
                error("新闻正文接口未配置")
            }

            val url = buildContentUrl(article)
            val responseText = executeRequest(url)
            val contentRaw = extractContent(responseText)
            val cleaned = NewsTextUtils.cleanText(contentRaw)
            if (cleaned.isBlank()) {
                error("正文为空")
            }
            cleaned
        }.onFailure { error ->
            Timber.w(error, "fetchContent failed: article=${article.url}")
        }
    }

    private fun executeRequest(url: String): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json,text/plain,*/*")

        if (config.apiKeyHeaderName.isNotBlank() && config.apiKeyValue.isNotBlank()) {
            requestBuilder.header(config.apiKeyHeaderName, config.apiKeyValue)
        }

        val request = requestBuilder.get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("新闻接口请求失败: ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun extractContent(responseText: String): String {
        val trimmed = responseText.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed
        }

        val root = mapper.readTree(trimmed)
        val node = findNodeByPath(root, config.contentTextPath)
            ?: error("正文路径未命中: ${config.contentTextPath}")
        return node.asText()
    }

    private fun mapArticle(node: JsonNode): NewsArticle? {
        val url = readText(node, config.feedUrlPath).orEmpty()
        val title = readText(node, config.feedTitlePath).orEmpty()
        if (url.isBlank() || title.isBlank()) {
            return null
        }

        val articleId = readText(node, config.feedIdPath)
            ?.ifBlank { null }
            ?: url.hashCode().toString()

        return NewsArticle(
            id = articleId,
            title = title,
            sourceName = readText(node, config.feedSourcePath).orEmpty().ifBlank { "未知来源" },
            publishedAtEpochMs = NewsTextUtils.parseEpochMs(readText(node, config.feedPublishedAtPath)),
            url = url,
            language = readText(node, config.feedLanguagePath),
            description = readText(node, config.feedDescriptionPath),
            fullText = readText(node, config.feedContentPath)
        )
    }

    private fun buildFeedUrl(keyword: String, language: String): String {
        var url = config.feedUrlTemplate
        url = url.replace("{keyword}", keyword.urlEncoded())
        url = url.replace("{language}", language)
        url = url.replace("{page}", "1")
        return appendApiKeyQuery(url)
    }

    private fun buildContentUrl(article: NewsArticle): String {
        var url = config.contentUrlTemplate
        url = url.replace("{id}", article.id.urlEncoded())
        url = url.replace("{url}", article.url.urlEncoded())
        return appendApiKeyQuery(url)
    }

    private fun appendApiKeyQuery(rawUrl: String): String {
        if (config.apiKeyQueryName.isBlank() || config.apiKeyValue.isBlank()) {
            return rawUrl
        }
        val parsed = rawUrl.toHttpUrlOrNull() ?: return rawUrl
        return parsed.newBuilder()
            .addQueryParameter(config.apiKeyQueryName, config.apiKeyValue)
            .build()
            .toString()
    }

    private fun readText(node: JsonNode, path: String): String? {
        if (path.isBlank()) return null
        val target = findNodeByPath(node, path) ?: return null
        if (target.isNull || target.isMissingNode) return null
        return target.asText()
    }

    private fun findNodeByPath(root: JsonNode, path: String): JsonNode? {
        if (path.isBlank()) return null
        var current: JsonNode = root
        path.split(".")
            .filter { it.isNotBlank() }
            .forEach { rawSegment ->
                val segment = rawSegment.trim()
                if (segment.endsWith("]")) {
                    val startIndex = segment.indexOf('[')
                    if (startIndex <= 0 || !segment.endsWith("]")) {
                        return null
                    }
                    val field = segment.substring(0, startIndex)
                    val indexRaw = segment.substring(startIndex + 1, segment.length - 1)
                    val arrayIndex = indexRaw.toIntOrNull() ?: return null
                    val arrayNode = current.get(field) ?: return null
                    current = arrayNode.get(arrayIndex) ?: return null
                } else {
                    current = current.get(segment) ?: return null
                }
            }
        return current
    }

    private fun isMockFeedEnabled(): Boolean =
        config.feedUrlTemplate.trim().startsWith(MOCK_FEED_PREFIX, ignoreCase = true)

    private fun buildMockArticles(keyword: String, language: String): List<NewsArticle> {
        val now = System.currentTimeMillis()
        val all = listOf(
            NewsArticle(
                id = "mock-cn-interval",
                title = "配速训练：3 分钟快跑 + 2 分钟慢跑更容易坚持",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 15 * 60_000L,
                url = "https://example.com/runmate/mock/interval",
                language = "zh",
                description = "间歇训练的入门实践",
                fullText = "来源：RunMate Mock。今天的训练建议是采用三分钟快跑配合两分钟慢跑的间歇方案。全程先热身十分钟，再进入四到六组间歇。每组快跑阶段保持可以完整说短句但有明显吃力的强度。慢跑恢复阶段不要停下，让心率逐步回落。训练结束后进行五分钟放松跑和腿后侧拉伸。这样的结构能在控制疲劳的同时提升心肺能力，适合工作日的短时训练。"
            ),
            NewsArticle(
                id = "mock-cn-hydration",
                title = "长距离跑补水策略：20 分钟小口补给更稳",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 45 * 60_000L,
                url = "https://example.com/runmate/mock/hydration",
                language = "zh",
                description = "边跑边补给节奏建议",
                fullText = "来源：RunMate Mock。长距离训练中建议每二十分钟进行一次小口补水。天气炎热或湿度较高时可以适当提前补给。补水量以不出现胃部晃动感为准，同时观察口干和出汗情况。若训练超过一小时，可考虑补充少量电解质，避免后段抽筋风险。跑后半小时内完成碳水和蛋白的恢复餐，有助于降低第二天疲劳。"
            ),
            NewsArticle(
                id = "mock-cn-recovery",
                title = "恢复跑要慢：把强度降到对话配速",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 90 * 60_000L,
                url = "https://example.com/runmate/mock/recovery",
                language = "zh",
                description = "恢复跑常见误区",
                fullText = "来源：RunMate Mock。恢复跑的目标是促进循环而不是再次刺激强度。建议将速度控制在可以连续对话的区间，呼吸平稳，步幅自然。若前一日做了高强度训练，恢复跑时长控制在二十到四十分钟更合适。训练结束后优先睡眠，再配合轻量拉伸和泡沫轴放松。长期坚持恢复跑可以显著降低伤病概率。"
            ),
            NewsArticle(
                id = "mock-en-tempo",
                title = "Tempo sessions improve your lactate tolerance",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 30 * 60_000L,
                url = "https://example.com/runmate/mock/tempo",
                language = "en",
                description = "A practical tempo workout",
                fullText = "Source RunMate Mock. A tempo session is a sustained effort slightly below race intensity. Begin with a ten minute warm up, then run twenty minutes at controlled discomfort. Keep your breathing strong but stable and avoid surging in the first half. Cool down for eight minutes at easy pace. Repeat this workout once a week to build threshold endurance without excessive fatigue."
            ),
            NewsArticle(
                id = "mock-en-form",
                title = "Small cadence gains can reduce impact load",
                sourceName = "RunMate Mock",
                publishedAtEpochMs = now - 70 * 60_000L,
                url = "https://example.com/runmate/mock/form",
                language = "en",
                description = "Form cue for easy runs",
                fullText = "Source RunMate Mock. Increasing cadence by a small margin can reduce overstriding and impact forces. Start by adding two to four steps per minute while keeping effort unchanged. Focus on quick light steps and relaxed shoulders. Test this adjustment during easy runs first, then keep only what feels natural. The goal is smoother mechanics, not forcing a new style."
            )
        )
        val normalizedLanguage = language.trim().lowercase()
        val languageMatched = if (normalizedLanguage.startsWith("en")) {
            all.filter { it.language?.startsWith("en", ignoreCase = true) == true }
        } else {
            all.filter { it.language?.startsWith("zh", ignoreCase = true) == true }
        }
        val fallbackPool = languageMatched.ifEmpty { all }
        val normalizedKeyword = keyword.trim().lowercase()
        if (normalizedKeyword.isBlank()) return fallbackPool
        val keywordMatched = fallbackPool.filter { article ->
            val haystack = listOf(article.title, article.description, article.fullText)
                .joinToString(separator = " ")
                .lowercase()
            haystack.contains(normalizedKeyword)
        }
        if (keywordMatched.isNotEmpty()) return keywordMatched
        return fallbackPool.sortedBy {
            val title = it.title.lowercase()
            abs(title.length - normalizedKeyword.length)
        }
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
