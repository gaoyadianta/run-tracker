package com.sdevprem.runtrack.ui.screen.rundetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdevprem.runtrack.common.utils.RunAiAnnotationCodec
import com.sdevprem.runtrack.common.utils.RunMetricsCalculator
import com.sdevprem.runtrack.common.utils.RunMetricsCodec
import com.sdevprem.runtrack.common.utils.RouteEncodingUtils
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.data.repository.AppRepository
import com.sdevprem.runtrack.domain.model.MetricPoint
import com.sdevprem.runtrack.domain.model.RunMetricsData
import com.sdevprem.runtrack.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

@HiltViewModel
class RunDetailViewModel @Inject constructor(
    private val repository: AppRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val runId = savedStateHandle.get<Int>(Destination.RunDetail.ARG_RUN_ID) ?: 0
    private val compareRun = MutableStateFlow<Run?>(null)

    private val detailFlow = repository.observeRunDetail(runId)

    init {
        detailFlow
            .onEach { detail ->
                if (detail != null) {
                    compareRun.value = repository.getComparableRun(
                        runId = detail.run.id,
                        targetDistance = detail.run.distanceInMeters,
                        toleranceMeters = 200
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    val uiState = combine(
        detailFlow,
        repository.observeRunMetrics(runId),
        compareRun
    ) { detail, metricsEntity, compare ->
        if (detail == null) {
            RunDetailUiState(isLoading = false)
        } else {
            val pathPoints = RouteEncodingUtils.decodeToPathPoints(detail.run.routePoints)
            val persistedMetrics = metricsEntity?.let {
                RunMetricsData(
                    paceSeries = RunMetricsCodec.decodeMetricPoints(it.paceSeries),
                    heartRateSeries = RunMetricsCodec.decodeMetricPoints(it.heartRateSeries),
                    elevationSeries = RunMetricsCodec.decodeMetricPoints(it.elevationSeries),
                    splits = RunMetricsCodec.decodeSplits(it.splits),
                    cadenceSeries = RunMetricsCodec.decodeMetricPoints(it.cadenceSeries),
                    strideLengthSeries = RunMetricsCodec.decodeMetricPoints(it.strideLengthSeries)
                )
            } ?: RunMetricsData()
            val fallbackMetrics = RunMetricsCalculator.calculate(
                pathPoints = pathPoints,
                totalDurationMs = detail.run.durationInMillis
            )
            val metrics = persistedMetrics.copy(
                paceSeries = persistedMetrics.paceSeries.ifEmpty { fallbackMetrics.paceSeries },
                elevationSeries = persistedMetrics.elevationSeries.ifEmpty { fallbackMetrics.elevationSeries },
                splits = persistedMetrics.splits.ifEmpty { fallbackMetrics.splits }
            )
            val alignedMetrics = alignMetricsTimeAxis(
                metrics = metrics,
                runDurationMs = detail.run.durationInMillis
            )

            val annotations = RunAiAnnotationCodec.decode(detail.traceAnnotationsJson ?: "")

            RunDetailUiState(
                isLoading = false,
                run = detail.run,
                oneLiner = detail.oneLiner,
                summary = detail.summary,
                pathPoints = pathPoints,
                metrics = alignedMetrics,
                aiAnnotations = annotations,
                compareRun = compare
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RunDetailUiState()
    )

    fun deleteRun(run: Run) {
        viewModelScope.launch {
            repository.deleteRun(run)
        }
    }

    private fun alignMetricsTimeAxis(
        metrics: RunMetricsData,
        runDurationMs: Long
    ): RunMetricsData {
        return metrics.copy(
            paceSeries = alignSeriesToDuration(metrics.paceSeries, runDurationMs),
            heartRateSeries = alignSeriesToDuration(metrics.heartRateSeries, runDurationMs),
            elevationSeries = alignSeriesToDuration(metrics.elevationSeries, runDurationMs),
            cadenceSeries = alignSeriesToDuration(metrics.cadenceSeries, runDurationMs),
            strideLengthSeries = alignSeriesToDuration(metrics.strideLengthSeries, runDurationMs)
        )
    }

    private fun alignSeriesToDuration(
        points: List<MetricPoint>,
        runDurationMs: Long
    ): List<MetricPoint> {
        if (points.size <= 1) return points
        val duration = runDurationMs.coerceAtLeast(0L)
        val fallbackInterval = if (duration > 0L) {
            (duration / (points.size - 1)).coerceAtLeast(1L)
        } else {
            1_000L
        }
        val rawTimes = points.map { it.timeOffsetMs }
        val baseTime = rawTimes.firstOrNull { it > 0L } ?: rawTimes.first().coerceAtLeast(0L)
        val rebasedTimes = rawTimes.mapIndexed { index, time ->
            if (time > 0L) {
                (time - baseTime).coerceAtLeast(0L)
            } else {
                fallbackInterval * index.toLong()
            }
        }
        val normalizedTimes = normalizeOffsets(rebasedTimes, fallbackInterval)
        val adjustedTimes = if (duration > 0L) {
            scaleToDurationIfNeeded(
                offsets = normalizedTimes,
                durationMs = duration,
                fallbackInterval = fallbackInterval
            )
        } else {
            normalizedTimes
        }
        return points.mapIndexed { index, point ->
            point.copy(timeOffsetMs = adjustedTimes[index])
        }
    }

    private fun scaleToDurationIfNeeded(
        offsets: List<Long>,
        durationMs: Long,
        fallbackInterval: Long
    ): List<Long> {
        if (offsets.isEmpty()) return offsets
        val normalizedLast = offsets.last().coerceAtLeast(1L)
        val needsScale = abs(normalizedLast - durationMs) > (durationMs * 0.2f).toLong()
        if (!needsScale) {
            return offsets.map { it.coerceIn(0L, durationMs) }
        }
        val scale = durationMs.toDouble() / normalizedLast.toDouble()
        val scaled = offsets.map { offset ->
            (offset * scale).roundToLong().coerceIn(0L, durationMs)
        }
        return normalizeOffsets(
            offsets = scaled,
            fallbackInterval = fallbackInterval.coerceAtLeast(1L)
        ).map { it.coerceIn(0L, durationMs) }
    }

    private fun normalizeOffsets(
        offsets: List<Long>,
        fallbackInterval: Long
    ): List<Long> {
        if (offsets.isEmpty()) return offsets
        val safeInterval = fallbackInterval.coerceAtLeast(1L)
        val result = ArrayList<Long>(offsets.size)
        var last = offsets.first().coerceAtLeast(0L)
        result.add(last)
        for (index in 1 until offsets.size) {
            val candidate = offsets[index].coerceAtLeast(0L)
            last = if (candidate <= last) {
                last + safeInterval
            } else {
                candidate
            }
            result.add(last)
        }
        return result
    }
}
