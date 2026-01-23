package com.sdevprem.runtrack.common.utils

import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import java.util.Locale

object RouteEncodingUtils {

    fun encodePathPoints(pathPoints: List<PathPoint>): String {
        if (pathPoints.isEmpty()) return ""
        return pathPoints.asSequence()
            .mapNotNull { (it as? PathPoint.LocationPoint)?.locationInfo }
            .joinToString(separator = ";") { info ->
                "${formatCoord(info.latitude)},${formatCoord(info.longitude)}"
            }
    }

    fun decodePathPoints(encoded: String): List<LocationInfo> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(';')
            .mapNotNull { token ->
                val parts = token.split(',')
                if (parts.size != 2) return@mapNotNull null
                val lat = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
                val lng = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                LocationInfo(latitude = lat, longitude = lng)
            }
    }

    fun decodeToPathPoints(encoded: String): List<PathPoint> =
        decodePathPoints(encoded).map { PathPoint.LocationPoint(it) }

    private fun formatCoord(value: Double): String =
        String.format(Locale.US, "%.6f", value)
}
