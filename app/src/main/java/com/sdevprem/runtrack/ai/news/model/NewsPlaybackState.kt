package com.sdevprem.runtrack.ai.news.model

enum class NewsPlaybackStatus {
    IDLE,
    PREPARING,
    FETCHING,
    RUNNING,
    PAUSED,
    INTERRUPTED,
    NO_CONTENT,
    STOPPED,
    CONFIGURATION_ERROR,
    OFFLINE,
    RATE_LIMITED,
    ERROR
}

enum class NewsPauseReason {
    USER,
    COMPANION,
    AUDIO_FOCUS_TRANSIENT,
    AUDIO_FOCUS_PERMANENT
}

data class NewsPlaybackState(
    val status: NewsPlaybackStatus = NewsPlaybackStatus.IDLE,
    val keyword: String? = null,
    val language: String = "zh",
    val currentTitle: String? = null,
    val currentSource: String? = null,
    val currentPublishedAtEpochMs: Long? = null,
    val currentArticleUrl: String? = null,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val message: String? = null,
    val pauseReason: NewsPauseReason? = null
)
