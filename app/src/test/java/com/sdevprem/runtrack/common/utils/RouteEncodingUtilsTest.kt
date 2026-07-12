package com.sdevprem.runtrack.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.PathPoint

class RouteEncodingUtilsTest {

    @Test
    fun `decode path points should skip invalid coordinates`() {
        val encoded = listOf(
            "39.904200,116.407400,52.4,1000",
            "NaN,116.407400,52.4,2000",
            "91.000000,116.407400,52.4,3000",
            "39.904200,181.000000,52.4,4000",
            "39.904200,116.407400,NaN,5000"
        ).joinToString(";")

        val points = RouteEncodingUtils.decodePathPoints(encoded)

        assertEquals(2, points.size)
        assertEquals(39.9042, points[0].latitude, 0.000001)
        assertEquals(116.4074, points[0].longitude, 0.000001)
        assertEquals(52.4, points[0].altitudeMeters ?: 0.0, 0.000001)
        assertEquals(1000L, points[0].timeMs)
        assertEquals(39.9042, points[1].latitude, 0.000001)
        assertEquals(116.4074, points[1].longitude, 0.000001)
        assertNull(points[1].altitudeMeters)
        assertEquals(5000L, points[1].timeMs)
    }

    @Test
    fun `round trip preserves pause boundaries`() {
        val original = listOf(
            PathPoint.LocationPoint(LocationInfo(31.0, 121.0, timeMs = 1_000L)),
            PathPoint.EmptyLocationPoint,
            PathPoint.LocationPoint(LocationInfo(31.001, 121.001, timeMs = 2_000L))
        )

        val decoded = RouteEncodingUtils.decodeToPathPoints(
            RouteEncodingUtils.encodePathPoints(original)
        )

        assertEquals(3, decoded.size)
        assertSame(PathPoint.EmptyLocationPoint, decoded[1])
    }
}
