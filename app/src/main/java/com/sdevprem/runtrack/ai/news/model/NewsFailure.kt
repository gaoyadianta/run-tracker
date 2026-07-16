package com.sdevprem.runtrack.ai.news.model

sealed class NewsFailure(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    data object NotConfigured : NewsFailure("新闻服务未配置")
    data object Unauthorized : NewsFailure("新闻服务鉴权失败")
    data object RateLimited : NewsFailure("新闻服务请求过于频繁")
    data object NoContent : NewsFailure("暂无可播报新闻")
    data class Network(val source: Throwable? = null) : NewsFailure("新闻网络连接失败", source)
    data class Provider(val detail: String, val source: Throwable? = null) : NewsFailure(detail, source)
    data object TtsUnavailable : NewsFailure("本地 TTS 不可用")
    data object LanguageUnsupported : NewsFailure("当前 TTS 不支持所选语言")
}
