package com.sdevprem.runtrack.domain.tracking.session

import com.sdevprem.runtrack.domain.tracking.model.CurrentRunState

data class TrackingSessionCheckpoint(
    val runState: CurrentRunState,
    val durationMs: Long
)

interface TrackingSessionCheckpointStore {
    suspend fun load(): TrackingSessionCheckpoint?
    suspend fun save(checkpoint: TrackingSessionCheckpoint)
    suspend fun clear()
}
