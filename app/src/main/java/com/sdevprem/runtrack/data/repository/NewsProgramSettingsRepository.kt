package com.sdevprem.runtrack.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sdevprem.runtrack.BuildConfig
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.ai.news.config.NewsProgramSettings
import com.sdevprem.runtrack.ai.news.config.NewsProviderMode
import com.sdevprem.runtrack.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsProgramSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val appScope: CoroutineScope
) {
    companion object {
        private const val CURRENT_SETTINGS_VERSION = 2

        private val NEWS_SETTINGS_VERSION = intPreferencesKey("news_settings_version")
        private val NEWS_ENABLED = booleanPreferencesKey("news_program_enabled")
        private val NEWS_ALLOW_VOICE_START = booleanPreferencesKey("news_program_allow_voice_start")
        private val NEWS_AUTO_START_ON_APP_OPEN = booleanPreferencesKey("news_program_auto_start_on_app_open")
        private val NEWS_DEFAULT_KEYWORD = stringPreferencesKey("news_program_default_keyword")
        private val NEWS_DEFAULT_LANGUAGE = stringPreferencesKey("news_program_default_language")
        private val NEWS_NO_CONTENT_RETRY_MINUTES = intPreferencesKey("news_program_no_content_retry_minutes")
        private val NEWS_PROVIDER_MODE = stringPreferencesKey("news_program_provider_mode")
        private val NEWS_INSTALLATION_ID = stringPreferencesKey("news_program_installation_id")

        // Legacy keys are removed once so old clear-text credentials cannot survive in DataStore.
        private val LEGACY_FULLTEXT_AUTHORIZED = booleanPreferencesKey("news_program_fulltext_authorized")
        private val LEGACY_API_KEY_VALUE = stringPreferencesKey("news_program_api_key_value")
        private val LEGACY_FEED_URL_TEMPLATE = stringPreferencesKey("news_program_feed_url_template")
        private val LEGACY_CONTENT_URL_TEMPLATE = stringPreferencesKey("news_program_content_url_template")
        private val LEGACY_API_KEY_HEADER_NAME = stringPreferencesKey("news_program_api_key_header_name")
        private val LEGACY_API_KEY_QUERY_NAME = stringPreferencesKey("news_program_api_key_query_name")
    }

    private val defaultProviderMode = if (BuildConfig.DEBUG) {
        NewsProviderMode.MOCK
    } else {
        NewsProviderMode.BACKEND
    }

    private val defaultSettings = NewsProgramSettings(
        enabled = false,
        allowVoiceStart = context.getString(R.string.news_program_allow_voice_start).toFlexibleBoolean(default = true),
        autoStartOnAppOpen = false,
        defaultKeyword = context.getString(R.string.news_program_default_keyword).trim().ifBlank { "科技" },
        defaultLanguage = context.getString(R.string.news_program_default_language).trim().ifBlank { "zh" },
        noContentRetryMinutes = context.getString(R.string.news_program_no_content_retry_minutes).toPositiveInt(default = 5),
        providerMode = defaultProviderMode
    )

    private val _settings = MutableStateFlow(defaultSettings)
    val settings: StateFlow<NewsProgramSettings> = _settings.asStateFlow()

    init {
        appScope.launch {
            migrateLegacySettings()
            dataStore.data
                .map(::mapSettings)
                .collect { latest -> _settings.value = latest }
        }
    }

    suspend fun setEnabled(value: Boolean) = setBoolean(NEWS_ENABLED, value)
    suspend fun setAllowVoiceStart(value: Boolean) = setBoolean(NEWS_ALLOW_VOICE_START, value)
    suspend fun setAutoStartOnAppOpen(value: Boolean) = setBoolean(NEWS_AUTO_START_ON_APP_OPEN, value)
    suspend fun setDefaultKeyword(value: String) = setString(NEWS_DEFAULT_KEYWORD, value.trim())
    suspend fun setDefaultLanguage(value: String) = setString(NEWS_DEFAULT_LANGUAGE, value.trim())
    suspend fun setNoContentRetryMinutes(value: Int) = setInt(NEWS_NO_CONTENT_RETRY_MINUTES, value.coerceAtLeast(1))

    suspend fun applyNewsApiPreset() {
        if (!BuildConfig.DEBUG) return
        dataStore.edit { prefs ->
            prefs[NEWS_PROVIDER_MODE] = NewsProviderMode.NEWS_API.name
            prefs[NEWS_ENABLED] = true
            prefs[NEWS_AUTO_START_ON_APP_OPEN] = false
        }
    }

    suspend fun applyLocalMockPreset() {
        if (!BuildConfig.DEBUG) return
        dataStore.edit { prefs ->
            prefs[NEWS_PROVIDER_MODE] = NewsProviderMode.MOCK.name
            prefs[NEWS_ENABLED] = true
            prefs[NEWS_AUTO_START_ON_APP_OPEN] = false
            prefs[NEWS_DEFAULT_KEYWORD] = "跑步"
            prefs[NEWS_DEFAULT_LANGUAGE] = "zh"
        }
    }

    suspend fun getOrCreateInstallationId(): String {
        var installationId: String? = null
        dataStore.edit { prefs ->
            installationId = prefs[NEWS_INSTALLATION_ID]
            if (installationId.isNullOrBlank()) {
                installationId = UUID.randomUUID().toString()
                prefs[NEWS_INSTALLATION_ID] = installationId!!
            }
        }
        return installationId!!
    }

    private suspend fun migrateLegacySettings() {
        dataStore.edit { prefs ->
            if ((prefs[NEWS_SETTINGS_VERSION] ?: 0) >= CURRENT_SETTINGS_VERSION) return@edit
            prefs.remove(LEGACY_API_KEY_VALUE)
            prefs.remove(LEGACY_FULLTEXT_AUTHORIZED)
            prefs.remove(LEGACY_FEED_URL_TEMPLATE)
            prefs.remove(LEGACY_CONTENT_URL_TEMPLATE)
            prefs.remove(LEGACY_API_KEY_HEADER_NAME)
            prefs.remove(LEGACY_API_KEY_QUERY_NAME)
            prefs[NEWS_AUTO_START_ON_APP_OPEN] = false
            prefs[NEWS_ENABLED] = false
            prefs[NEWS_PROVIDER_MODE] = defaultProviderMode.name
            prefs[NEWS_SETTINGS_VERSION] = CURRENT_SETTINGS_VERSION
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

    private fun mapSettings(prefs: Preferences): NewsProgramSettings = defaultSettings.copy(
        enabled = prefs[NEWS_ENABLED] ?: defaultSettings.enabled,
        allowVoiceStart = prefs[NEWS_ALLOW_VOICE_START] ?: defaultSettings.allowVoiceStart,
        autoStartOnAppOpen = prefs[NEWS_AUTO_START_ON_APP_OPEN] ?: defaultSettings.autoStartOnAppOpen,
        defaultKeyword = prefs[NEWS_DEFAULT_KEYWORD] ?: defaultSettings.defaultKeyword,
        defaultLanguage = prefs[NEWS_DEFAULT_LANGUAGE] ?: defaultSettings.defaultLanguage,
        noContentRetryMinutes = prefs[NEWS_NO_CONTENT_RETRY_MINUTES] ?: defaultSettings.noContentRetryMinutes,
        providerMode = NewsProviderMode.fromValue(
            value = prefs[NEWS_PROVIDER_MODE].orEmpty(),
            fallback = defaultProviderMode
        )
    )
}

private fun String.toFlexibleBoolean(default: Boolean): Boolean = when (trim().lowercase()) {
    "1", "true", "yes", "on", "enabled" -> true
    "0", "false", "no", "off", "disabled" -> false
    else -> default
}

private fun String.toPositiveInt(default: Int): Int =
    trim().toIntOrNull()?.takeIf { it > 0 } ?: default
