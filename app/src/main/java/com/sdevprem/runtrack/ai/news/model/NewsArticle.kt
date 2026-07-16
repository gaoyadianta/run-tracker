package com.sdevprem.runtrack.ai.news.model

data class NewsArticle(
    val id: String,
    val title: String,
    val sourceName: String,
    val publishedAtEpochMs: Long?,
    val url: String,
    val language: String? = null,
    val description: String? = null,
    val contentSnippet: String? = null
)
