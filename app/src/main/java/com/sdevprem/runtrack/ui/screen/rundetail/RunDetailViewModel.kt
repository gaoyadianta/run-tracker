package com.sdevprem.runtrack.ui.screen.rundetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdevprem.runtrack.common.utils.RunMetricsCodec
import com.sdevprem.runtrack.common.utils.RouteEncodingUtils
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.data.repository.AppRepository
import com.sdevprem.runtrack.domain.model.RunMetricsData
import com.sdevprem.runtrack.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
            val metrics = metricsEntity?.let {
                RunMetricsData(
                    paceSeries = RunMetricsCodec.decodeMetricPoints(it.paceSeries),
                    heartRateSeries = RunMetricsCodec.decodeMetricPoints(it.heartRateSeries),
                    elevationSeries = RunMetricsCodec.decodeMetricPoints(it.elevationSeries),
                    splits = RunMetricsCodec.decodeSplits(it.splits)
                )
            } ?: RunMetricsData()

            RunDetailUiState(
                isLoading = false,
                run = detail.run,
                oneLiner = detail.oneLiner,
                summary = detail.summary,
                pathPoints = RouteEncodingUtils.decodeToPathPoints(detail.run.routePoints),
                metrics = metrics,
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
}
