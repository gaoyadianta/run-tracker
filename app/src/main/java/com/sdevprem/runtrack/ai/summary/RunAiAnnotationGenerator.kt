package com.sdevprem.runtrack.ai.summary

import com.sdevprem.runtrack.domain.model.MetricPoint
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint
import com.sdevprem.runtrack.domain.model.RunMetricsData
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunAiAnnotationGenerator @Inject constructor() {

    fun generate(
        metrics: RunMetricsData,
        pathPoints: List<PathPoint>,
        totalDurationMs: Long
    ): List<RunAiAnnotationPoint> {
        val locations = pathPoints.mapNotNull { it as? PathPoint.LocationPoint }
        if (locations.size < 2) return emptyList()

        val timeOffsets = buildTimeOffsets(locations, totalDurationMs)
        if (timeOffsets.isEmpty()) return emptyList()

        val annotations = mutableListOf<RunAiAnnotationPoint>()
        val paceSlowPoint = metrics.paceSeries.maxByOrNull { it.value }
        paceSlowPoint?.let {
            addAnnotation(
                annotations,
                timeOffsets,
                it,
                locations,
                "这里配速明显下降"
            )
        }

        val elevationPeak = metrics.elevationSeries.maxByOrNull { it.value }
        elevationPeak?.let {
            addAnnotation(
                annotations,
                timeOffsets,
                it,
                locations,
                "这里是爬升最高点"
            )
        }

        val finalPush = findFinalPush(metrics.paceSeries)
        finalPush?.let {
            addAnnotation(
                annotations,
                timeOffsets,
                it,
                locations,
                "最后一段的冲刺点"
            )
        }

        return annotations.sortedBy { it.timeOffsetMs }
    }

    private fun addAnnotation(
        list: MutableList<RunAiAnnotationPoint>,
        timeOffsets: List<Long>,
        point: MetricPoint,
        locations: List<PathPoint.LocationPoint>,
        text: String
    ) {
        val existing = list.any { abs(it.timeOffsetMs - point.timeOffsetMs) < 60_000L }
        if (existing) return

        val location = findLocationForTime(timeOffsets, locations, point.timeOffsetMs)
        list.add(
            RunAiAnnotationPoint(
                timeOffsetMs = point.timeOffsetMs,
                latitude = location.locationInfo.latitude,
                longitude = location.locationInfo.longitude,
                text = text
            )
        )
    }

    private fun findFinalPush(points: List<MetricPoint>): MetricPoint? {
        if (points.isEmpty()) return null
        val startIndex = (points.size * 0.8f).toInt().coerceAtMost(points.lastIndex)
        val lastSegment = points.subList(startIndex, points.size)
        return lastSegment.minByOrNull { it.value }
    }

    private fun buildTimeOffsets(
        locations: List<PathPoint.LocationPoint>,
        totalDurationMs: Long
    ): List<Long> {
        val rawTimes = locations.map { it.locationInfo.timeMs }
        val hasTimes = rawTimes.any { it > 0L }
        if (hasTimes) {
            val base = rawTimes.firstOrNull { it > 0L } ?: 0L
            return rawTimes.mapIndexed { index, time ->
                if (time > 0L) time - base
                else {
                    val interval = estimateInterval(totalDurationMs, locations.size)
                    interval * index
                }
            }
        }
        val interval = estimateInterval(totalDurationMs, locations.size)
        return List(locations.size) { index -> interval * index }
    }

    private fun estimateInterval(totalDurationMs: Long, size: Int): Long {
        if (size <= 1) return 1000L
        return (totalDurationMs / (size - 1)).coerceAtLeast(1000L)
    }

    private fun findLocationForTime(
        timeOffsets: List<Long>,
        locations: List<PathPoint.LocationPoint>,
        targetTime: Long
    ): PathPoint.LocationPoint {
        var bestIndex = 0
        var bestDiff = Long.MAX_VALUE
        timeOffsets.forEachIndexed { index, time ->
            val diff = abs(time - targetTime)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = index
            }
        }
        return locations[bestIndex]
    }
}
