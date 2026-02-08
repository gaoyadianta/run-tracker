package com.sdevprem.runtrack.ui.screen.rundetail

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineSpec
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.chart.layout.fullWidth
import com.patrykandpatrick.vico.compose.component.shape.shader.color
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.DefaultPointConnector
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.component.marker.MarkerComponent
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.text.TextComponent
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.ExtraStore
import com.patrykandpatrick.vico.core.model.lineSeries
import com.sdevprem.runtrack.common.utils.DateTimeUtils
import com.sdevprem.runtrack.common.utils.RunUtils
import com.sdevprem.runtrack.domain.model.MetricPoint
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint
import com.sdevprem.runtrack.domain.model.RunMetricsData
import com.sdevprem.runtrack.domain.model.RunSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
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
    modifier: Modifier = Modifier
) {
    val tabs = listOf("配速", "心率", "海拔")
    var selectedTab by remember { mutableStateOf(0) }
    val palette = rememberMetricsPalette()

    Column(modifier = modifier) {
        if (metrics.splits.isNotEmpty()) {
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
                val times = series.map { it.timeOffsetMs }
                val lastTime = times.lastOrNull()?.coerceAtLeast(1L) ?: 1L
                val highlightIndex = closestIndex(times, highlightTimeMs)
                val smoothValues = smoothSeries(
                    series,
                    windowSize = when (selectedTab) {
                        0 -> 5
                        1 -> 3
                        else -> 7
                    }
                )
                val secondaryValues = if (selectedTab == 0) {
                    val average = series.map { it.value }.average().toFloat()
                    List(series.size) { average }
                } else {
                    null
                }

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
                    palette = palette,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .height(170.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "同步轨迹：${formatTimeOffset(highlightTimeMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textMuted
                )
                Slider(
                    value = highlightTimeMs.coerceIn(0L, lastTime).toFloat(),
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
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    val extraStoreKey = remember { ExtraStore.Key<List<Long>>() }
    val modelProducer = remember { CartesianChartModelProducer.build() }
    val primaryColor = primaryLineColor
    val annotationColor = palette.annotation
    val values = remember(points) { points.map { it.value } }
    val minValue = remember(values) { values.minOrNull() ?: 0f }
    val maxValue = remember(values) { values.maxOrNull() ?: 0f }
    val axisLabel = remember(palette.axisLabel) {
        TextComponent.build {
            color = palette.axisLabel.toArgb()
            textSizeSp = 10f
        }
    }
    val marker = remember(primaryColor) {
        MarkerComponent(
            indicator = ShapeComponent(
                shape = Shapes.pillShape,
                color = primaryColor.toArgb()
            ),
            label = TextComponent.build { textSizeSp = 0f }
        )
    }
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
    val markers = remember(highlightIndex, annotationIndices, annotationMarker, marker) {
        buildMap<Float, MarkerComponent> {
            annotationIndices.forEach { index ->
                put(index.toFloat(), annotationMarker)
            }
            highlightIndex?.let { put(it.toFloat(), marker) }
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

    LaunchedEffect(points, secondaryValues, unitLabel, invert) {
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
                    updateExtras { it[extraStoreKey] = points.map { p -> p.timeOffsetMs } }
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
                    lines = lineSpecs
                ),
                startAxis = rememberStartAxis(
                    valueFormatter = rememberYAxisFormatter(
                        unitLabel = unitLabel,
                        invert = invert,
                        minValue = minValue,
                        maxValue = maxValue
                    ),
                    label = axisLabel,
                    itemPlacer = remember {
                        AxisItemPlacer.Vertical.count(count = { 5 })
                    },
                    guideline = null
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = rememberBottomAxisValueFormatter(extraStoreKey),
                    label = axisLabel,
                    itemPlacer = remember {
                        AxisItemPlacer.Horizontal.default(addExtremeLabelPadding = true)
                    },
                    guideline = null
                ),
                persistentMarkers = markers
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth(),
            horizontalLayout = HorizontalLayout.fullWidth()
        )
    }
}

@Composable
private fun rememberBottomAxisValueFormatter(
    extraStoreKey: ExtraStore.Key<List<Long>>
) = remember(extraStoreKey) {
    AxisValueFormatter<AxisPosition.Horizontal.Bottom> { x, chartValues, _ ->
        val times = chartValues.model.extraStore[extraStoreKey]
        if (x.toInt() in times.indices) {
            formatTimeOffset(times[x.toInt()])
        } else {
            ""
        }
    }
}

@Composable
private fun rememberYAxisFormatter(
    unitLabel: String,
    invert: Boolean,
    minValue: Float,
    maxValue: Float
) = remember(unitLabel, invert, minValue, maxValue) {
    AxisValueFormatter<AxisPosition.Vertical.Start> { value, _, _ ->
        val displayValue = if (invert) {
            val base = minValue + maxValue
            base - value
        } else {
            value
        }
        when (unitLabel) {
            "min/km" -> RunUtils.formatPace(displayValue)
            "bpm" -> "${displayValue.toInt()} bpm"
            "spm" -> displayValue.toInt().toString()
            else -> "${displayValue.toInt()} m"
        }
    }
}

private fun formatTimeOffset(timeOffsetMs: Long): String {
    val totalSeconds = (timeOffsetMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
private fun RunSplitsSection(
    splits: List<RunSplit>,
    palette: MetricsPalette,
    modifier: Modifier = Modifier
) {
    val paceValues = remember(splits) { splits.map { it.paceMinPerKm } }
    val fastest = paceValues.minOrNull() ?: 0f
    val slowest = paceValues.maxOrNull() ?: 0f
    val average = if (paceValues.isNotEmpty()) paceValues.average().toFloat() else 0f
    val cumulativeTimes = remember(splits) {
        val totals = ArrayList<Long>(splits.size)
        var runningTotal = 0L
        splits.forEach { split ->
            runningTotal += split.durationMs
            totals.add(runningTotal)
        }
        totals
    }

    SectionCard(
        title = "配速",
        palette = palette,
        modifier = modifier,
        trailing = "更多 >"
    ) {
        PaceSummaryRow(
            slowest = formatPaceLabel(slowest),
            average = formatPaceLabel(average),
            fastest = formatPaceLabel(fastest),
            palette = palette
        )
        Spacer(modifier = Modifier.height(12.dp))
        SplitHeaderRow(palette = palette)
        Spacer(modifier = Modifier.height(8.dp))

        splits.forEachIndexed { index, split ->
            val cumulativeTime = cumulativeTimes.getOrNull(index) ?: split.durationMs
            SplitRow(
                split = split,
                fastest = fastest,
                slowest = slowest,
                cumulativeTimeMs = cumulativeTime,
                palette = palette
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (split.distanceMeters >= 1000 && split.kmIndex % 5 == 0) {
                SplitMilestoneRow(
                    kmMark = split.kmIndex,
                    cumulativeTimeMs = cumulativeTime,
                    palette = palette
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SplitRow(
    split: RunSplit,
    fastest: Float,
    slowest: Float,
    cumulativeTimeMs: Long,
    palette: MetricsPalette
) {
    val label = if (split.distanceMeters >= 1000) {
        split.kmIndex.toString()
    } else {
        "末段"
    }
    val paceLabel = RunUtils.formatPace(split.paceMinPerKm)
    val timeLabel = DateTimeUtils.getFormattedStopwatchTime(cumulativeTimeMs)
    val barProgress = splitBarProgress(
        pace = split.paceMinPerKm,
        fastest = fastest,
        slowest = slowest
    )
    val barColor = splitBarColor(barProgress)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(36.dp)) {
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
            modifier = Modifier.widthIn(min = 64.dp)
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
        Box(
            modifier = Modifier.widthIn(min = 64.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "累计用时",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }
    }
}

@Composable
private fun SplitMilestoneRow(
    kmMark: Int,
    cumulativeTimeMs: Long,
    palette: MetricsPalette
) {
    val pace = if (kmMark > 0) (cumulativeTimeMs / 60000f) / kmMark else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${kmMark}公里总用时：${DateTimeUtils.getFormattedStopwatchTime(cumulativeTimeMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted
        )
        Text(
            text = "配速 ${formatPaceLabel(pace)}",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted
        )
    }
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
