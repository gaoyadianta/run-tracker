package com.sdevprem.runtrack.ai.news.provider

import com.sdevprem.runtrack.ai.news.model.NewsArticle

interface NewsProvider {
    suspend fun fetchFeed(keyword: String, language: String): Result<List<NewsArticle>>
    suspend fun fetchContent(article: NewsArticle): Result<String>
}
