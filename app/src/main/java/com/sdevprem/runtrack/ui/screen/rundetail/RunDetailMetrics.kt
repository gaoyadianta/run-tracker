package com.sdevprem.runtrack.ui.screen.rundetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineSpec
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.chart.layout.fullWidth
import com.patrykandpatrick.vico.compose.chart.scroll.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.chart.zoom.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.component.shape.shader.color
import com.patrykandpatrick.vico.core.chart.DefaultPointConnector
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.component.marker.MarkerComponent
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.text.TextComponent
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.chart.values.AxisValueOverrider
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.lineSeries
import com.patrykandpatrick.vico.core.zoom.Zoom
import com.sdevprem.runtrack.common.utils.DateTimeUtils
import com.sdevprem.runtrack.common.utils.RunUtils
import com.sdevprem.runtrack.domain.model.MetricPoint
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint
import com.sdevprem.runtrack.domain.model.RunMetricsData
import com.sdevprem.runtrack.domain.model.RunSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.foundation.gestures.detectTapGestures

private data class MetricsPalette(
    val cardBackground: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val chipContainer: Color,
    val chipSelected: Color,
    val sliderInactive: Color,
    val axisLabel: Color,
    val annotation: Color
)

@Composable
private fun rememberMetricsPalette(): MetricsPalette {
    val isDark = isSystemInDarkTheme()
    val colors = MaterialTheme.colorScheme
    val cardBackground = if (isDark) {
        Color(0xFF2A2F36)
    } else {
        colors.surfaceVariant
    }
    return MetricsPalette(
        cardBackground = cardBackground,
        textPrimary = if (isDark) Color(0xFFE7ECF2) else colors.onSurface,
        textMuted = if (isDark) Color(0xFFA7B0BA) else colors.onSurfaceVariant,
        chipContainer = if (isDark) Color(0xFF242A31) else colors.surface,
        chipSelected = if (isDark) Color(0xFF3A4048) else colors.secondaryContainer,
        sliderInactive = if (isDark) Color(0xFF3A4048) else colors.surfaceVariant,
        axisLabel = if (isDark) Color(0xFF87909A) else colors.onSurfaceVariant,
        annotation = if (isDark) Color(0xFF6B737C) else colors.onSurfaceVariant.copy(alpha = 0.5f)
    )
}

private val PaceFastColor = Color(0xFF4CD27F)
private val PaceMidColor = Color(0xFFF2C14E)
private val PaceSlowColor = Color(0xFFE45A5A)
private val PaceNeutralColor = Color(0xFF9CA6B5)
private val PaceLineColor = Color(0xFF4AA3FF)
private val PaceAverageLineColor = Color(0xFFF7C54B)
private val HeartLineColor = Color(0xFFE45A5A)
private val ElevationLineColor = Color(0xFF4CD27F)
private val CadenceLineColor = Color(0xFFF2C14E)
private val StrideLineColor = Color(0xFF4AA3FF)

@Composable
fun RunMetricsSection(
    metrics: RunMetricsData,
    annotations: List<RunAiAnnotationPoint>,
    highlightTimeMs: Long,
    onHighlightTimeChange: (Long) -> Unit,
    runDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("配速", "心率", "海拔")
    var selectedTab by remember { mutableStateOf(0) }
    val palette = rememberMetricsPalette()

    Column(modifier = modifier) {
        if (metrics.splits.isNotEmpty()) {
            PaceOverviewSection(splits = metrics.splits, palette = palette)
            Spacer(modifier = Modifier.height(16.dp))
            RunSplitsSection(splits = metrics.splits, palette = palette)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (metrics.cadenceSeries.isNotEmpty() || metrics.strideLengthSeries.isNotEmpty()) {
            CadenceStrideSection(
                cadenceSeries = metrics.cadenceSeries,
                strideLengthSeries = metrics.strideLengthSeries,
                palette = palette
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = palette.chipSelected,
            selectedLabelColor = palette.textPrimary,
            containerColor = palette.chipContainer,
            labelColor = palette.textMuted
        )

        SectionCard(
            title = "数据趋势",
            palette = palette
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { index, title ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = chipColors,
                        shape = RoundedCornerShape(50),
                        label = { Text(text = title) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            val series = when (selectedTab) {
                0 -> metrics.paceSeries
                1 -> metrics.heartRateSeries
                else -> metrics.elevationSeries
            }
            val annotationTimes = remember(annotations) {
                annotations.map { it.timeOffsetMs }.distinct()
            }
            val unitLabel = when (selectedTab) {
                0 -> "min/km"
                1 -> "bpm"
                else -> "m"
            }
            val unitLabelText = when (selectedTab) {
                0 -> "配速"
                1 -> "心率"
                else -> "海拔"
            }
            val primaryLineColor = when (selectedTab) {
                0 -> PaceLineColor
                1 -> HeartLineColor
                else -> ElevationLineColor
            }
            val secondaryLineColor = if (selectedTab == 0) {
                PaceAverageLineColor
            } else {
                primaryLineColor.copy(alpha = 0.35f)
            }

            if (series.isEmpty()) {
                Text(
                    text = "暂无${unitLabelText}数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted
                )
            } else {
                val isPace = selectedTab == 0
                val times = buildUniformTimeline(
                    pointCount = series.size,
                    rawTimes = series.map { it.timeOffsetMs },
                    preferredDurationMs = runDurationMs
                )
                val lastTime = times.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                val clampedHighlightTime = highlightTimeMs.coerceIn(0L, lastTime)
                val highlightIndex = resolveHighlightIndex(
                    times = times,
                    highlightTimeMs = clampedHighlightTime,
                    fallbackMaxTime = lastTime
                )
                val smoothValues = smoothSeries(
                    series,
                    windowSize = when (selectedTab) {
                        0 -> 5
                        1 -> 3
                        else -> 7
                    }
                )
                val secondaryValues = if (selectedTab == 0) {
                    null
                } else {
                    null
                }
                val averageReferenceValue = if (selectedTab == 0) {
                    series.map { it.value }.average().toFloat()
                } else {
                    null
                }
                val smoothChartValues = smoothValues.map { it.value }
                val chartMinValue = smoothChartValues.minOrNull() ?: 0f
                val chartMaxValue = smoothChartValues.maxOrNull() ?: 0f
                val mappedMinValue = if (chartMinValue == chartMaxValue) {
                    chartMinValue - 0.5f
                } else {
                    chartMinValue
                }
                val mappedMaxValue = if (chartMinValue == chartMaxValue) {
                    chartMaxValue + 0.5f
                } else {
                    chartMaxValue
                }
                val paceInvertBase = chartMinValue + chartMaxValue

                val yAxisWidth = 42.dp
                val yAxisGap = 4.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .height(170.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    YAxisStrip(
                        unitLabel = unitLabel,
                        invert = isPace,
                        mappedMinValue = mappedMinValue,
                        mappedMaxValue = mappedMaxValue,
                        invertBase = paceInvertBase,
                        palette = palette,
                        modifier = Modifier
                            .width(yAxisWidth)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(yAxisGap))
                    RunMetricsChart(
                        points = smoothValues,
                        secondaryValues = secondaryValues,
                        unitLabel = unitLabel,
                        invert = isPace,
                        times = times,
                        annotationTimes = annotationTimes,
                        highlightIndex = highlightIndex,
                        onPointSelected = onHighlightTimeChange,
                        primaryLineColor = primaryLineColor,
                        secondaryLineColor = secondaryLineColor,
                        averageReferenceValue = averageReferenceValue,
                        palette = palette,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(yAxisWidth + yAxisGap))
                    TimeAxisStrip(
                        maxTimeMs = lastTime,
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (selectedTab == 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "蓝线：配速走势  黄虚线：平均配速参考线",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "同步轨迹：${formatTimeOffset(clampedHighlightTime)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textMuted
                )
                Slider(
                    value = clampedHighlightTime.toFloat(),
                    valueRange = 0f..lastTime.toFloat(),
                    onValueChange = { onHighlightTimeChange(it.toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = primaryLineColor,
                        activeTrackColor = primaryLineColor,
                        inactiveTrackColor = palette.sliderInactive
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    palette: MetricsPalette,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.cardBackground,
            contentColor = palette.textPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )
                if (!trailing.isNullOrBlank()) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun CadenceStrideSection(
    cadenceSeries: List<MetricPoint>,
    strideLengthSeries: List<MetricPoint>,
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    val cadenceValues = cadenceSeries.map { it.value }.filter { it > 0f }
    val strideValues = strideLengthSeries.map { it.value }.filter { it > 0f }
    val avgCadence = cadenceValues.average().toFloat().takeIf { it.isFinite() } ?: 0f
    val avgStride = strideValues.average().toFloat().takeIf { it.isFinite() } ?: 0f
    val plotSize = minOf(cadenceSeries.size, strideLengthSeries.size)
    val cadencePlot = if (plotSize > 0) cadenceSeries.take(plotSize) else emptyList()
    val stridePlot = if (plotSize > 0) strideLengthSeries.take(plotSize) else emptyList()
    val strideScaled = stridePlot.map { it.value * 100f }
    val times = cadencePlot.map { it.timeOffsetMs }
    val strideMin = strideValues.minOrNull() ?: 0f
    val strideMax = strideValues.maxOrNull() ?: 0f

    SectionCard(
        title = "步频步幅",
        palette = palette,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricSummary(
                value = formatCadence(avgCadence),
                label = "平均步频",
                dotColor = CadenceLineColor,
                palette = palette,
                modifier = Modifier.weight(1f)
            )
            MetricSummary(
                value = formatStride(avgStride),
                label = "平均步幅",
                dotColor = StrideLineColor,
                palette = palette,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (cadencePlot.isEmpty() || stridePlot.isEmpty()) {
            Text(
                text = "暂无步频/步幅数据",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted
            )
            return@SectionCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "步/分钟",
                style = MaterialTheme.typography.labelSmall,
                color = CadenceLineColor
            )
            Text(
                text = "米/步",
                style = MaterialTheme.typography.labelSmall,
                color = StrideLineColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            RunMetricsChart(
                points = cadencePlot,
                secondaryValues = strideScaled,
                unitLabel = "spm",
                invert = false,
                times = times,
                annotationTimes = emptyList(),
                highlightIndex = null,
                onPointSelected = {},
                primaryLineColor = CadenceLineColor,
                secondaryLineColor = StrideLineColor,
                palette = palette,
                modifier = Modifier.fillMaxSize()
            )

            if (strideMin > 0f && strideMax > 0f) {
                StrideAxisOverlay(
                    minValue = strideMin,
                    maxValue = strideMax,
                    textColor = palette.textMuted,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "分钟",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun MetricSummary(
    value: String,
    label: String,
    dotColor: Color,
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
            color = palette.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }
    }
}

@Composable
private fun StrideAxisOverlay(
    minValue: Float,
    maxValue: Float,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val midValue = (minValue + maxValue) / 2f
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = formatStride(maxValue),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
        Text(
            text = formatStride(midValue),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
        Text(
            text = formatStride(minValue),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun PaceSummaryRow(
    slowest: String,
    average: String,
    fastest: String,
    palette: MetricsPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SummaryItem(
            label = "最慢",
            value = slowest,
            valueColor = PaceSlowColor,
            textMuted = palette.textMuted,
            modifier = Modifier.weight(1f)
        )
        SummaryItem(
            label = "平均",
            value = average,
            valueColor = palette.textPrimary,
            textMuted = palette.textMuted,
            modifier = Modifier.weight(1f)
        )
        SummaryItem(
            label = "最快",
            value = fastest,
            valueColor = PaceFastColor,
            textMuted = palette.textMuted,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    valueColor: Color,
    textMuted: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textMuted
        )
    }
}

private fun formatPaceLabel(pace: Float): String {
    return if (pace > 0f) RunUtils.formatPace(pace) else "--"
}

private fun formatCadence(cadence: Float): String {
    return if (cadence > 0f) cadence.roundToInt().toString() else "--"
}

private fun formatStride(stride: Float): String {
    return if (stride > 0f) String.format(Locale.US, "%.2f", stride) else "--"
}

private fun splitBarProgress(
    pace: Float,
    fastest: Float,
    slowest: Float
): Float {
    if (pace <= 0f) return 0.3f
    val range = (slowest - fastest).coerceAtLeast(0.01f)
    val normalized = ((slowest - pace) / range).coerceIn(0f, 1f)
    return (0.3f + normalized * 0.7f).coerceIn(0.25f, 1f)
}

private fun splitBarColor(progress: Float): Color = when {
    progress >= 0.66f -> PaceFastColor
    progress >= 0.4f -> PaceMidColor
    else -> PaceSlowColor
}

@Composable
private fun RunMetricsChart(
    points: List<MetricPoint>,
    secondaryValues: List<Float>?,
    unitLabel: String,
    invert: Boolean,
    times: List<Long>,
    annotationTimes: List<Long>,
    highlightIndex: Int?,
    onPointSelected: (Long) -> Unit,
    primaryLineColor: Color,
    secondaryLineColor: Color,
    averageReferenceValue: Float? = null,
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer.build() }
    val primaryColor = primaryLineColor
    val annotationColor = palette.annotation
    val values = remember(points) { points.map { it.value } }
    val minValue = remember(values) { values.minOrNull() ?: 0f }
    val maxValue = remember(values) { values.maxOrNull() ?: 0f }
    val annotationMarker = remember(annotationColor) {
        MarkerComponent(
            indicator = ShapeComponent(
                shape = Shapes.pillShape,
                color = annotationColor.toArgb()
            ),
            label = TextComponent.build { textSizeSp = 0f }
        )
    }
    val annotationIndices = remember(annotationTimes, times) {
        annotationTimes.mapNotNull { time ->
            closestIndex(times, time)
        }.distinct()
    }
    val markers = remember(annotationIndices, annotationMarker) {
        buildMap<Float, MarkerComponent> {
            annotationIndices.forEach { index ->
                put(index.toFloat(), annotationMarker)
            }
        }
    }
    var chartWidthPx by remember { mutableStateOf(0) }
    val primarySpec = rememberLineSpec(
        shader = DynamicShaders.color(primaryColor),
        pointConnector = DefaultPointConnector(cubicStrength = 0.45f)
    )
    val secondarySpec = rememberLineSpec(
        shader = DynamicShaders.color(secondaryLineColor),
        pointConnector = DefaultPointConnector(cubicStrength = 0.45f)
    )
    val lineSpecs = if (secondaryValues != null) listOf(primarySpec, secondarySpec) else listOf(primarySpec)
    val yRangeMin = remember(minValue, maxValue) {
        if (minValue == maxValue) minValue - 0.5f else minValue
    }
    val yRangeMax = remember(minValue, maxValue) {
        if (minValue == maxValue) maxValue + 0.5f else maxValue
    }
    val axisValueOverrider = remember(points.size, yRangeMin, yRangeMax) {
        AxisValueOverrider.fixed(
            minX = 0f,
            maxX = points.lastIndex.coerceAtLeast(1).toFloat(),
            minY = yRangeMin,
            maxY = yRangeMax
        )
    }
    val chartScrollState = rememberVicoScrollState(scrollEnabled = false)
    val chartZoomState = rememberVicoZoomState(
        zoomEnabled = false,
        initialZoom = Zoom.Content,
        minZoom = Zoom.Content,
        maxZoom = Zoom.Content
    )

    LaunchedEffect(points, secondaryValues, unitLabel, invert, times) {
        withContext(Dispatchers.Default) {
            modelProducer.tryRunTransaction {
                val mappedValues = if (invert) {
                    val base = minValue + maxValue
                    points.map { base - it.value }
                } else {
                    points.map { it.value }
                }
                val mappedSecondaryValues = secondaryValues?.let { values ->
                    if (invert) {
                        val base = minValue + maxValue
                        values.map { base - it }
                    } else {
                        values
                    }
                }
                lineSeries {
                    series(mappedValues)
                    mappedSecondaryValues?.let { series(it) }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { chartWidthPx = it.width }
            .pointerInput(times) {
                detectTapGestures { offset ->
                    if (times.isEmpty() || chartWidthPx <= 0) return@detectTapGestures
                    val ratio = (offset.x / chartWidthPx).coerceIn(0f, 1f)
                    val index = ((times.size - 1) * ratio).roundToInt()
                        .coerceIn(0, times.lastIndex)
                    onPointSelected(times[index])
                }
            }
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lines = lineSpecs,
                    axisValueOverrider = axisValueOverrider
                ),
                persistentMarkers = markers
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth(),
            scrollState = chartScrollState,
            zoomState = chartZoomState,
            horizontalLayout = HorizontalLayout.fullWidth()
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val index = highlightIndex ?: return@Canvas
            if (index !in points.indices) return@Canvas
            if (points.size <= 1) return@Canvas

            val maxIndex = points.lastIndex.coerceAtLeast(1)
            val x = size.width * (index.toFloat() / maxIndex.toFloat())
            val sourceValues = points.map { point ->
                if (invert) {
                    val base = minValue + maxValue
                    base - point.value
                } else {
                    point.value
                }
            }
            val sourceMin = yRangeMin
            val sourceMax = yRangeMax
            val sourceRange = (sourceMax - sourceMin).coerceAtLeast(0.0001f)
            averageReferenceValue?.let { average ->
                val mappedAverage = if (invert) {
                    val base = minValue + maxValue
                    base - average
                } else {
                    average
                }
                val averageNormalized = ((mappedAverage - sourceMin) / sourceRange).coerceIn(0f, 1f)
                val averageY = size.height * (1f - averageNormalized)
                drawLine(
                    color = secondaryLineColor,
                    start = Offset(0f, averageY),
                    end = Offset(size.width, averageY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(5.dp.toPx(), 3.dp.toPx()),
                        phase = 0f
                    )
                )
            }
            val normalized = ((sourceValues[index] - sourceMin) / sourceRange).coerceIn(0f, 1f)
            val y = size.height * (1f - normalized)

            drawLine(
                color = primaryLineColor.copy(alpha = 0.35f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5.dp.toPx()
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = primaryLineColor,
                radius = 2.8.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun TimeAxisStrip(
    maxTimeMs: Long,
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    val safeMax = maxTimeMs.coerceAtLeast(1L)
    val mid = safeMax / 2
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatTimeOffset(0L),
            style = MaterialTheme.typography.labelSmall,
            color = palette.axisLabel
        )
        Text(
            text = formatTimeOffset(mid),
            style = MaterialTheme.typography.labelSmall,
            color = palette.axisLabel
        )
        Text(
            text = formatTimeOffset(safeMax),
            style = MaterialTheme.typography.labelSmall,
            color = palette.axisLabel
        )
    }
}

@Composable
private fun YAxisStrip(
    unitLabel: String,
    invert: Boolean,
    mappedMinValue: Float,
    mappedMaxValue: Float,
    invertBase: Float,
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    val tickCount = 5
    val range = (mappedMaxValue - mappedMinValue).coerceAtLeast(0.0001f)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        repeat(tickCount) { index ->
            val ratio = if (tickCount == 1) 0f else index.toFloat() / (tickCount - 1).toFloat()
            val mappedValue = mappedMaxValue - (range * ratio)
            val displayValue = if (invert) {
                invertBase - mappedValue
            } else {
                mappedValue
            }
            Text(
                text = formatYAxisValue(
                    value = displayValue,
                    unitLabel = unitLabel
                ),
                style = MaterialTheme.typography.labelSmall,
                color = palette.axisLabel,
                maxLines = 1
            )
        }
    }
}

private fun formatYAxisValue(
    value: Float,
    unitLabel: String
): String = when (unitLabel) {
    "min/km" -> RunUtils.formatPace(value.coerceAtLeast(0f))
    "bpm" -> value.roundToInt().toString()
    "spm" -> value.roundToInt().toString()
    else -> value.roundToInt().toString()
}

private fun formatTimeOffset(timeOffsetMs: Long): String {
    val totalSeconds = (timeOffsetMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
private fun PaceOverviewSection(
    splits: List<RunSplit>,
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    val fullKmSplits = remember(splits) { splits.filter { it.distanceMeters >= 1000 } }
    val sourceSplits = if (fullKmSplits.isNotEmpty()) fullKmSplits else splits
    val paceValues = sourceSplits.map { it.paceMinPerKm }.filter { it > 0f }

    SectionCard(
        title = "配速概览",
        palette = palette,
        modifier = modifier
    ) {
        if (paceValues.isEmpty()) {
            Text(
                text = "暂无可用配速数据",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted
            )
            return@SectionCard
        }

        val fastest = paceValues.minOrNull() ?: 0f
        val slowest = paceValues.maxOrNull() ?: 0f
        val paceRange = (slowest - fastest).coerceAtLeast(0.01f)
        val overviewHint = buildPaceOverviewHint(sourceSplits)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "慢",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
            Text(
                text = "快",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(PaceSlowColor, PaceMidColor, PaceFastColor)
                    )
                )
        ) {
            sourceSplits.forEach { split ->
                val position = ((slowest - split.paceMinPerKm) / paceRange).coerceIn(0f, 1f)
                val x = position * size.width
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = Offset(x = x, y = 0f),
                    end = Offset(x = x, y = size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = overviewHint,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted
        )
    }
}

private fun buildPaceOverviewHint(splits: List<RunSplit>): String {
    if (splits.size < 3) {
        return "公里段样本较少，继续积累可获得更稳定的节奏分析。"
    }
    val paces = splits.map { it.paceMinPerKm }.filter { it > 0f }
    if (paces.size < 3) {
        return "公里段样本较少，继续积累可获得更稳定的节奏分析。"
    }
    val fastest = paces.minOrNull() ?: return ""
    val slowest = paces.maxOrNull() ?: return ""
    val spread = slowest - fastest
    val half = (paces.size / 2).coerceAtLeast(1)
    val firstHalfAvg = paces.take(half).average().toFloat()
    val secondHalfAvg = paces.takeLast(half).average().toFloat()

    return when {
        spread <= 0.35f -> "整体接近匀速跑，节奏分布较稳定。"
        secondHalfAvg - firstHalfAvg > 0.25f -> "前快后慢，后程出现明显掉速。"
        firstHalfAvg - secondHalfAvg > 0.25f -> "后程提速明显，节奏后半段更积极。"
        else -> "节奏波动较大，建议训练中加强配速控制。"
    }
}

@Composable
private fun RunSplitsSection(
    splits: List<RunSplit>,
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    val fullKmSplits = remember(splits) { splits.filter { it.distanceMeters >= 1000 } }
    val tailSplit = remember(splits) { splits.lastOrNull()?.takeIf { it.distanceMeters < 1000 } }
    val displaySplits = if (fullKmSplits.isNotEmpty()) fullKmSplits else splits
    val paceValues = displaySplits.map { it.paceMinPerKm }.filter { it > 0f }
    val fastest = paceValues.minOrNull() ?: 0f
    val slowest = paceValues.maxOrNull() ?: 0f
    val slowestIndex = displaySplits.indices.maxByOrNull { displaySplits[it].paceMinPerKm } ?: -1
    val cumulativeTimes = remember(displaySplits) {
        val totals = ArrayList<Long>(displaySplits.size)
        var runningTotal = 0L
        displaySplits.forEach { split ->
            runningTotal += split.durationMs
            totals.add(runningTotal)
        }
        totals
    }
    val firstBlock = displaySplits.take(5)
    val lastBlock = displaySplits.takeLast(5)

    SectionCard(
        title = "公里分段分析",
        palette = palette,
        modifier = modifier
    ) {
        SplitAverageSummaryRow(
            firstHalfLabel = if (firstBlock.size >= 5) "前5公里" else "前${firstBlock.size}公里",
            firstHalfValue = formatPaceLabel(averagePace(firstBlock) ?: 0f),
            secondHalfLabel = if (lastBlock.size >= 5) "后5公里" else "后${lastBlock.size}公里",
            secondHalfValue = formatPaceLabel(averagePace(lastBlock) ?: 0f),
            overallLabel = "${displaySplits.size}公里均配",
            overallValue = formatPaceLabel(averagePace(displaySplits) ?: 0f),
            palette = palette
        )
        Spacer(modifier = Modifier.height(12.dp))
        SplitHeaderRow(palette = palette)
        Spacer(modifier = Modifier.height(8.dp))

        displaySplits.forEachIndexed { index, split ->
            val cumulativeTime = cumulativeTimes.getOrNull(index) ?: split.durationMs
            SplitRow(
                split = split,
                previousSplit = displaySplits.getOrNull(index - 1),
                fastest = fastest,
                slowest = slowest,
                cumulativeTimeMs = cumulativeTime,
                isSlowest = index == slowestIndex,
                palette = palette
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        tailSplit?.let { tail ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "最后${tail.distanceMeters}m补段：配速 ${formatPaceLabel(tail.paceMinPerKm)}，用时 ${DateTimeUtils.getFormattedStopwatchTime(tail.durationMs)}（不足1公里，未参与整公里趋势对比）",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }
    }
}

@Composable
private fun SplitAverageSummaryRow(
    firstHalfLabel: String,
    firstHalfValue: String,
    secondHalfLabel: String,
    secondHalfValue: String,
    overallLabel: String,
    overallValue: String,
    palette: MetricsPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SplitSummaryItem(
            label = firstHalfLabel,
            value = firstHalfValue,
            valueColor = PaceFastColor,
            palette = palette,
            modifier = Modifier.weight(1f)
        )
        SplitSummaryItem(
            label = secondHalfLabel,
            value = secondHalfValue,
            valueColor = PaceSlowColor,
            palette = palette,
            modifier = Modifier.weight(1f)
        )
        SplitSummaryItem(
            label = overallLabel,
            value = overallValue,
            valueColor = palette.textPrimary,
            palette = palette,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SplitSummaryItem(
    label: String,
    value: String,
    valueColor: Color,
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted
        )
    }
}

@Composable
private fun SplitRow(
    split: RunSplit,
    previousSplit: RunSplit?,
    fastest: Float,
    slowest: Float,
    cumulativeTimeMs: Long,
    isSlowest: Boolean,
    palette: MetricsPalette
) {
    val label = if (split.distanceMeters >= 1000) split.kmIndex.toString() else "末段"
    val paceLabel = RunUtils.formatPace(split.paceMinPerKm)
    val timeLabel = DateTimeUtils.getFormattedStopwatchTime(cumulativeTimeMs)
    val barProgress = splitBarProgress(
        pace = split.paceMinPerKm,
        fastest = fastest,
        slowest = slowest
    )
    val baseBarColor = splitBarColor(barProgress)
    val trend = resolveSplitTrend(previousSplit, split)
    val barColor = if (isSlowest) PaceSlowColor else baseBarColor
    val rowBackground = if (isSlowest) PaceSlowColor.copy(alpha = 0.12f) else Color.Transparent

    Surface(
        color = rowBackground,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(30.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textPrimary
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.sliderInactive)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barProgress)
                        .fillMaxSize()
                        .background(barColor)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(min = 72.dp)
            ) {
                Text(
                    text = paceLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = barColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${trend.symbol} ${trend.label}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = trend.color,
                modifier = Modifier.widthIn(min = 50.dp)
            )
        }
    }
}

@Composable
private fun SplitHeaderRow(
    palette: MetricsPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(36.dp)) {
            Text(
                text = "公里",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }
        Text(
            text = "配速(分钟/公里)",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.widthIn(min = 72.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = "累计用时",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }
        Text(
            text = "趋势",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            modifier = Modifier.widthIn(min = 50.dp)
        )
    }
}

private data class SplitTrend(
    val symbol: String,
    val label: String,
    val color: Color
)

private fun resolveSplitTrend(previousSplit: RunSplit?, currentSplit: RunSplit): SplitTrend {
    if (previousSplit == null) {
        return SplitTrend(symbol = "·", label = "起步", color = PaceNeutralColor)
    }
    val diff = currentSplit.paceMinPerKm - previousSplit.paceMinPerKm
    return when {
        diff < -0.08f -> SplitTrend(symbol = "↑", label = "提速", color = PaceFastColor)
        diff > 0.08f -> SplitTrend(symbol = "↓", label = "掉速", color = PaceSlowColor)
        else -> SplitTrend(symbol = "→", label = "平稳", color = PaceNeutralColor)
    }
}

private fun averagePace(splits: List<RunSplit>): Float? {
    val values = splits.map { it.paceMinPerKm }.filter { it > 0f }
    if (values.isEmpty()) return null
    return values.average().toFloat()
}

private fun smoothSeries(
    points: List<MetricPoint>,
    windowSize: Int
): List<MetricPoint> {
    if (points.size < 3 || windowSize <= 1) return points
    val radius = windowSize / 2
    return points.mapIndexed { index, point ->
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + radius).coerceAtMost(points.lastIndex)
        val average = points.subList(start, end + 1).map { it.value }.average().toFloat()
        point.copy(value = average)
    }
}

private fun closestIndex(times: List<Long>, target: Long): Int? {
    if (times.isEmpty()) return null
    var bestIndex = 0
    var bestDiff = Long.MAX_VALUE
    times.forEachIndexed { index, time ->
        val diff = kotlin.math.abs(time - target)
        if (diff < bestDiff) {
            bestDiff = diff
            bestIndex = index
        }
    }
    return bestIndex
}

private fun resolveHighlightIndex(
    times: List<Long>,
    highlightTimeMs: Long,
    fallbackMaxTime: Long
): Int? {
    if (times.isEmpty()) return null
    if (times.size == 1) return 0

    val hasIncreasingTimeline = times.zipWithNext().any { (a, b) -> b > a }
    if (hasIncreasingTimeline) {
        return closestIndex(times, highlightTimeMs)
    }

    val safeMax = fallbackMaxTime.coerceAtLeast(1L)
    val ratio = highlightTimeMs.coerceIn(0L, safeMax).toFloat() / safeMax.toFloat()
    return ((times.lastIndex) * ratio).roundToInt().coerceIn(0, times.lastIndex)
}

private fun buildUniformTimeline(
    pointCount: Int,
    rawTimes: List<Long>,
    preferredDurationMs: Long
): List<Long> {
    if (pointCount <= 0) return emptyList()
    if (pointCount == 1) return listOf(0L)

    val rawMax = rawTimes.maxOrNull()?.coerceAtLeast(0L) ?: 0L
    val fallbackDuration = (pointCount - 1).toLong() * 1_000L
    val spanMs = when {
        preferredDurationMs > 0L -> preferredDurationMs
        rawMax > 0L -> rawMax
        else -> fallbackDuration
    }.coerceAtLeast((pointCount - 1).toLong())

    return List(pointCount) { index ->
        (spanMs.toDouble() * index / (pointCount - 1).toDouble()).roundToLong()
    }
}
