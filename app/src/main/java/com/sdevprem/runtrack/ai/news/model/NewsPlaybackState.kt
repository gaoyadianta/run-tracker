package com.sdevprem.runtrack.ai.news.model

enum class NewsPlaybackStatus {
    IDLE,
    FETCHING,
    RUNNING,
    PAUSED,
    INTERRUPTED,
    NO_CONTENT,
    STOPPED,
    ERROR
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
    val message: String? = null
)
