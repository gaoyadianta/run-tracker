package com.sdevprem.runtrack.ai.model

/**
 * 跑步状态
 */
enum class RunningState {
    STOPPED,     // 未开始
    STARTING,    // 开始中（准备连接AI）
    RUNNING,     // 跑步中
    PAUSED,      // 暂停
    FINISHING    // 结束中（生成总结）
}

/**
 * 集成的跑步和AI状态
 */
data class IntegratedRunState(
    val runningState: RunningState,
    val aiConnectionState: AIConnectionState,
    val shouldAutoConnectAI: Boolean = true,
    val isGeneratingSummary: Boolean = false
)