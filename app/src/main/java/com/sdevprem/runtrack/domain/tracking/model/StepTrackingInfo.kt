package com.sdevprem.runtrack.domain.tracking.model

data class StepTrackingInfo(
    val totalSteps: Int = 0,           // 从开始跑步到现在的总步数
    val stepsPerMinute: Float = 0f,    // 当前步频
    val isStepSensorAvailable: Boolean = true
)