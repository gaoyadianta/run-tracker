package com.sdevprem.runtrack.ai.news.model

sealed class NewsVoiceCommand {
    data class Start(val keyword: String?, val language: String) : NewsVoiceCommand()
    data class ChangeKeyword(val keyword: String, val language: String) : NewsVoiceCommand()
    data object Pause : NewsVoiceCommand()
    data object Resume : NewsVoiceCommand()
    data object Skip : NewsVoiceCommand()
    data object Stop : NewsVoiceCommand()
    data object None : NewsVoiceCommand()
}
