package com.sdevprem.runtrack.ui.screen.currentrun

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdevprem.runtrack.ai.manager.AIRunningCompanionManager
import com.sdevprem.runtrack.ai.model.AIBroadcastType
import com.sdevprem.runtrack.ai.model.RunningContext
import com.sdevprem.runtrack.ai.model.RunningState
import com.sdevprem.runtrack.ai.model.IntegratedRunState
import com.sdevprem.runtrack.ai.model.AIConnectionState
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
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
    
    // 集成状态管理
    private val _integratedRunState = MutableStateFlow(
        IntegratedRunState(
            runningState = RunningState.STOPPED,
            aiConnectionState = AIConnectionState.DISCONNECTED
        )
    )
    val integratedRunState = _integratedRunState.asStateFlow()
    
    // Toast消息管理
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()
    
    private var lastBroadcastDistance = 0f
    private var previousPace = 0f
    
    init {
        // 初始化AI陪跑管理器
        aiCompanionManager.initialize()
        
        // 监听AI连接状态变化，同步到集成状态
        viewModelScope.launch {
            aiConnectionState.collect { aiState ->
                _integratedRunState.value = _integratedRunState.value.copy(
                    aiConnectionState = aiState
                )
            }
        }
        
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
        if (currentRunStateWithCalories.value.currentRunState.isTracking) {
            // 暂停跑步
            trackingManager.pauseTracking()
            _integratedRunState.value = _integratedRunState.value.copy(
                runningState = RunningState.PAUSED
            )
        } else {
            // 开始跑步时自动连接AI
            startRunWithAI()
        }
    }
    
    private fun startRunWithAI() {
        // 更新状态为启动中
        _integratedRunState.value = _integratedRunState.value.copy(
            runningState = RunningState.STARTING
        )
        
        // 首先开始跑步追踪
        trackingManager.startResumeTracking()
        
        // 更新状态为跑步中
        _integratedRunState.value = _integratedRunState.value.copy(
            runningState = RunningState.RUNNING
        )
        
        // 然后自动连接AI（如果启用了自动连接）
        if (_integratedRunState.value.shouldAutoConnectAI) {
            viewModelScope.launch {
                try {
                    aiCompanionManager.connect()
                } catch (e: Exception) {
                    // AI连接失败显示Toast提示，但不影响跑步
                    Timber.w(e, "AI连接失败，跑步继续进行")
                    showAIConnectionFailureToast(e)
                }
            }
        }
    }
    
    private fun showAIConnectionFailureToast(exception: Exception) {
        val errorMessage = when {
            exception.message?.contains("network", ignoreCase = true) == true -> 
                "网络连接失败，请检查网络设置"
            exception.message?.contains("permission", ignoreCase = true) == true -> 
                "缺少麦克风权限，无法启动AI陪跑"
            exception.message?.contains("config", ignoreCase = true) == true -> 
                "AI配置错误，请检查设置"
            exception.message?.contains("server", ignoreCase = true) == true -> 
                "AI服务暂时不可用，请稍后重试"
            else -> "AI连接失败：${exception.message ?: "未知错误"}"
        }
        
        _toastMessage.value = errorMessage
    }
    
    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun finishRun(bitmap: Bitmap) {
        trackingManager.pauseTracking()
        
        // 更新状态为结束中
        _integratedRunState.value = _integratedRunState.value.copy(
            runningState = RunningState.FINISHING
        )
        
        // 如果AI连接着，先生成总结
        if (aiConnectionState.value == AIConnectionState.CONNECTED) {
            generateRunSummaryAndWaitForCompletion(bitmap)
        } else {
            // 直接保存跑步数据并结束
            saveRunAndFinish(bitmap)
        }
    }
    
    private fun generateRunSummaryAndWaitForCompletion(bitmap: Bitmap) {
        val runningContext = createFinalRunningContext()
        
        // 更新状态表示正在生成总结
        _integratedRunState.value = _integratedRunState.value.copy(
            isGeneratingSummary = true
        )
        
        // 触发AI总结播报
        aiCompanionManager.triggerRunSummary(runningContext)
        
        // 监听总结播报完成
        viewModelScope.launch {
            aiCompanionManager.summaryBroadcastState
                .filter { it.broadcastState == com.sdevprem.runtrack.ai.model.AIBroadcastState.COMPLETED }
                .first()
            
            Timber.d("总结播报完成，开始保存跑步数据")
            saveRunAndFinish(bitmap)
        }
    }
    
    private fun saveRunAndFinish(bitmap: Bitmap) {
        val runState = currentRunStateWithCalories.value
        val duration = runningDurationInMillis.value
        
        // 计算平均步频：如果跑步时间大于0，则计算平均值，否则使用当前值
        val avgStepsPerMinute = if (duration > 0 && runState.currentRunState.totalSteps > 0) {
            (runState.currentRunState.totalSteps.toFloat() / (duration / 60000f))
        } else {
            runState.currentRunState.stepsPerMinute
        }
        
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
                caloriesBurned = currentRunStateWithCalories.value.caloriesBurnt,
                totalSteps = runState.currentRunState.totalSteps,
                avgStepsPerMinute = avgStepsPerMinute
            )
        )
        
        // 更新状态为已停止
        _integratedRunState.value = _integratedRunState.value.copy(
            runningState = RunningState.STOPPED,
            isGeneratingSummary = false
        )
        
        trackingManager.stop()
        
        // AI连接的断开由AIRunningCompanionManager自动处理
    }
    
    private fun createFinalRunningContext(): RunningContext {
        val runState = currentRunStateWithCalories.value
        val duration = runningDurationInMillis.value
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        
        return RunningContext(
            currentRunState = runState,
            durationInMillis = duration,
            isFirstRun = false,
            previousPace = previousPace,
            targetDistance = 5f,
            targetDuration = 30 * 60 * 1000L,
            weatherInfo = "",
            timeOfDay = currentTime
        )
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