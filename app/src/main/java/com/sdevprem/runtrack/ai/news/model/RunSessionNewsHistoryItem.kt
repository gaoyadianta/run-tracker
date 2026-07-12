package com.sdevprem.runtrack.ai.news.model

data class RunSessionNewsHistoryItem(
    val title: String,
    val source: String,
    val publishedAtEpochMs: Long?,
    val articleUrl: String,
    val playedAtEpochMs: Long
)
