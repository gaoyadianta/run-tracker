package com.sdevprem.runtrack.domain.model

data class MetricPoint(
    val timeOffsetMs: Long,
    val value: Float
)

data class RunSplit(
    val kmIndex: Int,
    val durationMs: Long,
    val paceMinPerKm: Float,
    val distanceMeters: Int
)

data class RunMetricsData(
    val paceSeries: List<MetricPoint> = emptyList(),
    val heartRateSeries: List<MetricPoint> = emptyList(),
    val elevationSeries: List<MetricPoint> = emptyList(),
    val splits: List<RunSplit> = emptyList(),
    val cadenceSeries: List<MetricPoint> = emptyList(),
    val strideLengthSeries: List<MetricPoint> = emptyList()
)
