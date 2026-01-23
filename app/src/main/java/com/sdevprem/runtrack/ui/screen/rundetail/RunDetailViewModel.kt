package com.sdevprem.runtrack.ui.screen.rundetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdevprem.runtrack.common.utils.RouteEncodingUtils
import com.sdevprem.runtrack.data.repository.AppRepository
import com.sdevprem.runtrack.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RunDetailViewModel @Inject constructor(
    repository: AppRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val runId = savedStateHandle.get<Int>(Destination.RunDetail.ARG_RUN_ID) ?: 0

    val uiState = repository.observeRunDetail(runId)
        .map { detail ->
            if (detail == null) {
                RunDetailUiState(isLoading = false)
            } else {
                RunDetailUiState(
                    isLoading = false,
                    run = detail.run,
                    oneLiner = detail.oneLiner,
                    summary = detail.summary,
                    pathPoints = RouteEncodingUtils.decodeToPathPoints(detail.run.routePoints)
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RunDetailUiState()
        )
}
