package com.sdevprem.runtrack.ai.realtime

enum class AIProvider {
    VOLCANO,
    BAILIAN;

    companion object {
        fun fromValue(value: String): AIProvider {
            return when (value.lowercase()) {
                "bailian", "aliyun", "alibaba" -> BAILIAN
                else -> VOLCANO
            }
        }
    }
}
