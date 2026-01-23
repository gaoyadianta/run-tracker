package com.sdevprem.runtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdevprem.runtrack.data.model.RunAiArtifact
import kotlinx.coroutines.flow.Flow

@Dao
interface RunAiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRunAiArtifact(artifact: RunAiArtifact)

    @Query("SELECT * FROM run_ai_artifact WHERE runId = :runId LIMIT 1")
    fun observeRunAiArtifact(runId: Int): Flow<RunAiArtifact?>

    @Query("SELECT * FROM run_ai_artifact WHERE runId = :runId LIMIT 1")
    suspend fun getRunAiArtifact(runId: Int): RunAiArtifact?

    @Query("DELETE FROM run_ai_artifact WHERE runId = :runId")
    suspend fun deleteRunAiArtifact(runId: Int)
}
