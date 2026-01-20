package com.sdevprem.runtrack.ai.realtime

import com.sdevprem.runtrack.ai.config.AIProviderConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolcanoProviderAdapter @Inject constructor() : AIProviderAdapter {
    override fun buildConnectionConfig(config: AIProviderConfig): AIConnectionConfig? {
        val url = config.volcanoWebSocketUrl
        if (url.isBlank()) {
            return null
        }

        val headers = buildMap {
            if (config.volcanoWebSocketToken.isNotBlank()) {
                put("Authorization", "Bearer ${config.volcanoWebSocketToken}")
            }
        }

        return AIConnectionConfig(url = url, headers = headers)
    }
}
