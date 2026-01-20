package com.sdevprem.runtrack.ai.realtime

import com.sdevprem.runtrack.ai.config.AIProviderConfig

interface AIProviderAdapter {
    fun buildConnectionConfig(config: AIProviderConfig): AIConnectionConfig?

    fun mapOutgoingEvent(event: AIMessageEvent): AIMessageEvent {
        return event
    }

    fun mapIncomingEvent(event: AIMessageEvent): AIMessageEvent {
        return event
    }
}
