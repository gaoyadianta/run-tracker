package com.sdevprem.runtrack.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.sdevprem.runtrack.data.db.dao.RunAiDao
import com.sdevprem.runtrack.data.db.dao.RunMetricsDao
import com.sdevprem.runtrack.data.db.dao.RunDao
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.data.model.RunAiArtifact
import com.sdevprem.runtrack.data.model.RunMetricsEntity
import com.sdevprem.runtrack.data.utils.RunSortOrder
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val runDao: RunDao,
    private val runAiDao: RunAiDao,
    private val runMetricsDao: RunMetricsDao
) {
    suspend fun insertRun(run: Run): Long = runDao.insertRun(run)

    suspend fun deleteRun(run: Run) = runDao.deleteRun(run)

    fun getSortedAllRun(sortingOrder: RunSortOrder) = Pager(
        config = PagingConfig(pageSize = 20),
    ) {
        when (sortingOrder) {
            RunSortOrder.DATE -> runDao.getAllRunSortByDate()
            RunSortOrder.DURATION -> runDao.getAllRunSortByDuration()
            RunSortOrder.CALORIES_BURNED -> runDao.getAllRunSortByCaloriesBurned()
            RunSortOrder.AVG_SPEED -> runDao.getAllRunSortByAvgSpeed()
            RunSortOrder.DISTANCE -> runDao.getAllRunSortByDistance()
        }
    }

    suspend fun getRunStatsInDateRange(fromDate: Date?, toDate: Date?) =
        runDao.getRunStatsInDateRange(fromDate, toDate)

    suspend fun getComparableRun(runId: Int, targetDistance: Int, toleranceMeters: Int): Run? =
        runDao.getComparableRun(
            runId = runId,
            targetDistance = targetDistance,
            minDistance = (targetDistance - toleranceMeters).coerceAtLeast(0),
            maxDistance = targetDistance + toleranceMeters
        )

    fun getRunByDescDateWithLimit(limit: Int) = runDao.getRunByDescDateWithLimit(limit)

    fun observeRunDetail(runId: Int) = runDao.observeRunDetail(runId)

    suspend fun upsertRunAiArtifact(artifact: RunAiArtifact) =
        runAiDao.upsertRunAiArtifact(artifact)

    fun observeRunAiArtifact(runId: Int) = runAiDao.observeRunAiArtifact(runId)

    suspend fun getRunAiArtifact(runId: Int) = runAiDao.getRunAiArtifact(runId)

    suspend fun deleteRunAiArtifact(runId: Int) = runAiDao.deleteRunAiArtifact(runId)

    suspend fun upsertRunMetrics(metrics: RunMetricsEntity) =
        runMetricsDao.upsertRunMetrics(metrics)

    fun observeRunMetrics(runId: Int) = runMetricsDao.observeRunMetrics(runId)

    suspend fun getRunMetrics(runId: Int) = runMetricsDao.getRunMetrics(runId)

    suspend fun deleteRunMetrics(runId: Int) = runMetricsDao.deleteRunMetrics(runId)

    fun getTotalRunningDuration(fromDate: Date? = null, toDate: Date? = null): Flow<Long> =
        runDao.getTotalRunningDuration(fromDate, toDate)

    fun getTotalCaloriesBurned(fromDate: Date? = null, toDate: Date? = null): Flow<Long> =
        runDao.getTotalCaloriesBurned(fromDate, toDate)

    fun getTotalDistance(fromDate: Date? = null, toDate: Date? = null): Flow<Long> =
        runDao.getTotalDistance(fromDate, toDate)

    fun getTotalAvgSpeed(fromDate: Date? = null, toDate: Date? = null): Flow<Float> =
        runDao.getTotalAvgSpeed(fromDate, toDate)

}
