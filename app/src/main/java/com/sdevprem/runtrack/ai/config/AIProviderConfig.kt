package com.sdevprem.runtrack.ai.config

import android.content.Context
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.ai.realtime.AIProvider
import com.sdevprem.runtrack.ai.realtime.AITransportMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIProviderConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val transportModeRaw: String by lazy {
        context.getString(R.string.ai_transport_mode).trim()
    }

    private val providerRaw: String by lazy {
        context.getString(R.string.ai_provider).trim()
    }

    val transportMode: AITransportMode
        get() = AITransportMode.fromValue(transportModeRaw)

    val provider: AIProvider
        get() = AIProvider.fromValue(providerRaw)

    val volcanoWebSocketUrl: String by lazy {
        context.getString(R.string.ai_ws_volcano_url).trim()
    }

    val volcanoWebSocketToken: String by lazy {
        context.getString(R.string.ai_ws_volcano_token).trim()
    }

    val bailianWebSocketUrl: String by lazy {
        context.getString(R.string.ai_ws_bailian_url).trim()
    }

    val bailianWebSocketToken: String by lazy {
        context.getString(R.string.ai_ws_bailian_token).trim()
    }

    fun isWebSocketEnabled(): Boolean {
        return transportMode == AITransportMode.WEBSOCKET
    }

    fun getWebSocketUrl(): String {
        return when (provider) {
            AIProvider.VOLCANO -> volcanoWebSocketUrl
            AIProvider.BAILIAN -> bailianWebSocketUrl
        }
    }

    fun getWebSocketToken(): String {
        return when (provider) {
            AIProvider.VOLCANO -> volcanoWebSocketToken
            AIProvider.BAILIAN -> bailianWebSocketToken
        }
    }

    fun isWebSocketConfigured(): Boolean {
        return getWebSocketUrl().isNotBlank()
    }
}
