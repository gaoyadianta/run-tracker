package com.sdevprem.runtrack.ai.news

import android.media.AudioManager
import com.sdevprem.runtrack.ai.audio.AudioFocusCoordinator
import com.sdevprem.runtrack.ai.news.config.NewsProgramConfig
import com.sdevprem.runtrack.ai.news.model.NewsBrief
import com.sdevprem.runtrack.ai.news.model.NewsBriefRequest
import com.sdevprem.runtrack.ai.news.model.NewsFailure
import com.sdevprem.runtrack.ai.news.model.NewsPauseReason
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackState
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackStatus
import com.sdevprem.runtrack.ai.news.model.RunSessionNewsHistoryItem
import com.sdevprem.runtrack.ai.news.provider.NewsProvider
import com.sdevprem.runtrack.ai.news.tts.SpeechChannel
import com.sdevprem.runtrack.ai.news.tts.SpeechEvent
import com.sdevprem.runtrack.di.MainDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsProgram @Inject constructor(
    private val config: NewsProgramConfig,
    private val newsProvider: NewsProvider,
    private val speechChannel: SpeechChannel,
    private val audioFocusCoordinator: AudioFocusCoordinator,
    @MainDispatcher mainDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val MAX_NO_CONTENT_RETRIES = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val _playbackState = MutableStateFlow(configurationAwareIdleState())
    val playbackState: StateFlow<NewsPlaybackState> = _playbackState.asStateFlow()

    private var currentBriefs: List<NewsBrief> = emptyList()
    private var currentBriefIndex = 0
    private var currentSentences: List<String> = emptyList()
    private var currentSentenceIndex = 0
    private var activeUtteranceId: String? = null
    private var waitingUtterance: CompletableDeferred<Boolean>? = null
    private var fetchJob: Job? = null
    private var playbackJob: Job? = null
    private var noContentRetryJob: Job? = null
    private var noContentRetryCount = 0

    private val sessionHistory = mutableListOf<RunSessionNewsHistoryItem>()
    private val historyIndexByBriefId = mutableMapOf<String, Int>()
    private val playedBriefKeys = mutableSetOf<String>()

    init {
        audioFocusCoordinator.setNewsFocusChangeListener(::onAudioFocusChanged)
        scope.launch {
            config.settingsUpdates.collect { refreshIdleConfigurationState() }
        }
        scope.launch {
            speechChannel.events.collect(::onSpeechEvent)
        }
    }

    fun start(keyword: String?, language: String) {
        noContentRetryCount = 0
        startInternal(keyword = keyword, language = language, resetQueue = true)
    }

    fun pause() {
        val status = _playbackState.value.status
        if (status !in setOf(
                NewsPlaybackStatus.PREPARING,
                NewsPlaybackStatus.FETCHING,
                NewsPlaybackStatus.RUNNING
            )
        ) return
        pauseForReason(NewsPauseReason.USER)
    }

    fun resume() {
        val state = _playbackState.value
        if (state.status !in setOf(
                NewsPlaybackStatus.PAUSED,
                NewsPlaybackStatus.INTERRUPTED,
                NewsPlaybackStatus.NO_CONTENT,
                NewsPlaybackStatus.STOPPED,
                NewsPlaybackStatus.OFFLINE,
                NewsPlaybackStatus.RATE_LIMITED,
                NewsPlaybackStatus.ERROR
            )
        ) return
        cancelNoContentRetry()
        _playbackState.value = state.copy(pauseReason = null, message = null)
        if (currentBriefs.isEmpty()) {
            startInternal(state.keyword, state.language, resetQueue = true)
        } else {
            startSentencePlayback()
        }
    }

    fun skip() {
        cancelNoContentRetry()
        if (currentBriefs.isEmpty()) return
        cancelActiveWork(stopSpeech = true, abandonFocus = true, cancelFetch = false)
        currentBriefIndex += 1
        currentSentenceIndex = 0
        currentSentences = emptyList()
        if (currentBriefIndex >= currentBriefs.size) {
            enterNoContent("已播完当前列表")
        } else {
            prepareCurrentBrief()
            startSentencePlayback()
        }
    }

    fun stop() {
        cancelNoContentRetry()
        cancelActiveWork(stopSpeech = true, abandonFocus = true, cancelFetch = true)
        clearQueue()
        _playbackState.value = NewsPlaybackState(
            status = NewsPlaybackStatus.STOPPED,
            keyword = _playbackState.value.keyword,
            language = _playbackState.value.language
        )
    }

    fun onCompanionInterruptStart() {
        if (_playbackState.value.status == NewsPlaybackStatus.RUNNING) {
            pauseForReason(NewsPauseReason.COMPANION)
        }
    }

    fun onCompanionInterruptEnd() {
        resumeIfAutomaticallyPaused(NewsPauseReason.COMPANION)
    }

    fun clearSessionHistory() {
        sessionHistory.clear()
        historyIndexByBriefId.clear()
        playedBriefKeys.clear()
    }

    fun sessionHistorySnapshot(): List<RunSessionNewsHistoryItem> = sessionHistory.toList()

    fun destroy() {
        cancelNoContentRetry()
        cancelActiveWork(stopSpeech = true, abandonFocus = true, cancelFetch = true)
        audioFocusCoordinator.setNewsFocusChangeListener(null)
        speechChannel.shutdown()
        scope.cancel()
    }

    private fun startInternal(keyword: String?, language: String, resetQueue: Boolean) {
        cancelNoContentRetry()
        val resolvedKeyword = keyword?.trim().takeUnless { it.isNullOrBlank() }
            ?: _playbackState.value.keyword
            ?: config.defaultKeyword.takeIf { it.isNotBlank() }
            ?: "科技"
        val resolvedLanguage = language.ifBlank { config.defaultLanguage }

        if (!config.enabled || !config.isProviderConfigured()) {
            setFailure(NewsFailure.NotConfigured)
            return
        }

        cancelActiveWork(stopSpeech = true, abandonFocus = true, cancelFetch = true)
        if (resetQueue) clearQueue()
        _playbackState.value = NewsPlaybackState(
            status = NewsPlaybackStatus.PREPARING,
            keyword = resolvedKeyword,
            language = resolvedLanguage,
            message = "正在获取并生成新闻简报"
        )

        fetchJob = scope.launch {
            _playbackState.value = _playbackState.value.copy(status = NewsPlaybackStatus.FETCHING)
            val result = newsProvider.fetchBriefs(
                NewsBriefRequest(
                    keyword = resolvedKeyword,
                    language = resolvedLanguage
                )
            )
            val batch = result.getOrElse { error ->
                val failure = error as? NewsFailure ?: NewsFailure.Provider("新闻简报生成失败", error)
                if (failure == NewsFailure.NoContent) {
                    enterNoContent("暂无可播报新闻")
                } else {
                    setFailure(failure)
                }
                return@launch
            }
            val freshBriefs = batch.briefs.filter { brief ->
                briefKeys(brief).none(playedBriefKeys::contains)
            }
            if (freshBriefs.isEmpty()) {
                enterNoContent("暂无新的可播报新闻")
                return@launch
            }
            noContentRetryCount = 0
            currentBriefs = freshBriefs
            currentBriefIndex = 0
            currentSentenceIndex = 0
            prepareCurrentBrief()
            startSentencePlayback()
        }
    }

    private fun configurationAwareIdleState(): NewsPlaybackState = when {
        !config.enabled -> NewsPlaybackState(
            status = NewsPlaybackStatus.CONFIGURATION_ERROR,
            message = "新闻功能已关闭，请在设置中启用"
        )
        !config.isProviderConfigured() -> NewsPlaybackState(
            status = NewsPlaybackStatus.CONFIGURATION_ERROR,
            message = "新闻服务未配置"
        )
        else -> NewsPlaybackState()
    }

    private fun refreshIdleConfigurationState() {
        if (_playbackState.value.status !in setOf(
                NewsPlaybackStatus.IDLE,
                NewsPlaybackStatus.STOPPED,
                NewsPlaybackStatus.CONFIGURATION_ERROR
            )
        ) return
        val next = configurationAwareIdleState()
        _playbackState.value = next.copy(
            keyword = _playbackState.value.keyword,
            language = _playbackState.value.language
        )
    }

    private fun prepareCurrentBrief() {
        val brief = currentBriefs.getOrNull(currentBriefIndex) ?: return
        val body = NewsTextUtils.splitToSentences(brief.spokenText)
        val alreadyIntroduced = brief.spokenText.contains(brief.title, ignoreCase = true) &&
            brief.spokenText.contains(brief.sourceName, ignoreCase = true)
        currentSentences = if (alreadyIntroduced) body else listOf(buildIntroSentence(brief)) + body
        currentSentenceIndex = currentSentenceIndex.coerceIn(0, currentSentences.lastIndex.coerceAtLeast(0))
        _playbackState.value = _playbackState.value.copy(
            status = NewsPlaybackStatus.PREPARING,
            currentTitle = brief.title,
            currentSource = brief.sourceName,
            currentPublishedAtEpochMs = brief.publishedAtEpochMs,
            currentArticleUrl = brief.articleUrl,
            currentSentenceIndex = currentSentenceIndex,
            totalSentences = currentSentences.size,
            message = "准备播报",
            pauseReason = null
        )
    }

    private fun startSentencePlayback() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            if (!speechChannel.awaitReady()) {
                setFailure(NewsFailure.TtsUnavailable)
                return@launch
            }
            val languageResult = speechChannel.setLanguage(_playbackState.value.language)
            if (languageResult.isFailure) {
                setFailure(languageResult.exceptionOrNull() as? NewsFailure ?: NewsFailure.TtsUnavailable)
                return@launch
            }
            if (!audioFocusCoordinator.requestNewsFocus()) {
                setFailure(NewsFailure.TtsUnavailable)
                return@launch
            }
            _playbackState.value = _playbackState.value.copy(
                status = NewsPlaybackStatus.RUNNING,
                message = null,
                pauseReason = null
            )

            while (currentBriefIndex < currentBriefs.size) {
                if (currentSentences.isEmpty()) prepareCurrentBrief()
                if (currentSentenceIndex >= currentSentences.size) {
                    markCurrentBriefCompleted()
                    currentBriefIndex += 1
                    currentSentenceIndex = 0
                    currentSentences = emptyList()
                    if (currentBriefIndex >= currentBriefs.size) {
                        enterNoContent("已播完当前列表")
                        return@launch
                    }
                    prepareCurrentBrief()
                    _playbackState.value = _playbackState.value.copy(status = NewsPlaybackStatus.RUNNING, message = null)
                    continue
                }

                val utteranceId = "news_${currentBriefIndex}_${currentSentenceIndex}_${System.currentTimeMillis()}"
                activeUtteranceId = utteranceId
                val waiter = CompletableDeferred<Boolean>()
                waitingUtterance = waiter
                if (!speechChannel.speak(currentSentences[currentSentenceIndex], utteranceId)) {
                    activeUtteranceId = null
                    waitingUtterance = null
                    setFailure(NewsFailure.TtsUnavailable)
                    return@launch
                }

                val completed = waiter.await()
                waitingUtterance = null
                activeUtteranceId = null
                if (!completed) {
                    if (_playbackState.value.status in setOf(
                            NewsPlaybackStatus.PAUSED,
                            NewsPlaybackStatus.INTERRUPTED,
                            NewsPlaybackStatus.STOPPED,
                            NewsPlaybackStatus.IDLE
                        )
                    ) return@launch
                    setFailure(NewsFailure.TtsUnavailable)
                    return@launch
                }

                rememberCurrentBriefStarted()
                currentSentenceIndex += 1
                _playbackState.value = _playbackState.value.copy(
                    status = NewsPlaybackStatus.RUNNING,
                    currentSentenceIndex = currentSentenceIndex.coerceAtMost(currentSentences.size),
                    totalSentences = currentSentences.size
                )
            }
        }
    }

    private fun pauseForReason(reason: NewsPauseReason) {
        cancelNoContentRetry()
        cancelActiveWork(stopSpeech = true, abandonFocus = true, cancelFetch = true)
        _playbackState.value = _playbackState.value.copy(
            status = if (reason == NewsPauseReason.COMPANION) {
                NewsPlaybackStatus.INTERRUPTED
            } else {
                NewsPlaybackStatus.PAUSED
            },
            pauseReason = reason,
            message = when (reason) {
                NewsPauseReason.USER -> "已由用户暂停"
                NewsPauseReason.COMPANION -> "陪跑插播中，结束后继续当前句"
                NewsPauseReason.AUDIO_FOCUS_TRANSIENT -> "音频被临时占用，恢复后继续"
                NewsPauseReason.AUDIO_FOCUS_PERMANENT -> "音频焦点丢失，请手动继续"
            }
        )
    }

    private fun resumeIfAutomaticallyPaused(reason: NewsPauseReason) {
        val state = _playbackState.value
        if (state.pauseReason != reason) return
        if (reason !in setOf(NewsPauseReason.COMPANION, NewsPauseReason.AUDIO_FOCUS_TRANSIENT)) return
        _playbackState.value = state.copy(pauseReason = null, message = null)
        if (currentBriefs.isEmpty()) {
            startInternal(state.keyword, state.language, resetQueue = true)
        } else {
            startSentencePlayback()
        }
    }

    private fun onAudioFocusChanged(change: Int) {
        scope.launch {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> resumeIfAutomaticallyPaused(NewsPauseReason.AUDIO_FOCUS_TRANSIENT)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    if (_playbackState.value.status == NewsPlaybackStatus.RUNNING) {
                        pauseForReason(NewsPauseReason.AUDIO_FOCUS_TRANSIENT)
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    if (_playbackState.value.status == NewsPlaybackStatus.RUNNING) {
                        pauseForReason(NewsPauseReason.AUDIO_FOCUS_PERMANENT)
                    }
                }
            }
        }
    }

    private fun onSpeechEvent(event: SpeechEvent) {
        if (event.utteranceId != activeUtteranceId) return
        waitingUtterance?.complete(event is SpeechEvent.Completed)
    }

    private fun rememberCurrentBriefStarted() {
        val brief = currentBriefs.getOrNull(currentBriefIndex) ?: return
        if (historyIndexByBriefId.containsKey(brief.id)) return
        playedBriefKeys += briefKeys(brief)
        val index = sessionHistory.size
        historyIndexByBriefId[brief.id] = index
        sessionHistory += RunSessionNewsHistoryItem(
            title = brief.title,
            source = brief.sourceName,
            publishedAtEpochMs = brief.publishedAtEpochMs,
            articleUrl = brief.articleUrl,
            playedAtEpochMs = System.currentTimeMillis(),
            briefText = brief.spokenText,
            completed = false
        )
    }

    private fun markCurrentBriefCompleted() {
        val brief = currentBriefs.getOrNull(currentBriefIndex) ?: return
        val index = historyIndexByBriefId[brief.id] ?: return
        sessionHistory[index] = sessionHistory[index].copy(completed = true)
    }

    private fun enterNoContent(message: String) {
        cancelActiveWork(stopSpeech = true, abandonFocus = true, cancelFetch = false)
        val canRetry = noContentRetryCount < MAX_NO_CONTENT_RETRIES
        _playbackState.value = _playbackState.value.copy(
            status = NewsPlaybackStatus.NO_CONTENT,
            message = if (canRetry) {
                "$message，${config.noContentRetryIntervalMs / 60_000L}分钟后自动重试"
            } else {
                "$message，请稍后手动重试"
            },
            currentSentenceIndex = 0,
            totalSentences = 0,
            pauseReason = null
        )
        if (canRetry) scheduleNoContentRetry()
    }

    private fun scheduleNoContentRetry() {
        cancelNoContentRetry()
        noContentRetryJob = scope.launch {
            delay(config.noContentRetryIntervalMs)
            if (_playbackState.value.status != NewsPlaybackStatus.NO_CONTENT) return@launch
            noContentRetryCount += 1
            startInternal(
                keyword = _playbackState.value.keyword,
                language = _playbackState.value.language,
                resetQueue = true
            )
        }
    }

    private fun setFailure(failure: NewsFailure) {
        cancelNoContentRetry()
        cancelActiveWork(stopSpeech = true, abandonFocus = true, cancelFetch = false)
        _playbackState.value = _playbackState.value.copy(
            status = when (failure) {
                NewsFailure.NotConfigured,
                NewsFailure.Unauthorized -> NewsPlaybackStatus.CONFIGURATION_ERROR
                NewsFailure.RateLimited -> NewsPlaybackStatus.RATE_LIMITED
                is NewsFailure.Network -> NewsPlaybackStatus.OFFLINE
                NewsFailure.NoContent -> NewsPlaybackStatus.NO_CONTENT
                else -> NewsPlaybackStatus.ERROR
            },
            message = failure.message,
            pauseReason = null
        )
    }

    private fun cancelActiveWork(
        stopSpeech: Boolean,
        abandonFocus: Boolean,
        cancelFetch: Boolean
    ) {
        playbackJob?.cancel()
        playbackJob = null
        if (cancelFetch) {
            fetchJob?.cancel()
            fetchJob = null
        }
        waitingUtterance?.complete(false)
        waitingUtterance = null
        activeUtteranceId = null
        if (stopSpeech) speechChannel.stop()
        if (abandonFocus) audioFocusCoordinator.abandonNewsFocus()
    }

    private fun cancelNoContentRetry() {
        noContentRetryJob?.cancel()
        noContentRetryJob = null
    }

    private fun clearQueue() {
        currentBriefs = emptyList()
        currentBriefIndex = 0
        currentSentences = emptyList()
        currentSentenceIndex = 0
    }

    private fun buildIntroSentence(brief: NewsBrief): String {
        val published = NewsTextUtils.formatPublishedTime(brief.publishedAtEpochMs)
        return "来源：${brief.sourceName}，发布时间：$published。标题：${brief.title}。"
    }

    private fun briefKeys(brief: NewsBrief): Set<String> = buildSet {
        brief.id.trim().takeIf { it.isNotBlank() }?.let { add("id:$it") }
        brief.articleUrl.substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
            .lowercase(Locale.ROOT)
            .takeIf { it.isNotBlank() }
            ?.let { add("url:$it") }
    }
}
