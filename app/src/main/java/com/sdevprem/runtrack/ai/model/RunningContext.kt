package com.sdevprem.runtrack.ai.model

import com.sdevprem.runtrack.domain.model.CurrentRunStateWithCalories

/** 跑步上下文数据，用于AI分析和生成语音反馈 */
data class RunningContext(
        val currentRunState: CurrentRunStateWithCalories,
        val durationInMillis: Long,
        val isFirstRun: Boolean = false,
        val previousPace: Float = 0f,
        val targetDistance: Float = 0f,
        val targetDuration: Long = 0L,
        val weatherInfo: String = "",
        val timeOfDay: String = ""
) {
    val currentPaceKmh: Float
        get() = currentRunState.currentRunState.speedInKMH

    val distanceKm: Float
        get() = currentRunState.currentRunState.distanceInMeters / 1000f

    val durationMinutes: Int
        get() = (durationInMillis / 60000).toInt()

    val averagePace: Float
        get() = if (distanceKm > 0) durationMinutes / distanceKm else 0f

    val caloriesBurned: Int
        get() = currentRunState.caloriesBurnt

    /** 生成用于AI的上下文描述 */
    fun toAIContext(): String {
        return buildString {
            append("当前跑步状态：")
            append("已跑距离${String.format("%.2f", distanceKm)}公里，")
            append("用时${durationMinutes}分钟，")
            append("当前配速${String.format("%.1f", currentPaceKmh)}公里/小时，")
            append("消耗卡路里${caloriesBurned}卡。")

            val stepState = currentRunState.currentRunState
            if (stepState.isStepSensorAvailable && stepState.totalSteps > 0) {
                append("累计${stepState.totalSteps}步，")
                append("当前步频${String.format("%.0f", stepState.stepsPerMinute)}步/分钟。")
            }

            if (targetDistance > 0) {
                val progress = (distanceKm / targetDistance * 100).toInt()
                append("目标距离${targetDistance}公里，完成进度${progress}%。")
            }

            if (previousPace > 0) {
                val paceChange = currentPaceKmh - previousPace
                when {
                    paceChange > 0.5f -> append("配速比之前提升了。")
                    paceChange < -0.5f -> append("配速比之前下降了。")
                    else -> append("配速保持稳定。")
                }
            }

            if (weatherInfo.isNotBlank()) {
                append("天气情况：$weatherInfo。")
            }

            if (timeOfDay.isNotBlank()) {
                append("时间：$timeOfDay。")
            }
        }
    }
}
