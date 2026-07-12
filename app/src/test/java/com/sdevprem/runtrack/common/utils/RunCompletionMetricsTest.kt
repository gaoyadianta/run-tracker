package com.sdevprem.runtrack.common.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class RunCompletionMetricsTest {
    @Test
    fun `zero duration produces safe metrics`() {
        assertEquals(0f, RunCompletionMetrics.averageSpeedKmh(100, 0L))
        assertEquals(125f, RunCompletionMetrics.averageCadence(10, 0L, 125f))
    }

    @Test
    fun `average speed uses meters and milliseconds`() {
        assertEquals(10f, RunCompletionMetrics.averageSpeedKmh(1_000, 360_000L), 0.001f)
    }

    @Test
    fun `average cadence uses active duration`() {
        assertEquals(160f, RunCompletionMetrics.averageCadence(800, 300_000L, 0f), 0.001f)
    }
}
