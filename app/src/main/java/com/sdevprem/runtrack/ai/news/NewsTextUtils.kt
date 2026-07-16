package com.sdevprem.runtrack.ai.news

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NewsTextUtils {
    private val controlCharRegex = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
    private val scriptTagRegex = Regex("(?is)<script[^>]*>.*?</script>")
    private val styleTagRegex = Regex("(?is)<style[^>]*>.*?</style>")
    private val htmlTagRegex = Regex("(?is)<[^>]+>")
    private val multiWhitespaceRegex = Regex("[\\t\\x0B\\f\\r ]+")
    private val multiNewlineRegex = Regex("\\n{3,}")
    private val sentenceSplitRegex = Regex("(?<=[。！？；!?;])|\\n+")
    private val newsApiTruncationRegex = Regex("\\s*\\[?\\+\\d+\\s+chars?]?\\s*$", RegexOption.IGNORE_CASE)
    private val dateFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    fun cleanText(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(scriptTagRegex, " ")
            .replace(styleTagRegex, " ")
            .replace(htmlTagRegex, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(controlCharRegex, " ")
            .replace("\r\n", "\n")
            .replace(multiWhitespaceRegex, " ")
            .replace(multiNewlineRegex, "\n\n")
            .trim()
    }

    fun cleanSnippet(raw: String?): String = cleanText(raw.orEmpty())
        .replace(newsApiTruncationRegex, "")
        .trim()

    fun splitToSentences(content: String): List<String> {
        return content
            .split(sentenceSplitRegex)
            .map { it.trim() }
            .filter { it.length >= 2 }
    }

    fun parseEpochMs(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        trimmed.toLongOrNull()?.let { raw ->
            return if (raw > 100000000000L) raw else raw * 1000
        }
        for (pattern in dateFormats) {
            val formatter = SimpleDateFormat(pattern, Locale.US)
            formatter.isLenient = true
            try {
                return formatter.parse(trimmed)?.time
            } catch (_: ParseException) {
            }
        }
        return null
    }

    fun formatPublishedTime(epochMs: Long?): String {
        if (epochMs == null) return "未知时间"
        val date = Date(epochMs)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(date)
    }
}
