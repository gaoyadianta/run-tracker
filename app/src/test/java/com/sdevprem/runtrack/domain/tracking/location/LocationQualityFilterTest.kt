package com.sdevprem.runtrack.domain.tracking.location

import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.LocationTrackingInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationQualityFilterTest {
    private val filter = LocationQualityFilter()

    @Test
    fun rejectsInaccurateLocation() {
        assertFalse(filter.shouldAccept(point(0.0, 0.0, 1_000L, accuracy = 80f), null))
    }

    @Test
    fun rejectsOutOfOrderLocation() {
        assertFalse(
            filter.shouldAccept(
                point(31.0, 121.0001, 1_000L),
                point(31.0, 121.0, 2_000L)
            )
        )
    }

    @Test
    fun rejectsImplausibleJump() {
        assertFalse(
            filter.shouldAccept(
                point(31.01, 121.0, 2_000L),
                point(31.0, 121.0, 1_000L)
            )
        )
    }

    @Test
    fun acceptsPlausibleRunningPoint() {
        assertTrue(
            filter.shouldAccept(
                point(31.00005, 121.0, 4_000L),
                point(31.0, 121.0, 1_000L)
            )
        )
    }

    private fun point(
        latitude: Double,
        longitude: Double,
        timeMs: Long,
        accuracy: Float = 5f
    ) = LocationTrackingInfo(
        locationInfo = LocationInfo(latitude, longitude, timeMs = timeMs),
        speedInMS = 3f,
        accuracyMeters = accuracy
    )
}
