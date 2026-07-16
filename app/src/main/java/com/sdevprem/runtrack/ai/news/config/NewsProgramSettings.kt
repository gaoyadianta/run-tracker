package com.sdevprem.runtrack.ai.news.config

data class NewsProgramSettings(
    val enabled: Boolean,
    val allowVoiceStart: Boolean,
    val autoStartOnAppOpen: Boolean,
    val defaultKeyword: String,
    val defaultLanguage: String,
    val noContentRetryMinutes: Int,
    val providerMode: NewsProviderMode
)

enum class NewsProviderMode {
    MOCK,
    NEWS_API,
    BACKEND;

    companion object {
        fun fromValue(value: String, fallback: NewsProviderMode): NewsProviderMode =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: fallback
    }
}
