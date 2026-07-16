package com.sdevprem.runtrack.ai.news

import com.sdevprem.runtrack.ai.bailian.BailianLlmClient
import com.sdevprem.runtrack.ai.config.BailianConfig
import com.sdevprem.runtrack.ai.news.generator.BailianNewsBriefGenerator
import com.sdevprem.runtrack.ai.news.model.NewsArticle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class NewsBriefGeneratorTest {
    @Test
    fun `falls back to deterministic brief when Bailian is unavailable`() = runTest {
        val config = mock(BailianConfig::class.java)
        `when`(config.isConfigured()).thenReturn(false)
        val generator = BailianNewsBriefGenerator(
            config = config,
            llmClient = mock(BailianLlmClient::class.java)
        )

        val brief = generator.generate(
            article = NewsArticle(
                id = "1",
                title = "跑步助手发布更新",
                sourceName = "测试来源",
                publishedAtEpochMs = null,
                url = "https://example.com/1",
                description = "本次更新提升了播报稳定性。",
                contentSnippet = "更多内容 [+123 chars]"
            ),
            language = "zh"
        )

        assertTrue(brief.contains("测试来源"))
        assertTrue(brief.contains("跑步助手发布更新"))
        assertFalse(brief.contains("chars"))
        assertTrue(brief.length <= 180)
    }

    @Test
    fun `falls back when generated text does not preserve supplied facts`() = runTest {
        val config = mock(BailianConfig::class.java)
        val llmClient = mock(BailianLlmClient::class.java)
        `when`(config.isConfigured()).thenReturn(true)
        `when`(
            llmClient.streamChatCompletion(
                org.mockito.ArgumentMatchers.anyList<Map<String, String>>()
            )
        )
            .thenReturn(flowOf("这是一段没有保留来源和标题、并且补充了未知事实的输出。"))
        val generator = BailianNewsBriefGenerator(config, llmClient)

        val brief = generator.generate(sampleArticle(), "zh")

        assertTrue(brief.contains("测试来源"))
        assertTrue(brief.contains("跑步助手发布更新"))
        assertFalse(brief.contains("未知事实"))
    }

    private fun sampleArticle() = NewsArticle(
        id = "1",
        title = "跑步助手发布更新",
        sourceName = "测试来源",
        publishedAtEpochMs = null,
        url = "https://example.com/1",
        description = "本次更新提升了播报稳定性。",
        contentSnippet = "更多内容 [+123 chars]"
    )
}
