package com.sdevprem.runtrack.common.utils

import com.sdevprem.runtrack.domain.model.MetricPoint
import com.sdevprem.runtrack.domain.model.RunSplit

object RunMetricsCodec {

    fun encodeMetricPoints(points: List<MetricPoint>): String =
        points.joinToString(separator = ";") { "${it.timeOffsetMs},${it.value}" }

    fun decodeMetricPoints(encoded: String): List<MetricPoint> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(';').mapNotNull { token ->
            val parts = token.split(',')
            if (parts.size != 2) return@mapNotNull null
            val time = parts[0].trim().toLongOrNull() ?: return@mapNotNull null
            val value = parts[1].trim().toFloatOrNull() ?: return@mapNotNull null
            MetricPoint(timeOffsetMs = time, value = value)
        }
    }

    fun encodeSplits(splits: List<RunSplit>): String =
        splits.joinToString(separator = ";") {
            "${it.kmIndex},${it.durationMs},${it.paceMinPerKm},${it.distanceMeters}"
        }

    fun decodeSplits(encoded: String): List<RunSplit> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(';').mapNotNull { token ->
            val parts = token.split(',')
            if (parts.size != 4) return@mapNotNull null
            val kmIndex = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
            val durationMs = parts[1].trim().toLongOrNull() ?: return@mapNotNull null
            val pace = parts[2].trim().toFloatOrNull() ?: return@mapNotNull null
            val distance = parts[3].trim().toIntOrNull() ?: return@mapNotNull null
            RunSplit(
                kmIndex = kmIndex,
                durationMs = durationMs,
                paceMinPerKm = pace,
                distanceMeters = distance
            )
        }
    }
}
