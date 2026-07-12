package com.sdevprem.runtrack.common.utils

import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import org.junit.Assert.assertTrue
import org.junit.Test

class RunMetricsCalculatorTest {

    @Test
    fun calculate_doesNotBridgePauseBoundary() {
        val path = listOf(
            PathPoint.LocationPoint(LocationInfo(31.0, 121.0, timeMs = 1_000L)),
            PathPoint.EmptyLocationPoint,
            PathPoint.LocationPoint(LocationInfo(32.0, 122.0, timeMs = 61_000L))
        )

        val metrics = RunMetricsCalculator.calculate(path, totalDurationMs = 60_000L)

        assertTrue(metrics.paceSeries.isEmpty())
        assertTrue(metrics.elevationSeries.isEmpty())
        assertTrue(metrics.splits.isEmpty())
    }
}
