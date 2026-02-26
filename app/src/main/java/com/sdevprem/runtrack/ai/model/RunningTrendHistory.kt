package com.sdevprem.runtrack.ai.model

import java.util.Locale

data class RunningTrendSample(
    val tSec: Int,
    val distM: Int,
    val speedKmh: Float,
    val lat: Double,
    val lon: Double,
    val cadSpm: Int?,
    val steps: Int?
)

data class RunningTrendHistory(
    val samplingPolicy: String = "",
    val originalPointCount: Int = 0,
    val sampledPointCount: Int = 0,
    val points: List<RunningTrendSample> = emptyList()
) {
    fun isEmpty(): Boolean = points.isEmpty()

    fun toPromptSection(maxRenderPoints: Int = DEFAULT_MAX_RENDER_POINTS): String {
        if (points.isEmpty()) return ""

        val renderPoints = if (points.size <= maxRenderPoints) {
            points
        } else {
            points.takeLast(maxRenderPoints)
        }

        val truncated = points.size - renderPoints.size
        val samplesText = renderPoints.joinToString(separator = ";") { sample ->
            val speed = String.format(Locale.US, "%.1f", sample.speedKmh)
            val lat = String.format(Locale.US, "%.5f", sample.lat)
            val lon = String.format(Locale.US, "%.5f", sample.lon)
            val cadence = sample.cadSpm?.toString() ?: "-"
            val steps = sample.steps?.toString() ?: "-"
            "[${sample.tSec},${sample.distM},$speed,$lat,$lon,$cadence,$steps]"
        }

        return buildString {
            append("历史趋势数据（分层降采样）:")
            append("\n采样策略：")
            append(samplingPolicy)
            append("\n原始点数：")
            append(originalPointCount)
            append("，采样后：")
            append(sampledPointCount)
            append("，已发送：")
            append(renderPoints.size)
            if (truncated > 0) {
                append("（省略更早")
                append(truncated)
                append("点）")
            }
            append("\n样本格式[tSec,distM,speedKmh,lat,lon,cadSpm,steps]：")
            append("\n")
            append(samplesText)
        }
    }

    companion object {
        private const val DEFAULT_MAX_RENDER_POINTS = 120
    }
}
