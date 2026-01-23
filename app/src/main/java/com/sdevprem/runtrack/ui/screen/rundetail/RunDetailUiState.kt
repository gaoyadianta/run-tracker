package com.sdevprem.runtrack.ui.screen.rundetail

import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint
import com.sdevprem.runtrack.domain.model.RunMetricsData
import com.sdevprem.runtrack.domain.tracking.model.PathPoint

data class RunDetailUiState(
    val isLoading: Boolean = true,
    val run: Run? = null,
    val oneLiner: String? = null,
    val summary: String? = null,
    val pathPoints: List<PathPoint> = emptyList(),
    val metrics: RunMetricsData = RunMetricsData(),
    val aiAnnotations: List<RunAiAnnotationPoint> = emptyList(),
    val compareRun: Run? = null
)
