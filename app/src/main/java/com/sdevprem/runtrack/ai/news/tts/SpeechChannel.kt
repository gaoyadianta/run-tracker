package com.sdevprem.runtrack.ai.news.tts

import kotlinx.coroutines.flow.Flow

interface SpeechChannel {
    val utteranceDone: Flow<String>
    val utteranceError: Flow<String>

    suspend fun awaitReady(): Boolean
    fun setLanguage(language: String)
    fun speak(text: String, utteranceId: String): Boolean
    fun stop()
    fun shutdown()
}
