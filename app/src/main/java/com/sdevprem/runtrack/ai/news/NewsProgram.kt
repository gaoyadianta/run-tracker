package com.sdevprem.runtrack.ai.news

import android.media.AudioManager
import com.sdevprem.runtrack.ai.audio.AudioFocusCoordinator
import com.sdevprem.runtrack.ai.news.config.NewsProgramConfig
import com.sdevprem.runtrack.ai.news.model.NewsArticle
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackState
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackStatus
import com.sdevprem.runtrack.ai.news.model.RunSessionNewsHistoryItem
import com.sdevprem.runtrack.ai.news.provider.ConfigurableNewsProvider
import com.sdevprem.runtrack.ai.news.tts.LocalTtsChannel
import com.sdevprem.runtrack.di.MainDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class NewsProgram @Inject constructor(
    private val config: NewsProgramConfig,
    private val newsProvider: ConfigurableNewsProvider,
    private val speechChannel: LocalTtsChannel,
    private val audioFocusCoordinator: AudioFocusCoordinator,
    @MainDispatcher mainDispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val _playbackState = kotlinx.coroutines.flow.MutableStateFlow(NewsPlaybackState())
    val playbackState: kotlinx.coroutines.flow.StateFlow<NewsPlaybackState> = _playbackState

    private var currentArticles: List<NewsArticle> = emptyList()
    private var currentArticleIndex = 0
    private var currentSentences: List<String> = emptyList()
    private var currentSentenceIndex = 0
    private var activeSentenceIndex: Int? = null
    private var playbackJob: Job? = null
    private var noContentRetryJob: Job? = null
    private var waitingUtterance: CompletableDeferred<Boolean>? = null
    private var isCompanionInterrupted = false

    private val sessionHistory = mutableListOf<RunSessionNewsHistoryItem>()
    private val playedArticleKeys = mutableSetOf<String>()

    init {
        audioFocusCoordinator.setNewsFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    scope.launch {
                        pauseByAudioFocusLossIfNeeded()
                    }
                }
                else -> Unit
            }
        }
        scope.launch {
            speechChannel.utteranceDone.collect { utteranceId ->
                waitingUtterance?.complete(true)
                Timber.v("news utterance done: $utteranceId")
            }
        }
        scope.launch {
            speechChannel.utteranceError.collect { utteranceId ->
                waitingUtterance?.complete(false)
                Timber.w("news utterance error: $utteranceId")
            }
        }
    }

    fun start(keyword: String?, language: String) {
        cancelNoContentRetry()
        val resolvedKeyword = keyword?.trim().takeUnless { it.isNullOrBlank() }
            ?: _playbackState.value.keyword
            ?: config.defaultKeyword.takeIf { it.isNotBlank() }
            ?: "科技"
        val resolvedLanguage = language.ifBlank { config.defaultLanguage }

        if (!config.enabled) {
            setError("新闻节目未启用")
            return
        }
        if (!config.fullTextAuthorized) {
            setError("新闻全文播报未授权，无法开启")
            return
        }
        if (!config.isProviderConfigured()) {
            setError("新闻服务未配置")
            return
        }

        stopPlayback(clearState = false, keepMessage = false)
        currentArticles = emptyList()
        currentArticleIndex = 0
        currentSentences = emptyList()
        currentSentenceIndex = 0
        activeSentenceIndex = null
        isCompanionInterrupted = false

        _playbackState.value = _playbackState.value.copy(
            status = NewsPlaybackStatus.FETCHING,
            keyword = resolvedKeyword,
            language = resolvedLanguage,
            message = null,
            currentTitle = null,
            currentSource = null,
            currentPublishedAtEpochMs = null,
            currentArticleUrl = null,
            currentSentenceIndex = 0,
            totalSentences = 0
        )

        playbackJob = scope.launch {
            val feedResult = newsProvider.fetchFeed(resolvedKeyword, resolvedLanguage)
            val articles = feedResult.getOrElse { error ->
                setError("获取新闻列表失败：${error.message ?: "未知错误"}")
                return@launch
            }
            if (articles.isEmpty()) {
                enterNoContent("暂无相关新闻")
                return@launch
            }
            currentArticles = articles
            currentArticleIndex = 0
            currentSentenceIndex = 0
            loadArticleAndStartPlayback()
        }
    }

    fun pause() {
        cancelNoContentRetry()
        val status = _playbackState.value.status
        if (status != NewsPlaybackStatus.RUNNING && status != NewsPlaybackStatus.FETCHING) {
            return
        }
        stopPlayback(clearState = false, keepMessage = true)
        _playbackState.value = _playbackState.value.copy(
            status = NewsPlaybackStatus.PAUSED,
            message = null
        )
    }

    fun resume() {
        cancelNoContentRetry()
        val status = _playbackState.value.status
        if (status != NewsPlaybackStatus.PAUSED && status != NewsPlaybackStatus.INTERRUPTED && status != NewsPlaybackStatus.NO_CONTENT && status != NewsPlaybackStatus.ERROR && status != NewsPlaybackStatus.STOPPED) {
            return
        }

        if (currentArticles.isEmpty()) {
            start(keyword = _playbackState.value.keyword, language = _playbackState.value.language)
            return
        }

        if (currentSentences.isNotEmpty()) {
            startSentencePlayback()
            return
        }

        playbackJob = scope.launch {
            loadArticleAndStartPlayback()
        }
    }

    fun skip() {
        cancelNoContentRetry()
        if (currentArticles.isEmpty()) return
        stopPlayback(clearState = false, keepMessage = true)
        currentArticleIndex += 1
        currentSentenceIndex = 0
        activeSentenceIndex = null

        if (currentArticleIndex >= currentArticles.size) {
            enterNoContent("已播完当前列表")
            return
        }

        playbackJob = scope.launch {
            loadArticleAndStartPlayback()
        }
    }

    fun stop() {
        cancelNoContentRetry()
        stopPlayback(clearState = true, keepMessage = false)
    }

    fun onCompanionInterruptStart() {
        val status = _playbackState.value.status
        if (status != NewsPlaybackStatus.RUNNING) return
        isCompanionInterrupted = true
        val resumeIndex = activeSentenceIndex?.let { it + 1 } ?: currentSentenceIndex
        currentSentenceIndex = max(resumeIndex, currentSentenceIndex)
        stopPlayback(clearState = false, keepMessage = true)
        _playbackState.value = _playbackState.value.copy(
            status = NewsPlaybackStatus.INTERRUPTED,
            currentSentenceIndex = currentSentenceIndex.coerceAtMost(_playbackState.value.totalSentences),
            message = "陪跑插播中，新闻将自动继续"
        )
    }

    fun onCompanionInterruptEnd() {
        if (!isCompanionInterrupted) return
        isCompanionInterrupted = false
        if (_playbackState.value.status == NewsPlaybackStatus.INTERRUPTED) {
            resume()
        }
    }

    fun clearSessionHistory() {
        cancelNoContentRetry()
        sessionHistory.clear()
        playedArticleKeys.clear()
    }

    fun consumeSessionHistory(): List<RunSessionNewsHistoryItem> {
        val snapshot = sessionHistory.toList()
        sessionHistory.clear()
        playedArticleKeys.clear()
        return snapshot
    }

    fun destroy() {
        cancelNoContentRetry()
        stopPlayback(clearState = true, keepMessage = false)
        audioFocusCoordinator.setNewsFocusChangeListener(null)
        audioFocusCoordinator.abandonNewsFocus()
        speechChannel.shutdown()
        scope.cancel()
    }

    private suspend fun loadArticleAndStartPlayback() {
        while (currentArticleIndex < currentArticles.size) {
            val article = currentArticles[currentArticleIndex]
            _playbackState.value = _playbackState.value.copy(
                status = NewsPlaybackStatus.FETCHING,
                currentTitle = article.title,
                currentSource = article.sourceName,
                currentPublishedAtEpochMs = article.publishedAtEpochMs,
                currentArticleUrl = article.url,
                message = "获取内容中"
            )

            val contentResult = newsProvider.fetchContent(article)
            if (contentResult.isFailure) {
                Timber.w(contentResult.exceptionOrNull(), "Load article failed, skip: ${article.url}")
                currentArticleIndex += 1
                currentSentenceIndex = 0
                delay(100L)
                continue
            }
            val content = contentResult.getOrNull().orEmpty()

            val bodySentences = NewsTextUtils.splitToSentences(content)
            if (bodySentences.isEmpty()) {
                currentArticleIndex += 1
                currentSentenceIndex = 0
                continue
            }

            val intro = buildIntroSentence(article)
            currentSentences = listOf(intro) + bodySentences
            currentSentenceIndex = 0
            rememberArticleHistory(article)

            _playbackState.value = _playbackState.value.copy(
                status = NewsPlaybackStatus.RUNNING,
                currentTitle = article.title,
                currentSource = article.sourceName,
                currentPublishedAtEpochMs = article.publishedAtEpochMs,
                currentArticleUrl = article.url,
                currentSentenceIndex = 0,
                totalSentences = currentSentences.size,
                message = null
            )
            startSentencePlayback()
            return
        }

        enterNoContent("暂无可播报内容")
    }

    private fun startSentencePlayback() {
        stopPlayback(
            clearState = false,
            keepMessage = true,
            stopSpeech = false,
            abandonNewsFocus = false
        )
        playbackJob = scope.launch {
            val ready = speechChannel.awaitReady()
            if (!ready) {
                setError("本地 TTS 不可用")
                return@launch
            }
            val focusGranted = audioFocusCoordinator.requestNewsFocus()
            if (!focusGranted) {
                setError("无法获取音频焦点")
                return@launch
            }
            speechChannel.setLanguage(_playbackState.value.language)
            _playbackState.value = _playbackState.value.copy(
                status = NewsPlaybackStatus.RUNNING,
                message = null
            )

            while (currentArticleIndex < currentArticles.size) {
                if (currentSentenceIndex >= currentSentences.size) {
                    currentArticleIndex += 1
                    currentSentenceIndex = 0
                    currentSentences = emptyList()
                    loadArticleAndStartPlayback()
                    return@launch
                }

                val sentence = currentSentences[currentSentenceIndex]
                val utteranceId = "news_${currentArticleIndex}_${currentSentenceIndex}_${System.currentTimeMillis()}"
                activeSentenceIndex = currentSentenceIndex
                waitingUtterance = CompletableDeferred()

                val accepted = speechChannel.speak(sentence, utteranceId)
                if (!accepted) {
                    setError("新闻播报失败")
                    return@launch
                }

                val completed = waitUtteranceResult()
                waitingUtterance = null
                activeSentenceIndex = null

                if (!completed) {
                    if (_playbackState.value.status == NewsPlaybackStatus.PAUSED ||
                        _playbackState.value.status == NewsPlaybackStatus.INTERRUPTED ||
                        _playbackState.value.status == NewsPlaybackStatus.STOPPED ||
                        _playbackState.value.status == NewsPlaybackStatus.IDLE
                    ) {
                        return@launch
                    }
                    setError("新闻播报中断")
                    return@launch
                }

                currentSentenceIndex += 1
                _playbackState.value = _playbackState.value.copy(
                    status = NewsPlaybackStatus.RUNNING,
                    currentSentenceIndex = currentSentenceIndex.coerceAtMost(currentSentences.size),
                    totalSentences = currentSentences.size
                )
            }
        }
    }

    private suspend fun waitUtteranceResult(): Boolean {
        val waiter = waitingUtterance ?: return false
        val cancelSignal = CompletableDeferred<Boolean>()
        val job = scope.launch {
            while (!cancelSignal.isCompleted) {
                if (_playbackState.value.status != NewsPlaybackStatus.RUNNING) {
                    cancelSignal.complete(false)
                    return@launch
                }
                delay(80L)
            }
        }
        return try {
            select {
                waiter.onAwait { it }
                cancelSignal.onAwait { it }
            }
        } finally {
            job.cancel()
        }
    }

    private fun rememberArticleHistory(article: NewsArticle) {
        val key = article.id.ifBlank { article.url }
        if (!playedArticleKeys.add(key)) return
        sessionHistory += RunSessionNewsHistoryItem(
            title = article.title,
            source = article.sourceName,
            publishedAtEpochMs = article.publishedAtEpochMs,
            articleUrl = article.url,
            playedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun stopPlayback(
        clearState: Boolean,
        keepMessage: Boolean,
        stopSpeech: Boolean = true,
        abandonNewsFocus: Boolean = true
    ) {
        playbackJob?.cancel()
        playbackJob = null
        waitingUtterance?.complete(false)
        waitingUtterance = null
        activeSentenceIndex = null
        if (stopSpeech) {
            speechChannel.stop()
        }
        if (abandonNewsFocus) {
            audioFocusCoordinator.abandonNewsFocus()
        }

        if (!clearState) return
        currentArticles = emptyList()
        currentArticleIndex = 0
        currentSentences = emptyList()
        currentSentenceIndex = 0
        _playbackState.value = NewsPlaybackState(
            status = NewsPlaybackStatus.STOPPED,
            keyword = _playbackState.value.keyword,
            language = _playbackState.value.language,
            message = if (keepMessage) _playbackState.value.message else null
        )
    }

    private fun setError(message: String) {
        cancelNoContentRetry()
        stopPlayback(clearState = false, keepMessage = true)
        _playbackState.value = _playbackState.value.copy(
            status = NewsPlaybackStatus.ERROR,
            message = message
        )
    }

    private fun enterNoContent(message: String) {
        _playbackState.value = _playbackState.value.copy(
            status = NewsPlaybackStatus.NO_CONTENT,
            message = buildNoContentMessage(message),
            currentSentenceIndex = 0,
            totalSentences = 0
        )
        scheduleNoContentRetry()
    }

    private fun buildNoContentMessage(message: String): String {
        val retryMinutes = (config.noContentRetryIntervalMs / 60_000L).coerceAtLeast(1L)
        return "$message，${retryMinutes}分钟后自动重试"
    }

    private fun scheduleNoContentRetry() {
        cancelNoContentRetry()
        val retryDelayMs = config.noContentRetryIntervalMs
        noContentRetryJob = scope.launch {
            delay(retryDelayMs)
            if (_playbackState.value.status != NewsPlaybackStatus.NO_CONTENT) return@launch
            start(
                keyword = _playbackState.value.keyword,
                language = _playbackState.value.language
            )
        }
    }

    private fun cancelNoContentRetry() {
        noContentRetryJob?.cancel()
        noContentRetryJob = null
    }

    private fun pauseByAudioFocusLossIfNeeded() {
        val status = _playbackState.value.status
        if (status != NewsPlaybackStatus.RUNNING && status != NewsPlaybackStatus.FETCHING) {
            return
        }
        stopPlayback(clearState = false, keepMessage = true)
        _playbackState.value = _playbackState.value.copy(
            status = NewsPlaybackStatus.PAUSED,
            message = "音频焦点被占用，已暂停新闻播报"
        )
    }

    private fun buildIntroSentence(article: NewsArticle): String {
        val published = NewsTextUtils.formatPublishedTime(article.publishedAtEpochMs)
        return "来源：${article.sourceName}，发布时间：$published。标题：${article.title}。"
    }
}
