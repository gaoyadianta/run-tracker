package com.sdevprem.runtrack.ui.screen.currentrun

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdevprem.runtrack.ai.manager.AIRunningCompanionManager
import com.sdevprem.runtrack.ai.model.AIBroadcastType
import com.sdevprem.runtrack.ai.model.RunningContext
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.data.repository.AppRepository
import com.sdevprem.runtrack.di.ApplicationScope
import com.sdevprem.runtrack.di.IoDispatcher
import com.sdevprem.runtrack.domain.model.CurrentRunStateWithCalories
import com.sdevprem.runtrack.domain.tracking.TrackingManager
import com.sdevprem.runtrack.domain.usecase.GetCurrentRunStateWithCaloriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CurrentRunViewModel @Inject constructor(
    private val trackingManager: TrackingManager,
    private val repository: AppRepository,
    val batteryOptimizationManager: com.sdevprem.runtrack.background.tracking.battery.BatteryOptimizationManager,
    val aiCompanionManager: AIRunningCompanionManager,
    @ApplicationScope
    private val appCoroutineScope: CoroutineScope,
    @IoDispatcher
    private val ioDispatcher: CoroutineDispatcher,
    getCurrentRunStateWithCaloriesUseCase: GetCurrentRunStateWithCaloriesUseCase
) : ViewModel() {
    val currentRunStateWithCalories = getCurrentRunStateWithCaloriesUseCase()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            CurrentRunStateWithCalories()
        )
    val runningDurationInMillis = trackingManager.trackingDurationInMs
    
    // AI陪跑相关状态
    val aiConnectionState = aiCompanionManager.connectionState
    val aiLastMessage = aiCompanionManager.lastMessage
    
    private var lastBroadcastDistance = 0f
    private var previousPace = 0f
    
    init {
        // 初始化AI陪跑管理器
        aiCompanionManager.initialize()
        
        // 监听跑步状态变化，触发AI播报
        viewModelScope.launch {
            combine(
                currentRunStateWithCalories,
                runningDurationInMillis
            ) { runState, duration ->
                Pair(runState, duration)
            }.collect { (runState, duration) ->
                handleRunningStateChange(runState, duration)
            }
        }
    }

    fun startLocationAcquisition() {
        trackingManager.startLocationAcquisition()
    }

    fun playPauseTracking() {
        if (currentRunStateWithCalories.value.currentRunState.isTracking)
            trackingManager.pauseTracking()
        else trackingManager.startResumeTracking()
    }

    fun finishRun(bitmap: Bitmap) {
        trackingManager.pauseTracking()
        saveRun(
            Run(
                img = bitmap,
                avgSpeedInKMH = currentRunStateWithCalories.value.currentRunState.distanceInMeters
                    .toBigDecimal()
                    .multiply(3600.toBigDecimal())
                    .divide(runningDurationInMillis.value.toBigDecimal(), 2, RoundingMode.HALF_UP)
                    .toFloat(),
                distanceInMeters = currentRunStateWithCalories.value.currentRunState.distanceInMeters,
                durationInMillis = runningDurationInMillis.value,
                timestamp = Date(),
                caloriesBurned = currentRunStateWithCalories.value.caloriesBurnt
            )
        )
        trackingManager.stop()
    }

    private fun saveRun(run: Run) = appCoroutineScope.launch(ioDispatcher) {
        repository.insertRun(run)
    }
    
    // AI陪跑相关方法
    fun connectAI() {
        aiCompanionManager.connect()
    }
    
    fun disconnectAI() {
        aiCompanionManager.disconnect()
    }
    
    
    private fun handleRunningStateChange(runState: CurrentRunStateWithCalories, duration: Long) {
        if (!runState.currentRunState.isTracking) return
        
        val currentDistance = runState.currentRunState.distanceInMeters / 1000f
        val currentPace = runState.currentRunState.speedInKMH
        
        // 每公里播报
        if (currentDistance > 0 && currentDistance - lastBroadcastDistance >= 1f) {
            val runningContext = createRunningContext(runState, duration)
            aiCompanionManager.triggerBroadcast(runningContext, AIBroadcastType.MILESTONE_CELEBRATION)
            lastBroadcastDistance = currentDistance
        }
        
        // 配速变化播报
        if (previousPace > 0) {
            val paceChange = kotlin.math.abs(currentPace - previousPace)
            if (paceChange > 2f) { // 配速变化超过2km/h
                val runningContext = createRunningContext(runState, duration)
                aiCompanionManager.triggerBroadcast(runningContext, AIBroadcastType.PACE_REMINDER)
            }
        }
        
        previousPace = currentPace
        
        // 定时鼓励播报
        val runningContext = createRunningContext(runState, duration)
        aiCompanionManager.triggerBroadcast(runningContext)
    }
    
    private fun createRunningContext(
        runState: CurrentRunStateWithCalories = currentRunStateWithCalories.value,
        duration: Long = runningDurationInMillis.value
    ): RunningContext {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        
        return RunningContext(
            currentRunState = runState,
            durationInMillis = duration,
            isFirstRun = false, // 可以根据历史数据判断
            previousPace = previousPace,
            targetDistance = 5f, // 可以从用户设置中获取
            targetDuration = 30 * 60 * 1000L, // 30分钟目标
            weatherInfo = "", // 可以集成天气API
            timeOfDay = currentTime
        )
    }
    
    
    override fun onCleared() {
        super.onCleared()
        aiCompanionManager.disconnect()
    }
}