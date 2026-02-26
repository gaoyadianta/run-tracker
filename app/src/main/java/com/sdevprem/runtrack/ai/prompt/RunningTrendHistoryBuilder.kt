package com.sdevprem.runtrack.ai.prompt

import com.sdevprem.runtrack.ai.model.RunningTrendHistory
import com.sdevprem.runtrack.ai.model.RunningTrendSample
import com.sdevprem.runtrack.common.utils.LocationUtils
import com.sdevprem.runtrack.domain.model.MetricPoint
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import kotlin.math.ceil
import kotlin.math.roundToInt

object RunningTrendHistoryBuilder {

    private val samplingTiers = listOf(
        SamplingTier(maxAgeMs = 5 * 60_000L, intervalMs = 0L),
        SamplingTier(maxAgeMs = 10 * 60_000L, intervalMs = 10_000L),
        SamplingTier(maxAgeMs = 20 * 60_000L, intervalMs = 30_000L),
        SamplingTier(maxAgeMs = 40 * 60_000L, intervalMs = 60_000L),
        SamplingTier(maxAgeMs = Long.MAX_VALUE, intervalMs = 120_000L)
    )

    private const val DEFAULT_MAX_POINTS = 180
    private const val DEFAULT_POLICY_LABEL = "0-5m全量,5-10m/10s,10-20m/30s,20-40m/60s,40m+/120s"

    fun build(
        pathPoints: List<PathPoint>,
        totalDurationMs: Long,
        cadenceSeries: List<MetricPoint>,
        totalStepsSeries: List<MetricPoint>,
        maxPoints: Int = DEFAULT_MAX_POINTS
    ): RunningTrendHistory {
        val locationPoints = pathPoints.filterIsInstance<PathPoint.LocationPoint>()
        if (locationPoints.isEmpty()) {
            return RunningTrendHistory(
                samplingPolicy = DEFAULT_POLICY_LABEL
            )
        }

        val timeOffsets = buildTimeOffsetsMs(locationPoints, totalDurationMs)
        val baseSamples = buildBaseSamples(locationPoints, timeOffsets)
        val sampledBase = downsampleByRecency(baseSamples)
        val boundedBase = applyPointBudget(sampledBase, maxPoints.coerceAtLeast(1))

        val sortedCadence = cadenceSeries.sortedBy { it.timeOffsetMs }
        val sortedSteps = totalStepsSeries.sortedBy { it.timeOffsetMs }

        val enrichedSamples = boundedBase.map { sample ->
            val cadence = sortedCadence.lastOrNull { it.timeOffsetMs <= sample.timeOffsetMs }?.value
            val steps = sortedSteps.lastOrNull { it.timeOffsetMs <= sample.timeOffsetMs }?.value
            RunningTrendSample(
                tSec = (sample.timeOffsetMs / 1000L).toInt(),
                distM = sample.distanceMeters,
                speedKmh = sample.speedKmh.coerceAtLeast(0f),
                lat = sample.latitude,
                lon = sample.longitude,
                cadSpm = cadence?.takeIf { it.isFinite() }?.roundToInt(),
                steps = steps?.takeIf { it.isFinite() }?.roundToInt()
            )
        }

        return RunningTrendHistory(
            samplingPolicy = DEFAULT_POLICY_LABEL,
            originalPointCount = baseSamples.size,
            sampledPointCount = enrichedSamples.size,
            points = enrichedSamples
        )
    }

    private fun buildTimeOffsetsMs(
        locationPoints: List<PathPoint.LocationPoint>,
        totalDurationMs: Long
    ): List<Long> {
        if (locationPoints.size == 1) return listOf(0L)

        val rawTimes = locationPoints.map { it.locationInfo.timeMs }
        val validTimes = rawTimes.count { it > 0L } >= 2
        if (validTimes) {
            val baseTime = rawTimes.firstOrNull { it > 0L } ?: 0L
            var previous = 0L
            return rawTimes.mapIndexed { index, timeMs ->
                val fallback = estimateFallbackOffset(totalDurationMs, locationPoints.size, index)
                val candidate = if (timeMs > 0L) (timeMs - baseTime).coerceAtLeast(0L) else fallback
                val normalized = maxOf(candidate, previous)
                previous = normalized
                normalized
            }
        }

        return List(locationPoints.size) { index ->
            estimateFallbackOffset(totalDurationMs, locationPoints.size, index)
        }
    }

    private fun estimateFallbackOffset(totalDurationMs: Long, size: Int, index: Int): Long {
        if (size <= 1) return 0L
        val safeDuration = totalDurationMs.coerceAtLeast(1L)
        return (safeDuration * index) / (size - 1)
    }

    private fun buildBaseSamples(
        locationPoints: List<PathPoint.LocationPoint>,
        timeOffsetsMs: List<Long>
    ): List<BaseSample> {
        val result = ArrayList<BaseSample>(locationPoints.size)
        var cumulativeDistance = 0

        locationPoints.forEachIndexed { index, point ->
            var speedKmh = 0f
            if (index > 0) {
                val segmentDistance = LocationUtils.getDistanceBetweenPathPoints(
                    locationPoints[index - 1],
                    point
                )
                cumulativeDistance += segmentDistance
                val dtMs = (timeOffsetsMs[index] - timeOffsetsMs[index - 1]).coerceAtLeast(1L)
                speedKmh = (segmentDistance.toFloat() / dtMs.toFloat()) * 3600f
            }

            result.add(
                BaseSample(
                    timeOffsetMs = timeOffsetsMs[index],
                    distanceMeters = cumulativeDistance,
                    speedKmh = speedKmh,
                    latitude = point.locationInfo.latitude,
                    longitude = point.locationInfo.longitude
                )
            )
        }
        return result
    }

    private fun downsampleByRecency(baseSamples: List<BaseSample>): List<BaseSample> {
        if (baseSamples.isEmpty()) return emptyList()
        if (baseSamples.size == 1) return baseSamples

        val newestTimeMs = baseSamples.last().timeOffsetMs
        val kept = ArrayList<BaseSample>(baseSamples.size)
        var lastKeptTimeMs = Long.MIN_VALUE

        baseSamples.forEach { sample ->
            val ageMs = (newestTimeMs - sample.timeOffsetMs).coerceAtLeast(0L)
            val intervalMs = intervalForAge(ageMs)
            val shouldKeep = intervalMs <= 0L ||
                lastKeptTimeMs == Long.MIN_VALUE ||
                sample.timeOffsetMs - lastKeptTimeMs >= intervalMs

            if (shouldKeep) {
                kept.add(sample)
                lastKeptTimeMs = sample.timeOffsetMs
            }
        }

        if (kept.last().timeOffsetMs != baseSamples.last().timeOffsetMs) {
            kept.add(baseSamples.last())
        }
        if (kept.first().timeOffsetMs != baseSamples.first().timeOffsetMs) {
            kept.add(0, baseSamples.first())
        }
        return kept
    }

    private fun intervalForAge(ageMs: Long): Long {
        return samplingTiers.firstOrNull { ageMs <= it.maxAgeMs }?.intervalMs ?: 120_000L
    }

    private fun applyPointBudget(samples: List<BaseSample>, maxPoints: Int): List<BaseSample> {
        if (samples.size <= maxPoints) return samples
        if (maxPoints <= 2) return listOf(samples.first(), samples.last())

        val recentBudget = (maxPoints * 0.6f).roundToInt().coerceIn(2, maxPoints - 1)
        val oldBudget = (maxPoints - recentBudget).coerceAtLeast(1)
        val recent = samples.takeLast(recentBudget)
        val old = samples.dropLast(recentBudget)

        val stride = ceil(old.size / oldBudget.toDouble()).toInt().coerceAtLeast(1)
        val compactOld = old.filterIndexed { index, _ -> index % stride == 0 }

        val merged = (compactOld + recent).distinctBy { it.timeOffsetMs }
        return if (merged.size <= maxPoints) {
            merged
        } else {
            merged.takeLast(maxPoints)
        }
    }

    private data class BaseSample(
        val timeOffsetMs: Long,
        val distanceMeters: Int,
        val speedKmh: Float,
        val latitude: Double,
        val longitude: Double
    )

    private data class SamplingTier(
        val maxAgeMs: Long,
        val intervalMs: Long
    )
}
