package com.sdevprem.runtrack.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "run_news_history",
    foreignKeys = [
        ForeignKey(
            entity = Run::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["runId"]),
        Index(value = ["playedAtEpochMs"])
    ]
)
data class RunNewsHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val runId: Int,
    val title: String,
    val source: String,
    val publishedAtEpochMs: Long?,
    val articleUrl: String,
    val playedAtEpochMs: Long,
    val briefText: String = "",
    val completed: Boolean = false
)
