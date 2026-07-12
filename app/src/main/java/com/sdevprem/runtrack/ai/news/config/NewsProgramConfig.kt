package com.sdevprem.runtrack.ai.news.config

import com.sdevprem.runtrack.data.repository.NewsProgramSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsProgramConfig @Inject constructor(
    private val settingsRepository: NewsProgramSettingsRepository
) {
    private val settings: NewsProgramSettings
        get() = settingsRepository.settings.value

    val enabled: Boolean
        get() = settings.enabled

    val allowVoiceStart: Boolean
        get() = settings.allowVoiceStart

    val fullTextAuthorized: Boolean
        get() = settings.fullTextAuthorized

    val autoStartOnAppOpen: Boolean
        get() = settings.autoStartOnAppOpen

    val defaultKeyword: String
        get() = settings.defaultKeyword

    val defaultLanguage: String
        get() = settings.defaultLanguage.ifBlank { "zh" }

    val noContentRetryIntervalMs: Long
        get() = settings.noContentRetryMinutes.toLong().coerceAtLeast(1L) * 60_000L

    val feedUrlTemplate: String
        get() = settings.feedUrlTemplate

    val contentUrlTemplate: String
        get() = settings.contentUrlTemplate

    val apiKeyHeaderName: String
        get() = settings.apiKeyHeaderName

    val apiKeyQueryName: String
        get() = settings.apiKeyQueryName

    val apiKeyValue: String
        get() = settings.apiKeyValue

    val feedItemsPath: String
        get() = settings.feedItemsPath.ifBlank { "articles" }

    val feedIdPath: String
        get() = settings.feedIdPath.ifBlank { "url" }

    val feedTitlePath: String
        get() = settings.feedTitlePath.ifBlank { "title" }

    val feedSourcePath: String
        get() = settings.feedSourcePath.ifBlank { "source.name" }

    val feedPublishedAtPath: String
        get() = settings.feedPublishedAtPath.ifBlank { "publishedAt" }

    val feedUrlPath: String
        get() = settings.feedUrlPath.ifBlank { "url" }

    val feedLanguagePath: String
        get() = settings.feedLanguagePath

    val feedDescriptionPath: String
        get() = settings.feedDescriptionPath

    val feedContentPath: String
        get() = settings.feedContentPath

    val contentTextPath: String
        get() = settings.contentTextPath.ifBlank { "content" }

    fun isProviderConfigured(): Boolean {
        return feedUrlTemplate.isNotBlank()
    }
}
