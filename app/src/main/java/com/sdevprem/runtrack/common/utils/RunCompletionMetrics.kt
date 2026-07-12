package com.sdevprem.runtrack.common.utils

object RunCompletionMetrics {
    fun averageSpeedKmh(distanceMeters: Int, durationMs: Long): Float {
        if (distanceMeters <= 0 || durationMs <= 0L) return 0f
        return distanceMeters * 3_600f / durationMs
    }

    fun averageCadence(totalSteps: Int, durationMs: Long, fallback: Float): Float {
        if (totalSteps <= 0 || durationMs <= 0L) return fallback.coerceAtLeast(0f)
        return totalSteps / (durationMs / 60_000f)
    }
}
