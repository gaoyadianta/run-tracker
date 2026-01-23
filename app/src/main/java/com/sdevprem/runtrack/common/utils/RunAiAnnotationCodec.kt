package com.sdevprem.runtrack.common.utils

import android.util.Base64
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint

object RunAiAnnotationCodec {

    fun encode(points: List<RunAiAnnotationPoint>): String {
        if (points.isEmpty()) return ""
        return points.joinToString(separator = "|") { point ->
            val encodedText = Base64.encodeToString(point.text.toByteArray(), Base64.NO_WRAP)
            "${point.timeOffsetMs},${point.latitude},${point.longitude},$encodedText"
        }
    }

    fun decode(encoded: String): List<RunAiAnnotationPoint> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split('|').mapNotNull { token ->
            val parts = token.split(',')
            if (parts.size < 4) return@mapNotNull null
            val time = parts[0].toLongOrNull() ?: return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val lng = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            val textBytes = try {
                Base64.decode(parts.drop(3).joinToString(","), Base64.NO_WRAP)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            RunAiAnnotationPoint(
                timeOffsetMs = time,
                latitude = lat,
                longitude = lng,
                text = String(textBytes)
            )
        }
    }
}
