package com.sdevprem.runtrack.ai.news.provider

import com.sdevprem.runtrack.ai.news.model.NewsBriefRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsApiRequestFactoryTest {
    @Test
    fun `uses header authentication and encodes query without exposing key`() {
        val request = NewsApiRequestFactory.create(
            request = NewsBriefRequest(
                keyword = "人工智能 跑步",
                language = "zh",
                limit = 5
            ),
            apiKey = "debug-secret-key",
            oldestAllowedEpochMs = 1_700_000_000_000L
        )

        assertEquals("debug-secret-key", request.header("X-Api-Key"))
        assertEquals("人工智能 跑步", request.url.queryParameter("q"))
        assertEquals("zh", request.url.queryParameter("language"))
        assertEquals("10", request.url.queryParameter("pageSize"))
        assertFalse(request.url.toString().contains("debug-secret-key"))
        assertFalse(request.url.queryParameterNames.contains("apiKey"))
        assertTrue(request.url.toString().startsWith("https://newsapi.org/v2/everything"))
    }
}
