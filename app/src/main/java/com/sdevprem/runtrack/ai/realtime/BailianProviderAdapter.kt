package com.sdevprem.runtrack.ai.realtime

import com.sdevprem.runtrack.ai.config.AIProviderConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BailianProviderAdapter @Inject constructor() : AIProviderAdapter {
    override fun buildConnectionConfig(config: AIProviderConfig): AIConnectionConfig? {
        val url = config.bailianWebSocketUrl
        if (url.isBlank()) {
            return null
        }

        val headers = buildMap {
            if (config.bailianWebSocketToken.isNotBlank()) {
                put("Authorization", "Bearer ${config.bailianWebSocketToken}")
            }
        }

        return AIConnectionConfig(url = url, headers = headers)
    }
}
