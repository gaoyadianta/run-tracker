package com.sdevprem.runtrack.ai.realtime

data class AIMessageEvent(
    val type: String,
    val payload: Map<String, Any?> = emptyMap(),
    val sessionId: String? = null,
    val seq: Long? = null
)
