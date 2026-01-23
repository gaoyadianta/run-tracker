package com.sdevprem.runtrack.ui.screen.rundetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.ExtraStore
import com.patrykandpatrick.vico.core.model.lineSeries
import com.sdevprem.runtrack.common.utils.DateTimeUtils
import com.sdevprem.runtrack.common.utils.RunUtils
import com.sdevprem.runtrack.domain.model.MetricPoint
import com.sdevprem.runtrack.domain.model.RunMetricsData
import com.sdevprem.runtrack.domain.model.RunSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun RunMetricsSection(
    metrics: RunMetricsData,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Pace", "Heart Rate", "Elevation")
    var selectedTab by remember { mutableStateOf(0) }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Run Analysis",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { index, title ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
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
            val unitLabel = when (selectedTab) {
                0 -> "min/km"
                1 -> "bpm"
                else -> "m"
            }
            if (series.isEmpty()) {
                Text(
                    text = "No data available for $unitLabel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                RunMetricsChart(
                    points = series,
                    unitLabel = unitLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }
    }

    if (metrics.splits.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        RunSplitsSection(splits = metrics.splits)
    }
}

@Composable
private fun RunMetricsChart(
    points: List<MetricPoint>,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    val extraStoreKey = remember { ExtraStore.Key<List<Long>>() }
    val modelProducer = remember { CartesianChartModelProducer.build() }
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(points, unitLabel) {
        withContext(Dispatchers.Default) {
            modelProducer.tryRunTransaction {
                lineSeries {
                    series(points.map { it.value })
                    updateExtras { it[extraStoreKey] = points.map { p -> p.timeOffsetMs } }
                }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lines = listOf(
                    rememberLineSpec(
                        shader = DynamicShaders.color(primaryColor),
                        pointConnector = DefaultPointConnector(cubicStrength = 0.35f)
                    )
                )
            ),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(
                valueFormatter = rememberBottomAxisValueFormatter(extraStoreKey),
                itemPlacer = remember {
                    AxisItemPlacer.Horizontal.default(addExtremeLabelPadding = true)
                },
                guideline = null
            )
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        horizontalLayout = HorizontalLayout.fullWidth()
    )
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

private fun formatTimeOffset(timeOffsetMs: Long): String {
    val totalSeconds = (timeOffsetMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
private fun RunSplitsSection(
    splits: List<RunSplit>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Splits",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            splits.forEach { split ->
                SplitRow(split = split)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SplitRow(
    split: RunSplit
) {
    val label = if (split.distanceMeters >= 1000) {
        "KM ${split.kmIndex}"
    } else {
        "Last ${split.distanceMeters} m"
    }
    val paceLabel = RunUtils.formatPace(split.paceMinPerKm)
    val timeLabel = DateTimeUtils.getFormattedStopwatchTime(split.durationMs)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.width(90.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = paceLabel,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
