package com.sdevprem.runtrack.ai.news

import com.sdevprem.runtrack.ai.audio.AudioFocusCoordinator
import com.sdevprem.runtrack.ai.news.config.NewsProgramConfig
import com.sdevprem.runtrack.ai.news.config.NewsProgramSettings
import com.sdevprem.runtrack.ai.news.config.NewsProviderMode
import com.sdevprem.runtrack.ai.news.model.NewsBrief
import com.sdevprem.runtrack.ai.news.model.NewsBriefBatch
import com.sdevprem.runtrack.ai.news.model.NewsBriefRequest
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackStatus
import com.sdevprem.runtrack.ai.news.provider.NewsProvider
import com.sdevprem.runtrack.ai.news.tts.SpeechChannel
import com.sdevprem.runtrack.ai.news.tts.SpeechEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class NewsProgramTest {
    @Test
    fun `ignores stale utterance completion`() = runTest {
        val fixture = fixture(UnconfinedTestDispatcher(testScheduler))
        fixture.program.start("科技", "zh")
        val activeId = fixture.speech.lastUtteranceId
        assertNotNull(activeId)

        fixture.speech.complete("stale-id")
        assertEquals(0, fixture.program.playbackState.value.currentSentenceIndex)

        fixture.speech.complete(activeId!!)
        assertEquals(1, fixture.program.playbackState.value.currentSentenceIndex)
        fixture.program.destroy()
    }

    @Test
    fun `companion interruption restarts current sentence`() = runTest {
        val fixture = fixture(UnconfinedTestDispatcher(testScheduler))
        fixture.program.start("科技", "zh")
        val interruptedText = fixture.speech.lastText

        fixture.program.onCompanionInterruptStart()
        assertEquals(NewsPlaybackStatus.INTERRUPTED, fixture.program.playbackState.value.status)
        fixture.program.onCompanionInterruptEnd()

        assertEquals(interruptedText, fixture.speech.lastText)
        assertEquals(NewsPlaybackStatus.RUNNING, fixture.program.playbackState.value.status)
        fixture.program.destroy()
    }

    @Test
    fun `user pause is not resumed by companion end`() = runTest {
        val fixture = fixture(UnconfinedTestDispatcher(testScheduler))
        fixture.program.start("科技", "zh")

        fixture.program.pause()
        fixture.program.onCompanionInterruptEnd()

        assertEquals(NewsPlaybackStatus.PAUSED, fixture.program.playbackState.value.status)
        fixture.program.destroy()
    }

    @Test
    fun `history is recorded only after first sentence completes`() = runTest {
        val fixture = fixture(UnconfinedTestDispatcher(testScheduler))
        fixture.program.start("科技", "zh")
        assertTrue(fixture.program.sessionHistorySnapshot().isEmpty())

        fixture.speech.complete(fixture.speech.lastUtteranceId!!)

        assertEquals(1, fixture.program.sessionHistorySnapshot().size)
        assertEquals(false, fixture.program.sessionHistorySnapshot().single().completed)
        fixture.program.destroy()
    }

    private fun fixture(dispatcher: TestDispatcher): Fixture {
        val config = mock(NewsProgramConfig::class.java)
        `when`(config.enabled).thenReturn(true)
        `when`(config.isProviderConfigured()).thenReturn(true)
        `when`(config.defaultKeyword).thenReturn("科技")
        `when`(config.defaultLanguage).thenReturn("zh")
        `when`(config.noContentRetryIntervalMs).thenReturn(60_000L)
        `when`(config.settingsUpdates).thenReturn(
            MutableStateFlow(
                NewsProgramSettings(
                    enabled = true,
                    allowVoiceStart = true,
                    autoStartOnAppOpen = false,
                    defaultKeyword = "科技",
                    defaultLanguage = "zh",
                    noContentRetryMinutes = 1,
                    providerMode = NewsProviderMode.MOCK
                )
            )
        )

        val audioFocus = mock(AudioFocusCoordinator::class.java)
        `when`(audioFocus.requestNewsFocus()).thenReturn(true)
        val speech = FakeSpeechChannel()
        val provider = FakeNewsProvider()
        val program = NewsProgram(
            config = config,
            newsProvider = provider,
            speechChannel = speech,
            audioFocusCoordinator = audioFocus,
            mainDispatcher = dispatcher
        )
        return Fixture(program, speech)
    }

    private data class Fixture(
        val program: NewsProgram,
        val speech: FakeSpeechChannel
    )

    private class FakeNewsProvider : NewsProvider {
        override suspend fun fetchBriefs(request: NewsBriefRequest): Result<NewsBriefBatch> = Result.success(
            NewsBriefBatch(
                briefs = listOf(
                    NewsBrief(
                        id = "brief-1",
                        title = "测试新闻",
                        sourceName = "测试来源",
                        publishedAtEpochMs = 1_700_000_000_000L,
                        articleUrl = "https://example.com/brief-1",
                        spokenText = "这是第一句。这是第二句。"
                    )
                )
            )
        )
    }

    private class FakeSpeechChannel : SpeechChannel {
        private val eventFlow = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
        override val events: Flow<SpeechEvent> = eventFlow
        var lastUtteranceId: String? = null
        var lastText: String? = null

        override suspend fun awaitReady(): Boolean = true
        override suspend fun setLanguage(language: String): Result<Unit> = Result.success(Unit)
        override fun speak(text: String, utteranceId: String): Boolean {
            lastText = text
            lastUtteranceId = utteranceId
            return true
        }

        override fun stop() = Unit
        override fun shutdown() = Unit
        fun complete(utteranceId: String) {
            eventFlow.tryEmit(SpeechEvent.Completed(utteranceId))
        }
    }
}
