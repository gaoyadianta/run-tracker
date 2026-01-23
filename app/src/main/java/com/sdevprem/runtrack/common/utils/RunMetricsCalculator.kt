package com.sdevprem.runtrack.common.utils

import com.sdevprem.runtrack.domain.model.MetricPoint
import com.sdevprem.runtrack.domain.model.RunMetricsData
import com.sdevprem.runtrack.domain.model.RunSplit
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import kotlin.math.roundToInt

object RunMetricsCalculator {

    fun calculate(
        pathPoints: List<PathPoint>,
        totalDurationMs: Long
    ): RunMetricsData {
        val locationPoints = pathPoints.mapNotNull { it as? PathPoint.LocationPoint }
        if (locationPoints.size < 2) return RunMetricsData()

        val times = buildTimeSeries(locationPoints, totalDurationMs)
        val baseTime = times.firstOrNull() ?: 0L

        val paceSeries = mutableListOf<MetricPoint>()
        val elevationSeries = mutableListOf<MetricPoint>()
        val splits = mutableListOf<RunSplit>()

        var splitDistance = 0f
        var splitTime = 0f
        var kmIndex = 1

        for (i in 1 until locationPoints.size) {
            val prev = locationPoints[i - 1]
            val curr = locationPoints[i]
            val dtMs = (times[i] - times[i - 1]).coerceAtLeast(0L)
            if (dtMs == 0L) continue

            val segmentDistanceMeters = LocationUtils.getDistanceBetweenPathPoints(prev, curr).toFloat()
            if (segmentDistanceMeters <= 0f) continue

            val speedMetersPerSec = segmentDistanceMeters / (dtMs / 1000f)
            val paceMinPerKm = if (speedMetersPerSec > 0f) {
                (1000f / speedMetersPerSec) / 60f
            } else 0f

            val timeOffset = times[i] - baseTime
            paceSeries.add(MetricPoint(timeOffsetMs = timeOffset, value = paceMinPerKm))

            curr.locationInfo.altitudeMeters?.let { altitude ->
                elevationSeries.add(
                    MetricPoint(
                        timeOffsetMs = timeOffset,
                        value = altitude.toFloat()
                    )
                )
            }

            var remainingDistance = segmentDistanceMeters
            var remainingTime = dtMs.toFloat()
            while (splitDistance + remainingDistance >= 1000f && remainingDistance > 0f) {
                val distanceToBoundary = 1000f - splitDistance
                val ratio = distanceToBoundary / remainingDistance
                val timeToBoundary = remainingTime * ratio
                splitTime += timeToBoundary

                val paceForSplit = (splitTime / 60000f)
                splits.add(
                    RunSplit(
                        kmIndex = kmIndex,
                        durationMs = splitTime.roundToInt().toLong(),
                        paceMinPerKm = paceForSplit,
                        distanceMeters = 1000
                    )
                )

                kmIndex += 1
                remainingDistance -= distanceToBoundary
                remainingTime -= timeToBoundary
                splitDistance = 0f
                splitTime = 0f
            }

            splitDistance += remainingDistance
            splitTime += remainingTime
        }

        if (splitDistance >= 200f && splitTime > 0f) {
            val paceForSplit = (splitTime / 60000f) / (splitDistance / 1000f)
            splits.add(
                RunSplit(
                    kmIndex = kmIndex,
                    durationMs = splitTime.roundToInt().toLong(),
                    paceMinPerKm = paceForSplit,
                    distanceMeters = splitDistance.roundToInt()
                )
            )
        }

        return RunMetricsData(
            paceSeries = paceSeries,
            elevationSeries = elevationSeries,
            splits = splits
        )
    }

    private fun buildTimeSeries(
        locationPoints: List<PathPoint.LocationPoint>,
        totalDurationMs: Long
    ): List<Long> {
        val providedTimes = locationPoints.map { it.locationInfo.timeMs }
        val hasValidTimes = providedTimes.any { it > 0L }

        if (hasValidTimes) {
            val first = providedTimes.firstOrNull { it > 0L } ?: 0L
            return providedTimes.mapIndexed { index, time ->
                if (time > 0L) time else first + estimateFallbackInterval(totalDurationMs, locationPoints.size) * index
            }
        }

        val interval = estimateFallbackInterval(totalDurationMs, locationPoints.size)
        return List(locationPoints.size) { index -> interval * index }
    }

    private fun estimateFallbackInterval(totalDurationMs: Long, size: Int): Long {
        if (size <= 1) return 1000L
        return (totalDurationMs / (size - 1)).coerceAtLeast(1000L)
    }
}
