package com.sdevprem.runtrack.domain.tracking.model

data class CurrentRunState(
    val distanceInMeters: Int = 0,
    val speedInKMH: Float = 0f,
    val isTracking: Boolean = false,
    val pathPoints: List<PathPoint> = emptyList(),
    // 新增步数相关字段
    val totalSteps: Int = 0,           // 总步数
    val stepsPerMinute: Float = 0f,    // 步频
    val isStepSensorAvailable: Boolean = true, // 步数传感器是否可用
    val initialStepCount: Int? = null   // 记录开始跑步时的初始步数
)
