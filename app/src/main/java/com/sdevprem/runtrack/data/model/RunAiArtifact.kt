package com.sdevprem.runtrack.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "run_ai_artifact",
    foreignKeys = [
        ForeignKey(
            entity = Run::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RunAiArtifact(
    @PrimaryKey
    val runId: Int,
    val oneLiner: String? = null,
    val summary: String? = null,
    val traceAnnotationsJson: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
