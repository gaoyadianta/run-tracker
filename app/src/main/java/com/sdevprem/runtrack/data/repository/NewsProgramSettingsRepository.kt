package com.sdevprem.runtrack.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.ai.news.config.NewsProgramSettings
import com.sdevprem.runtrack.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsProgramSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val appScope: CoroutineScope
) {
    companion object {
        private val NEWS_ENABLED = booleanPreferencesKey("news_program_enabled")
        private val NEWS_ALLOW_VOICE_START = booleanPreferencesKey("news_program_allow_voice_start")
        private val NEWS_FULLTEXT_AUTHORIZED = booleanPreferencesKey("news_program_fulltext_authorized")
        private val NEWS_AUTO_START_ON_APP_OPEN = booleanPreferencesKey("news_program_auto_start_on_app_open")
        private val NEWS_DEFAULT_KEYWORD = stringPreferencesKey("news_program_default_keyword")
        private val NEWS_DEFAULT_LANGUAGE = stringPreferencesKey("news_program_default_language")
        private val NEWS_NO_CONTENT_RETRY_MINUTES = intPreferencesKey("news_program_no_content_retry_minutes")
        private val NEWS_FEED_URL_TEMPLATE = stringPreferencesKey("news_program_feed_url_template")
        private val NEWS_CONTENT_URL_TEMPLATE = stringPreferencesKey("news_program_content_url_template")
        private val NEWS_API_KEY_HEADER_NAME = stringPreferencesKey("news_program_api_key_header_name")
        private val NEWS_API_KEY_QUERY_NAME = stringPreferencesKey("news_program_api_key_query_name")
        private val NEWS_API_KEY_VALUE = stringPreferencesKey("news_program_api_key_value")
        private val NEWS_FEED_ITEMS_PATH = stringPreferencesKey("news_program_feed_items_path")
        private val NEWS_FEED_ID_PATH = stringPreferencesKey("news_program_feed_id_path")
        private val NEWS_FEED_TITLE_PATH = stringPreferencesKey("news_program_feed_title_path")
        private val NEWS_FEED_SOURCE_PATH = stringPreferencesKey("news_program_feed_source_path")
        private val NEWS_FEED_PUBLISHED_AT_PATH = stringPreferencesKey("news_program_feed_published_at_path")
        private val NEWS_FEED_URL_PATH = stringPreferencesKey("news_program_feed_url_path")
        private val NEWS_FEED_LANGUAGE_PATH = stringPreferencesKey("news_program_feed_language_path")
        private val NEWS_FEED_DESCRIPTION_PATH = stringPreferencesKey("news_program_feed_description_path")
        private val NEWS_FEED_CONTENT_PATH = stringPreferencesKey("news_program_feed_content_path")
        private val NEWS_CONTENT_TEXT_PATH = stringPreferencesKey("news_program_content_text_path")
    }

    private val defaultSettings = NewsProgramSettings(
        enabled = context.getString(R.string.news_program_enabled).toFlexibleBoolean(default = true),
        allowVoiceStart = context.getString(R.string.news_program_allow_voice_start).toFlexibleBoolean(default = true),
        fullTextAuthorized = context.getString(R.string.news_program_fulltext_authorized).toFlexibleBoolean(default = false),
        autoStartOnAppOpen = context.getString(R.string.news_program_auto_start_on_app_open).toFlexibleBoolean(default = false),
        defaultKeyword = context.getString(R.string.news_program_default_keyword).trim(),
        defaultLanguage = context.getString(R.string.news_program_default_language).trim().ifBlank { "zh" },
        noContentRetryMinutes = context.getString(R.string.news_program_no_content_retry_minutes).toPositiveInt(default = 5),
        feedUrlTemplate = context.getString(R.string.news_program_feed_url_template).trim(),
        contentUrlTemplate = context.getString(R.string.news_program_content_url_template).trim(),
        apiKeyHeaderName = context.getString(R.string.news_program_api_key_header_name).trim(),
        apiKeyQueryName = context.getString(R.string.news_program_api_key_query_name).trim(),
        apiKeyValue = context.getString(R.string.news_program_api_key_value).trim(),
        feedItemsPath = context.getString(R.string.news_program_feed_items_path).trim(),
        feedIdPath = context.getString(R.string.news_program_feed_id_path).trim(),
        feedTitlePath = context.getString(R.string.news_program_feed_title_path).trim(),
        feedSourcePath = context.getString(R.string.news_program_feed_source_path).trim(),
        feedPublishedAtPath = context.getString(R.string.news_program_feed_published_at_path).trim(),
        feedUrlPath = context.getString(R.string.news_program_feed_url_path).trim(),
        feedLanguagePath = context.getString(R.string.news_program_feed_language_path).trim(),
        feedDescriptionPath = context.getString(R.string.news_program_feed_description_path).trim(),
        feedContentPath = context.getString(R.string.news_program_feed_content_path).trim(),
        contentTextPath = context.getString(R.string.news_program_content_text_path).trim()
    )

    private val _settings = MutableStateFlow(defaultSettings)
    val settings: StateFlow<NewsProgramSettings> = _settings.asStateFlow()

    init {
        appScope.launch {
            dataStore.data
                .map { prefs -> mapSettings(prefs) }
                .collect { latest ->
                    _settings.value = latest
                }
        }
    }

    suspend fun setEnabled(value: Boolean) = setBoolean(NEWS_ENABLED, value)
    suspend fun setAllowVoiceStart(value: Boolean) = setBoolean(NEWS_ALLOW_VOICE_START, value)
    suspend fun setFullTextAuthorized(value: Boolean) = setBoolean(NEWS_FULLTEXT_AUTHORIZED, value)
    suspend fun setAutoStartOnAppOpen(value: Boolean) = setBoolean(NEWS_AUTO_START_ON_APP_OPEN, value)
    suspend fun setDefaultKeyword(value: String) = setString(NEWS_DEFAULT_KEYWORD, value.trim())
    suspend fun setDefaultLanguage(value: String) = setString(NEWS_DEFAULT_LANGUAGE, value.trim())
    suspend fun setNoContentRetryMinutes(value: Int) = setInt(NEWS_NO_CONTENT_RETRY_MINUTES, value.coerceAtLeast(1))
    suspend fun setFeedUrlTemplate(value: String) = setString(NEWS_FEED_URL_TEMPLATE, value.trim())
    suspend fun setContentUrlTemplate(value: String) = setString(NEWS_CONTENT_URL_TEMPLATE, value.trim())
    suspend fun setApiKeyHeaderName(value: String) = setString(NEWS_API_KEY_HEADER_NAME, value.trim())
    suspend fun setApiKeyQueryName(value: String) = setString(NEWS_API_KEY_QUERY_NAME, value.trim())
    suspend fun setApiKeyValue(value: String) = setString(NEWS_API_KEY_VALUE, value.trim())

    suspend fun applyNewsApiPreset() {
        dataStore.edit { prefs ->
            prefs[NEWS_FEED_URL_TEMPLATE] = context.getString(R.string.news_program_feed_url_template).trim()
            prefs[NEWS_CONTENT_URL_TEMPLATE] = ""
            prefs[NEWS_API_KEY_QUERY_NAME] = "apiKey"
            prefs[NEWS_API_KEY_HEADER_NAME] = ""
            prefs[NEWS_FEED_ITEMS_PATH] = "articles"
            prefs[NEWS_FEED_ID_PATH] = "url"
            prefs[NEWS_FEED_TITLE_PATH] = "title"
            prefs[NEWS_FEED_SOURCE_PATH] = "source.name"
            prefs[NEWS_FEED_PUBLISHED_AT_PATH] = "publishedAt"
            prefs[NEWS_FEED_URL_PATH] = "url"
            prefs[NEWS_FEED_LANGUAGE_PATH] = "language"
            prefs[NEWS_FEED_DESCRIPTION_PATH] = "description"
            prefs[NEWS_FEED_CONTENT_PATH] = "content"
            prefs[NEWS_CONTENT_TEXT_PATH] = "content"
        }
    }

    suspend fun applyLocalMockPreset() {
        dataStore.edit { prefs ->
            prefs[NEWS_ENABLED] = true
            prefs[NEWS_ALLOW_VOICE_START] = true
            prefs[NEWS_FULLTEXT_AUTHORIZED] = true
            prefs[NEWS_AUTO_START_ON_APP_OPEN] = false
            prefs[NEWS_DEFAULT_KEYWORD] = "跑步"
            prefs[NEWS_DEFAULT_LANGUAGE] = "zh"
            prefs[NEWS_FEED_URL_TEMPLATE] = "mock://runmate/news"
            prefs[NEWS_CONTENT_URL_TEMPLATE] = ""
            prefs[NEWS_API_KEY_QUERY_NAME] = ""
            prefs[NEWS_API_KEY_HEADER_NAME] = ""
            prefs[NEWS_API_KEY_VALUE] = ""
            prefs[NEWS_FEED_ITEMS_PATH] = "articles"
            prefs[NEWS_FEED_ID_PATH] = "url"
            prefs[NEWS_FEED_TITLE_PATH] = "title"
            prefs[NEWS_FEED_SOURCE_PATH] = "source.name"
            prefs[NEWS_FEED_PUBLISHED_AT_PATH] = "publishedAt"
            prefs[NEWS_FEED_URL_PATH] = "url"
            prefs[NEWS_FEED_LANGUAGE_PATH] = "language"
            prefs[NEWS_FEED_DESCRIPTION_PATH] = "description"
            prefs[NEWS_FEED_CONTENT_PATH] = "content"
            prefs[NEWS_CONTENT_TEXT_PATH] = "content"
        }
    }

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    private suspend fun setString(key: Preferences.Key<String>, value: String) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    private suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    private fun mapSettings(prefs: Preferences): NewsProgramSettings {
        return defaultSettings.copy(
            enabled = prefs[NEWS_ENABLED] ?: defaultSettings.enabled,
            allowVoiceStart = prefs[NEWS_ALLOW_VOICE_START] ?: defaultSettings.allowVoiceStart,
            fullTextAuthorized = prefs[NEWS_FULLTEXT_AUTHORIZED] ?: defaultSettings.fullTextAuthorized,
            autoStartOnAppOpen = prefs[NEWS_AUTO_START_ON_APP_OPEN] ?: defaultSettings.autoStartOnAppOpen,
            defaultKeyword = prefs[NEWS_DEFAULT_KEYWORD] ?: defaultSettings.defaultKeyword,
            defaultLanguage = prefs[NEWS_DEFAULT_LANGUAGE] ?: defaultSettings.defaultLanguage,
            noContentRetryMinutes = prefs[NEWS_NO_CONTENT_RETRY_MINUTES] ?: defaultSettings.noContentRetryMinutes,
            feedUrlTemplate = prefs[NEWS_FEED_URL_TEMPLATE] ?: defaultSettings.feedUrlTemplate,
            contentUrlTemplate = prefs[NEWS_CONTENT_URL_TEMPLATE] ?: defaultSettings.contentUrlTemplate,
            apiKeyHeaderName = prefs[NEWS_API_KEY_HEADER_NAME] ?: defaultSettings.apiKeyHeaderName,
            apiKeyQueryName = prefs[NEWS_API_KEY_QUERY_NAME] ?: defaultSettings.apiKeyQueryName,
            apiKeyValue = prefs[NEWS_API_KEY_VALUE] ?: defaultSettings.apiKeyValue,
            feedItemsPath = prefs[NEWS_FEED_ITEMS_PATH] ?: defaultSettings.feedItemsPath,
            feedIdPath = prefs[NEWS_FEED_ID_PATH] ?: defaultSettings.feedIdPath,
            feedTitlePath = prefs[NEWS_FEED_TITLE_PATH] ?: defaultSettings.feedTitlePath,
            feedSourcePath = prefs[NEWS_FEED_SOURCE_PATH] ?: defaultSettings.feedSourcePath,
            feedPublishedAtPath = prefs[NEWS_FEED_PUBLISHED_AT_PATH] ?: defaultSettings.feedPublishedAtPath,
            feedUrlPath = prefs[NEWS_FEED_URL_PATH] ?: defaultSettings.feedUrlPath,
            feedLanguagePath = prefs[NEWS_FEED_LANGUAGE_PATH] ?: defaultSettings.feedLanguagePath,
            feedDescriptionPath = prefs[NEWS_FEED_DESCRIPTION_PATH] ?: defaultSettings.feedDescriptionPath,
            feedContentPath = prefs[NEWS_FEED_CONTENT_PATH] ?: defaultSettings.feedContentPath,
            contentTextPath = prefs[NEWS_CONTENT_TEXT_PATH] ?: defaultSettings.contentTextPath
        )
    }
}

private fun String.toFlexibleBoolean(default: Boolean): Boolean {
    return when (trim().lowercase()) {
        "1", "true", "yes", "on", "enabled" -> true
        "0", "false", "no", "off", "disabled" -> false
        else -> default
    }
}

private fun String.toPositiveInt(default: Int): Int {
    return trim().toIntOrNull()?.takeIf { it > 0 } ?: default
}
