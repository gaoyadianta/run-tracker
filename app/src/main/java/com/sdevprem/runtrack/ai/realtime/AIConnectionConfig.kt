package com.sdevprem.runtrack.ai.realtime

data class AIConnectionConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)
