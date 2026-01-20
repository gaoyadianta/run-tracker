package com.sdevprem.runtrack.ai.realtime

enum class AITransportMode {
    RTC,
    WEBSOCKET;

    companion object {
        fun fromValue(value: String): AITransportMode {
            return when (value.lowercase()) {
                "websocket", "ws" -> WEBSOCKET
                else -> RTC
            }
        }
    }
}
