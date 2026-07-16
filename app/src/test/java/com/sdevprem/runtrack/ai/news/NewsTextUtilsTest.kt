package com.sdevprem.runtrack.ai.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NewsTextUtilsTest {
    @Test
    fun `clean snippet removes html entities and NewsAPI truncation marker`() {
        val cleaned = NewsTextUtils.cleanSnippet(
            "<p>人工智能&amp;跑步助手</p> [+1234 chars]"
        )

        assertEquals("人工智能&跑步助手", cleaned)
        assertFalse(cleaned.contains("chars"))
    }

    @Test
    fun `split sentences keeps Chinese punctuation boundaries`() {
        assertEquals(
            listOf("第一句。", "第二句！", "第三句？"),
            NewsTextUtils.splitToSentences("第一句。第二句！第三句？")
        )
    }
}
