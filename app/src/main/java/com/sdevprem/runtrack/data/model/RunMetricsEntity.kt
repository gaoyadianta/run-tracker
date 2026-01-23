package com.sdevprem.runtrack.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "run_metrics",
    foreignKeys = [
        ForeignKey(
            entity = Run::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RunMetricsEntity(
    @PrimaryKey
    val runId: Int,
    val paceSeries: String = "",
    val heartRateSeries: String = "",
    val elevationSeries: String = "",
    val splits: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
