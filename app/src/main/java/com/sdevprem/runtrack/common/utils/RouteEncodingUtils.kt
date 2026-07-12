package com.sdevprem.runtrack.common.utils

import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import java.util.Locale

object RouteEncodingUtils {

    fun encodePathPoints(pathPoints: List<PathPoint>): String {
        if (pathPoints.isEmpty()) return ""
        return pathPoints.asSequence()
            .map { point ->
                when (point) {
                    PathPoint.EmptyLocationPoint -> PAUSE_MARKER
                    is PathPoint.LocationPoint -> {
                        val info = point.locationInfo
                        val altitude = info.altitudeMeters
                            ?.let { String.format(Locale.US, "%.1f", it) }
                            ?: ""
                        "${formatCoord(info.latitude)},${formatCoord(info.longitude)},$altitude,${info.timeMs}"
                    }
                }
            }
            .joinToString(separator = ";")
    }

    fun decodePathPoints(encoded: String): List<LocationInfo> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(';').mapNotNull(::decodeLocationToken)
    }

    fun decodeToPathPoints(encoded: String): List<PathPoint> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(';').mapNotNull { token ->
            if (token.trim() == PAUSE_MARKER) {
                PathPoint.EmptyLocationPoint
            } else {
                decodeLocationToken(token)?.let { PathPoint.LocationPoint(it) }
            }
        }
    }

    private fun decodeLocationToken(token: String): LocationInfo? {
        if (token.trim() == PAUSE_MARKER) return null
        val parts = token.split(',')
        if (parts.size < 2) return null
        val lat = parts[0].trim().toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in -90.0..90.0 }
            ?: return null
        val lng = parts[1].trim().toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in -180.0..180.0 }
            ?: return null
        val altitude = parts.getOrNull(2)
            ?.trim()
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() }
        val timeMs = parts.getOrNull(3)?.trim()?.toLongOrNull() ?: 0L
        return LocationInfo(
            latitude = lat,
            longitude = lng,
            altitudeMeters = altitude,
            timeMs = timeMs
        )
    }

    private fun formatCoord(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    private const val PAUSE_MARKER = "~"
}
