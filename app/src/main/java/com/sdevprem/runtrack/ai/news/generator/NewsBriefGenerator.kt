package com.sdevprem.runtrack.ai.news.generator

import com.sdevprem.runtrack.ai.bailian.BailianLlmClient
import com.sdevprem.runtrack.ai.config.BailianConfig
import com.sdevprem.runtrack.ai.news.NewsTextUtils
import com.sdevprem.runtrack.ai.news.model.NewsArticle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

interface NewsBriefGenerator {
    suspend fun generate(article: NewsArticle, language: String): String
}

@Singleton
class BailianNewsBriefGenerator @Inject constructor(
    private val config: BailianConfig,
    private val llmClient: BailianLlmClient
) : NewsBriefGenerator {
    override suspend fun generate(article: NewsArticle, language: String): String {
        val fallback = fallbackBrief(article, language)
        if (!config.isConfigured()) return fallback

        return runCatching {
            val output = StringBuilder()
            withTimeout(20_000L) {
                llmClient.streamChatCompletion(
                    messages = listOf(
                        mapOf(
                            "role" to "system",
                            "content" to systemPrompt(language)
                        ),
                        mapOf(
                            "role" to "user",
                            "content" to buildInput(article)
                        )
                    )
                ).collect { chunk -> output.append(chunk) }
            }
            normalizeGeneratedBrief(output.toString(), article, language)
                ?: fallback
        }.getOrDefault(fallback)
    }

    private fun systemPrompt(language: String): String = if (language.startsWith("en", ignoreCase = true)) {
        "Rewrite only the supplied facts as a natural 60-100 word running-news brief. " +
            "Do not add facts, opinions, predictions, or markdown. Keep the source and headline."
    } else {
        "仅使用输入中提供的事实，改写成适合跑步时收听的100到180字中文新闻简报。" +
            "不得补充事实、观点或预测，不要使用Markdown，并保留来源和标题。"
    }

    private fun buildInput(article: NewsArticle): String = buildString {
        appendLine("source: ${article.sourceName}")
        appendLine("headline: ${article.title}")
        appendLine("description: ${NewsTextUtils.cleanSnippet(article.description)}")
        append("snippet: ${NewsTextUtils.cleanSnippet(article.contentSnippet)}")
    }

    private fun normalizeGeneratedBrief(
        text: String,
        article: NewsArticle,
        language: String
    ): String? {
        val cleaned = NewsTextUtils.cleanText(text)
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (cleaned.isBlank()) return null
        if (!cleaned.contains(article.sourceName, ignoreCase = true) ||
            !cleaned.contains(article.title, ignoreCase = true)
        ) return null

        return if (language.startsWith("en", ignoreCase = true)) {
            val words = cleaned.split(Regex("\\s+")).filter(String::isNotBlank)
            cleaned.takeIf { words.size in 60..100 }
        } else {
            val characterCount = cleaned.count { !it.isWhitespace() }
            cleaned.takeIf { characterCount in 100..180 }
        }
    }

    private fun fallbackBrief(article: NewsArticle, language: String): String {
        val detail = NewsTextUtils.cleanSnippet(article.description)
            .ifBlank { NewsTextUtils.cleanSnippet(article.contentSnippet) }
        return if (language.startsWith("en", ignoreCase = true)) {
            listOf("From ${article.sourceName}.", article.title, detail)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .split(Regex("\\s+"))
                .take(100)
                .joinToString(" ")
        } else {
            listOf("来源：${article.sourceName}。", "标题：${article.title}。", detail)
                .filter { it.isNotBlank() }
                .joinToString("")
                .take(180)
        }
    }
}
