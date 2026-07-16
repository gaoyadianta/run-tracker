package com.sdevprem.runtrack.ai.news.config

import android.content.Context
import com.sdevprem.runtrack.BuildConfig
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.data.repository.NewsProgramSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsProgramConfig @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: NewsProgramSettingsRepository
) {
    private val settings: NewsProgramSettings
        get() = settingsRepository.settings.value

    val settingsUpdates: StateFlow<NewsProgramSettings>
        get() = settingsRepository.settings

    val enabled: Boolean
        get() = settings.enabled

    val allowVoiceStart: Boolean
        get() = settings.allowVoiceStart

    val autoStartOnAppOpen: Boolean
        get() = settings.autoStartOnAppOpen

    val defaultKeyword: String
        get() = settings.defaultKeyword

    val defaultLanguage: String
        get() = settings.defaultLanguage.ifBlank { "zh" }

    val noContentRetryIntervalMs: Long
        get() = settings.noContentRetryMinutes.toLong().coerceAtLeast(1L) * 60_000L

    val providerMode: NewsProviderMode
        get() = if (BuildConfig.DEBUG) settings.providerMode else NewsProviderMode.BACKEND

    val newsApiKey: String
        get() = context.getString(R.string.news_program_api_key_value).trim()

    val backendBaseUrl: String
        get() = context.getString(R.string.news_backend_base_url).trim().trimEnd('/')

    fun isProviderConfigured(): Boolean = when (providerMode) {
        NewsProviderMode.MOCK -> BuildConfig.DEBUG
        NewsProviderMode.NEWS_API -> BuildConfig.DEBUG && newsApiKey.isNotBlank()
        NewsProviderMode.BACKEND -> backendBaseUrl.startsWith("https://")
    }
}
