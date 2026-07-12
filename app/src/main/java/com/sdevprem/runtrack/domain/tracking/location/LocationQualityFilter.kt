package com.sdevprem.runtrack.domain.tracking.location

import com.sdevprem.runtrack.domain.tracking.model.LocationTrackingInfo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationQualityFilter(
    private val maxAccuracyMeters: Float = 50f,
    private val minMovementMeters: Double = 2.0,
    private val maxPlausibleSpeedMetersPerSecond: Double = 15.0
) {
    fun shouldAccept(
        candidate: LocationTrackingInfo,
        previous: LocationTrackingInfo?
    ): Boolean {
        val accuracy = candidate.accuracyMeters
        if (accuracy != null && (accuracy <= 0f || accuracy > maxAccuracyMeters)) return false
        if (!candidate.speedInMS.isFinite() || candidate.speedInMS < 0f) return false
        if (previous == null) return true

        val previousTime = previous.locationInfo.timeMs
        val candidateTime = candidate.locationInfo.timeMs
        if (previousTime > 0L && candidateTime > 0L && candidateTime <= previousTime) return false

        val distanceMeters = distanceMeters(previous, candidate)
        val elapsedSeconds = if (previousTime > 0L && candidateTime > 0L) {
            (candidateTime - previousTime) / 1000.0
        } else {
            0.0
        }
        if (elapsedSeconds > 0.0 && distanceMeters / elapsedSeconds > maxPlausibleSpeedMetersPerSecond) {
            return false
        }
        if (distanceMeters < minMovementMeters && elapsedSeconds in 0.0..10.0) return false
        return true
    }

    private fun distanceMeters(
        first: LocationTrackingInfo,
        second: LocationTrackingInfo
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(first.locationInfo.latitude)
        val lat2 = Math.toRadians(second.locationInfo.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(
            second.locationInfo.longitude - first.locationInfo.longitude
        )
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return earthRadiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
