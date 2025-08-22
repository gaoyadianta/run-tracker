package com.sdevprem.runtrack.ai.model

/**
 * AI播报状态
 */
enum class AIBroadcastState {
    IDLE,           // 空闲状态
    BROADCASTING,   // 播报中
    COMPLETED       // 播报完成
}

/**
 * 总结播报状态
 */
data class SummaryBroadcastState(
    val isGeneratingSummary: Boolean = false,
    val broadcastState: AIBroadcastState = AIBroadcastState.IDLE,
    val shouldAutoDisconnectAfterSummary: Boolean = false
)