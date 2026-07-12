package com.sdevprem.runtrack.ai.news

import com.sdevprem.runtrack.ai.news.model.NewsVoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsVoiceCommandParserTest {
    private val parser = NewsVoiceCommandParser()

    @Test
    fun `parse pause command in chinese`() {
        val command = parser.parse("暂停新闻")
        assertEquals(NewsVoiceCommand.Pause, command)
    }

    @Test
    fun `parse start command with keyword`() {
        val command = parser.parse("开始播报人工智能新闻")
        assertTrue(command is NewsVoiceCommand.Start)
        val start = command as NewsVoiceCommand.Start
        assertEquals("人工智能", start.keyword)
        assertEquals("zh", start.language)
    }

    @Test
    fun `parse change keyword in english`() {
        val command = parser.parse("switch to semiconductor news")
        assertTrue(command is NewsVoiceCommand.ChangeKeyword)
        val change = command as NewsVoiceCommand.ChangeKeyword
        assertEquals("semiconductor", change.keyword)
        assertEquals("en", change.language)
    }
}
