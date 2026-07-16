package com.sdevprem.runtrack.ai.news.provider

import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsHttpClientTest {
    @Test
    fun `returns status and response body from provider`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
        )
        server.start()

        try {
            val response = NewsHttpClient().execute(
                Request.Builder().url(server.url("/v1/news/briefs")).build()
            )

            assertEquals(200, response.code)
            assertEquals("{\"status\":\"ok\"}", response.body)
            assertEquals("/v1/news/briefs", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }
}
