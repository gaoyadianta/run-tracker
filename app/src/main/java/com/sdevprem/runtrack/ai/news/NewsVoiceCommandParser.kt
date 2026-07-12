package com.sdevprem.runtrack.ai.news

import com.sdevprem.runtrack.ai.news.model.NewsVoiceCommand
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsVoiceCommandParser @Inject constructor() {
    private val chineseChangeRegex = Regex("(?:换成|换到|改成|切换到)(.+?)(?:行业的?新闻|新闻资讯|资讯|新闻)?[。！!？?]*$")
    private val chineseStartRegex = Regex("(?:给我播报点|开始播报|开始新闻播报|新闻模式|播报)(.+?)(?:行业的?新闻资讯|行业新闻|新闻资讯|资讯|新闻)?[。！!？?]*$")
    private val englishChangeRegex = Regex("(?:switch|change)\\s+(?:to\\s+)?(.+?)(?:\\s+news)?[.!?]*$", RegexOption.IGNORE_CASE)
    private val englishStartRegex = Regex("(?:start|play|read)\\s+(?:me\\s+)?(?:news\\s+)?(?:about\\s+)?(.+?)[.!?]*$", RegexOption.IGNORE_CASE)
    private val containsChineseRegex = Regex("[\\u4e00-\\u9FFF]")

    fun parse(transcript: String): NewsVoiceCommand {
        val trimmed = transcript.trim()
        if (trimmed.isBlank()) return NewsVoiceCommand.None

        val compact = trimmed.replace(" ", "")
        val lower = trimmed.lowercase()
        val language = detectLanguage(trimmed)

        if (compact.contains("暂停新闻") || compact.contains("暂停播报") || lower.contains("pause news")) {
            return NewsVoiceCommand.Pause
        }
        if (compact.contains("继续新闻") || compact.contains("恢复新闻") || lower.contains("resume news") || lower.contains("continue news")) {
            return NewsVoiceCommand.Resume
        }
        if (compact.contains("跳过这条") || compact.contains("下一条") || lower.contains("skip this") || lower.contains("next news")) {
            return NewsVoiceCommand.Skip
        }
        if (compact.contains("结束播报") || compact.contains("停止新闻") || compact.contains("停止播报") || lower.contains("stop news") || lower.contains("end news")) {
            return NewsVoiceCommand.Stop
        }

        chineseChangeRegex.find(compact)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { keyword ->
                return NewsVoiceCommand.ChangeKeyword(keyword = keyword, language = language)
            }

        englishChangeRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { keyword ->
                return NewsVoiceCommand.ChangeKeyword(keyword = keyword, language = "en")
            }

        chineseStartRegex.find(compact)?.groupValues?.getOrNull(1)?.trim()
            ?.let { keyword ->
                return NewsVoiceCommand.Start(keyword = keyword.takeIf { it.isNotBlank() }, language = language)
            }

        if (compact == "开始新闻播报" || compact == "开始新闻" || compact == "新闻模式") {
            return NewsVoiceCommand.Start(keyword = null, language = language)
        }

        englishStartRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
            ?.let { keyword ->
                return NewsVoiceCommand.Start(keyword = keyword.takeIf { it.isNotBlank() }, language = "en")
            }

        return NewsVoiceCommand.None
    }

    private fun detectLanguage(text: String): String {
        if (containsChineseRegex.containsMatchIn(text)) return "zh"
        return "en"
    }
}
