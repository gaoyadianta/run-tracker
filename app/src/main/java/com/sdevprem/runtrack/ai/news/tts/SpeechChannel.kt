package com.sdevprem.runtrack.ai.news.tts

import kotlinx.coroutines.flow.Flow

sealed interface SpeechEvent {
    val utteranceId: String

    data class Completed(override val utteranceId: String) : SpeechEvent
    data class Error(override val utteranceId: String, val errorCode: Int? = null) : SpeechEvent
}

interface SpeechChannel {
    val events: Flow<SpeechEvent>

    suspend fun awaitReady(): Boolean
    suspend fun setLanguage(language: String): Result<Unit>
    fun speak(text: String, utteranceId: String): Boolean
    fun stop()
    fun shutdown()
}
