package com.sdevprem.runtrack.ai.news.config

data class NewsProgramSettings(
    val enabled: Boolean,
    val allowVoiceStart: Boolean,
    val fullTextAuthorized: Boolean,
    val autoStartOnAppOpen: Boolean,
    val defaultKeyword: String,
    val defaultLanguage: String,
    val noContentRetryMinutes: Int,
    val feedUrlTemplate: String,
    val contentUrlTemplate: String,
    val apiKeyHeaderName: String,
    val apiKeyQueryName: String,
    val apiKeyValue: String,
    val feedItemsPath: String,
    val feedIdPath: String,
    val feedTitlePath: String,
    val feedSourcePath: String,
    val feedPublishedAtPath: String,
    val feedUrlPath: String,
    val feedLanguagePath: String,
    val feedDescriptionPath: String,
    val feedContentPath: String,
    val contentTextPath: String
)
