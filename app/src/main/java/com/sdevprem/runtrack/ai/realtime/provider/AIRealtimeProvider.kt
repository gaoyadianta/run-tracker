package com.sdevprem.runtrack.ai.realtime.provider

import kotlinx.coroutines.flow.Flow

interface AIRealtimeProvider {
    val isConfigured: Boolean
    val localVadEnabled: Boolean

    suspend fun connect(): Result<Unit>
    fun disconnect()
    fun sendText(text: String)
    fun sendAudioFrame(frame: ByteArray)
    fun observeEvents(): Flow<AIRealtimeProviderEvent>
}

sealed class AIRealtimeProviderEvent {
    data class UserTranscript(val text: String, val isFinal: Boolean) : AIRealtimeProviderEvent()
    data class AssistantTextDelta(val text: String) : AIRealtimeProviderEvent()
    data class AssistantAudioDelta(val audio: ByteArray) : AIRealtimeProviderEvent()
    data class AssistantCompleted(val reason: String? = null) : AIRealtimeProviderEvent()
    data class Error(val message: String? = null) : AIRealtimeProviderEvent()
}
