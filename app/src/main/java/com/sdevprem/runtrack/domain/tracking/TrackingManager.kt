package com.sdevprem.runtrack.domain.tracking

import com.sdevprem.runtrack.common.utils.LocationUtils
import com.sdevprem.runtrack.domain.tracking.background.BackgroundTrackingManager
import com.sdevprem.runtrack.domain.tracking.location.LocationTrackingManager
import com.sdevprem.runtrack.domain.tracking.location.LocationQualityFilter
import com.sdevprem.runtrack.domain.tracking.model.CurrentRunState
import com.sdevprem.runtrack.domain.tracking.model.LocationTrackingInfo
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import com.sdevprem.runtrack.domain.tracking.model.StepTrackingInfo
import com.sdevprem.runtrack.domain.tracking.step.StepTrackingManager
import com.sdevprem.runtrack.domain.tracking.timer.TimeTracker
import com.sdevprem.runtrack.domain.tracking.session.TrackingSessionCheckpoint
import com.sdevprem.runtrack.domain.tracking.session.TrackingSessionCheckpointStore
import com.sdevprem.runtrack.di.ApplicationScope
import com.sdevprem.runtrack.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import com.sdevprem.runtrack.domain.model.MetricPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingManager @Inject constructor(
    private val locationTrackingManager: LocationTrackingManager,
    private val timeTracker: TimeTracker,
    private val backgroundTrackingManager: BackgroundTrackingManager,
    private val stepTrackingManager: StepTrackingManager,
    private val checkpointStore: TrackingSessionCheckpointStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val STEP_SAMPLE_INTERVAL_MS = 5_000L
        private const val CHECKPOINT_INTERVAL_MS = 5_000L
    }

    private var isTracking = false
        set(value) {
            _currentRunState.update { it.copy(isTracking = value) }
            field = value
        }
    
    private var isLocationAcquisitionActive = false

    private val _currentRunState = MutableStateFlow(CurrentRunState())
    val currentRunState = _currentRunState

    private val _trackingDurationInMs = MutableStateFlow(0L)
    val trackingDurationInMs = _trackingDurationInMs.asStateFlow()

    private val timeTrackerCallback = { timeElapsed: Long ->
        _trackingDurationInMs.update { timeElapsed }
        if (timeElapsed - lastCheckpointDurationMs >= CHECKPOINT_INTERVAL_MS) {
            lastCheckpointDurationMs = timeElapsed
            persistCheckpoint()
        }
    }

    private val stepCallback = object : StepTrackingManager.StepCallback {
        override fun onStepUpdate(stepInfo: StepTrackingInfo) {
            Timber.d("收到步数更新: totalSteps=${stepInfo.totalSteps}, stepsPerMinute=${stepInfo.stepsPerMinute}")
            _currentRunState.update { state ->
                state.copy(
                    totalSteps = stepInfo.totalSteps,
                    stepsPerMinute = stepInfo.stepsPerMinute,
                    isStepSensorAvailable = stepInfo.isStepSensorAvailable
                )
            }
            recordStepSeries(stepInfo)
        }
    }

    private var isFirst = true
    private val locationQualityFilter = LocationQualityFilter()
    private var lastAcceptedLocation: LocationTrackingInfo? = null
    private var isBackgroundTrackingStarted = false
    private val stepSeriesLock = Any()
    private val cadenceSeries = mutableListOf<MetricPoint>()
    private val strideLengthSeries = mutableListOf<MetricPoint>()
    private val totalStepsSeries = mutableListOf<MetricPoint>()
    private var lastStepSeriesTimeMs = -STEP_SAMPLE_INTERVAL_MS
    private var lastCheckpointDurationMs = 0L
    private var checkpointJob: Job? = null
    private var sessionGeneration = 0L
    private var isStopping = false

    private val locationCallback = object : LocationTrackingManager.LocationCallback {

        override fun onLocationUpdate(results: List<LocationTrackingInfo>) {
            if (isTracking) {
                results.forEach { info ->
                    addPathPoints(info)
                    Timber.d(
                        "New LocationPoint : " +
                                "latitude: ${info.locationInfo.latitude}, " +
                                "longitude: ${info.locationInfo.longitude}"
                    )
                }
            } else if (isLocationAcquisitionActive) {
                results.forEach { info ->
                    addCurrentLocationPoint(info)
                    Timber.d(
                        "Current Location : " +
                                "latitude: ${info.locationInfo.latitude}, " +
                                "longitude: ${info.locationInfo.longitude}"
                    )
                }
            }
        }
    }

    private fun postInitialValue() {
        _currentRunState.update {
            CurrentRunState()
        }
        _trackingDurationInMs.update { 0 }
        clearStepSeries()
        lastAcceptedLocation = null
    }

    private fun addCurrentLocationPoint(info: LocationTrackingInfo) {
        if (!locationQualityFilter.shouldAccept(info, lastAcceptedLocation)) return
        lastAcceptedLocation = info
        _currentRunState.update { state ->
            state.copy(
                pathPoints = listOf(PathPoint.LocationPoint(info.locationInfo)),
                speedInKMH = (info.speedInMS * 3.6f).toBigDecimal()
                    .setScale(2, RoundingMode.HALF_UP).toFloat()
            )
        }
    }

    private fun addPathPoints(info: LocationTrackingInfo) {
        if (!locationQualityFilter.shouldAccept(info, lastAcceptedLocation)) return
        lastAcceptedLocation = info
        _currentRunState.update { state ->
            val pathPoints = state.pathPoints + PathPoint.LocationPoint(info.locationInfo)
            state.copy(
                pathPoints = pathPoints,
                distanceInMeters = state.distanceInMeters.run {
                    var distance = this
                    if (pathPoints.size > 1)
                        distance += LocationUtils.getDistanceBetweenPathPoints(
                            pathPoint1 = pathPoints[pathPoints.size - 1],
                            pathPoint2 = pathPoints[pathPoints.size - 2]
                        )
                    distance
                },
                speedInKMH = (info.speedInMS * 3.6f).toBigDecimal()
                    .setScale(2, RoundingMode.HALF_UP).toFloat()
            )
        }
    }

    fun startLocationAcquisition() {
        if (isFirst) {
            postInitialValue()
            isFirst = false
        }
        isLocationAcquisitionActive = true
        locationTrackingManager.setCallback(locationCallback)
    }

    fun startResumeTracking() {
        if (isTracking)
            return
        if (isFirst) {
            postInitialValue()
            isFirst = false
        }
        if (!isBackgroundTrackingStarted) {
            backgroundTrackingManager.startBackgroundTracking()
            isBackgroundTrackingStarted = true
        }
        // 每次开始跑步都启动步数追踪
        Timber.d("开始启动步数追踪...")
        stepTrackingManager.startStepTracking(stepCallback)
        Timber.d("步数追踪启动完成")
        
        isLocationAcquisitionActive = false
        lastAcceptedLocation = null
        isTracking = true
        timeTracker.startResumeTimer(timeTrackerCallback)
        locationTrackingManager.setCallback(locationCallback)
    }

    private fun addEmptyPolyLine() {
        _currentRunState.update {
            it.copy(
                pathPoints = it.pathPoints + PathPoint.EmptyLocationPoint
            )
        }
    }

    fun pauseTracking() {
        isTracking = false
        locationTrackingManager.removeCallback()
        timeTracker.pauseTimer()
        stepTrackingManager.stopStepTracking()
        lastAcceptedLocation = null
        addEmptyPolyLine()
        persistCheckpoint()
    }

    fun stop() {
        isStopping = true
        pauseTracking()
        sessionGeneration += 1
        val pendingCheckpoint = checkpointJob
        pendingCheckpoint?.cancel()
        isLocationAcquisitionActive = false
        backgroundTrackingManager.stopBackgroundTracking()
        isBackgroundTrackingStarted = false
        stepTrackingManager.stopStepTracking()
        stepTrackingManager.resetStepTracking()
        timeTracker.stopTimer()
        postInitialValue()
        isFirst = true
        isStopping = false
        applicationScope.launch(ioDispatcher) {
            pendingCheckpoint?.cancelAndJoin()
            checkpointStore.clear()
        }
    }

    suspend fun restoreSessionIfNeeded(): Boolean {
        if (!isFirst) return true
        val checkpoint = checkpointStore.load() ?: return false
        val restoredState = checkpoint.runState.copy(isTracking = false)
        _currentRunState.value = restoredState
        _trackingDurationInMs.value = checkpoint.durationMs.coerceAtLeast(0L)
        timeTracker.restoreElapsedTime(checkpoint.durationMs)
        stepTrackingManager.restoreStepCount(checkpoint.runState.totalSteps)
        isFirst = false
        isBackgroundTrackingStarted = true
        lastCheckpointDurationMs = checkpoint.durationMs
        lastAcceptedLocation = null

        if (checkpoint.runState.isTracking) {
            stepTrackingManager.startStepTracking(stepCallback)
            isLocationAcquisitionActive = false
            isTracking = true
            timeTracker.startResumeTimer(timeTrackerCallback)
            locationTrackingManager.setCallback(locationCallback)
        }
        return true
    }

    fun getCadenceSeries(): List<MetricPoint> = synchronized(stepSeriesLock) {
        cadenceSeries.toList()
    }

    fun getStrideLengthSeries(): List<MetricPoint> = synchronized(stepSeriesLock) {
        strideLengthSeries.toList()
    }

    fun getTotalStepsSeries(): List<MetricPoint> = synchronized(stepSeriesLock) {
        totalStepsSeries.toList()
    }

    private fun clearStepSeries() = synchronized(stepSeriesLock) {
        cadenceSeries.clear()
        strideLengthSeries.clear()
        totalStepsSeries.clear()
        lastStepSeriesTimeMs = -STEP_SAMPLE_INTERVAL_MS
    }

    private fun recordStepSeries(stepInfo: StepTrackingInfo) {
        if (!isTracking) return
        val timeOffsetMs = _trackingDurationInMs.value
        if (timeOffsetMs < 0L) return
        if (timeOffsetMs - lastStepSeriesTimeMs < STEP_SAMPLE_INTERVAL_MS) return
        val cadence = stepInfo.stepsPerMinute
        val speedMps = _currentRunState.value.speedInKMH / 3.6f
        val strideLength = if (cadence > 0f) {
            (speedMps * 60f / cadence).coerceAtLeast(0f)
        } else {
            0f
        }
        synchronized(stepSeriesLock) {
            cadenceSeries.add(
                MetricPoint(timeOffsetMs = timeOffsetMs, value = cadence.coerceAtLeast(0f))
            )
            strideLengthSeries.add(
                MetricPoint(timeOffsetMs = timeOffsetMs, value = strideLength)
            )
            totalStepsSeries.add(
                MetricPoint(timeOffsetMs = timeOffsetMs, value = stepInfo.totalSteps.toFloat())
            )
            lastStepSeriesTimeMs = timeOffsetMs
        }
    }

    private fun persistCheckpoint() {
        if (isFirst || isStopping) return
        val generation = sessionGeneration
        val checkpoint = TrackingSessionCheckpoint(
            runState = _currentRunState.value,
            durationMs = _trackingDurationInMs.value
        )
        checkpointJob?.cancel()
        checkpointJob = applicationScope.launch(ioDispatcher) {
            if (generation == sessionGeneration) {
                checkpointStore.save(checkpoint)
            }
        }
    }

}
