package com.sdevprem.runtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdevprem.runtrack.data.model.RunMetricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunMetricsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRunMetrics(metrics: RunMetricsEntity)

    @Query("SELECT * FROM run_metrics WHERE runId = :runId LIMIT 1")
    fun observeRunMetrics(runId: Int): Flow<RunMetricsEntity?>

    @Query("SELECT * FROM run_metrics WHERE runId = :runId LIMIT 1")
    suspend fun getRunMetrics(runId: Int): RunMetricsEntity?

    @Query("DELETE FROM run_metrics WHERE runId = :runId")
    suspend fun deleteRunMetrics(runId: Int)
}
