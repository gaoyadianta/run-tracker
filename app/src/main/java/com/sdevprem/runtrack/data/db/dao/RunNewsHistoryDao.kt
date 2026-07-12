package com.sdevprem.runtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdevprem.runtrack.data.model.RunNewsHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunNewsHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RunNewsHistoryEntity>)

    @Query("SELECT * FROM run_news_history WHERE runId = :runId ORDER BY playedAtEpochMs ASC")
    fun observeByRunId(runId: Int): Flow<List<RunNewsHistoryEntity>>

    @Query("DELETE FROM run_news_history WHERE runId = :runId")
    suspend fun deleteByRunId(runId: Int)
}
