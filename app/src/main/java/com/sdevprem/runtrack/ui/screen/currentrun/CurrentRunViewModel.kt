package com.sdevprem.runtrack.ui.screen.currentrun

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdevprem.runtrack.ai.manager.AIRunningCompanionManager
import com.sdevprem.runtrack.ai.news.model.NewsPlaybackStatus
import com.sdevprem.runtrack.ai.model.AIBroadcastState
import com.sdevprem.runtrack.ai.model.AIBroadcastType
import com.sdevprem.runtrack.ai.model.RunningContext
import com.sdevprem.runtrack.ai.model.RunningState
import com.sdevprem.runtrack.ai.model.IntegratedRunState
import com.sdevprem.runtrack.ai.model.AIConnectionState
import com.sdevprem.runtrack.ai.model.RunningTrendHistory
import com.sdevprem.runtrack.ai.prompt.RunningTrendHistoryBuilder
import com.sdevprem.runtrack.ai.summary.LocalRunSummaryGenerator
import com.sdevprem.runtrack.ai.summary.RunAiAnnotationGenerator
import com.sdevprem.runtrack.common.utils.RunAiAnnotationCodec
import com.sdevprem.runtrack.common.utils.RunMetricsCalculator
import com.sdevprem.runtrack.common.utils.RunCompletionMetrics
import com.sdevprem.runtrack.common.utils.RunMetricsCodec
import com.sdevprem.runtrack.common.utils.RouteEncodingUtils
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.data.model.RunMetricsEntity
import com.sdevprem.runtrack.data.model.RunNewsHistoryEntity
import com.sdevprem.runtrack.data.model.CompletedRunBundle
import com.sdevprem.runtrack.data.repository.AppRepository
import com.sdevprem.runtrack.data.storage.RunImageStore
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CurrentRunViewModel @Inject constructor(
    private val trackingManager: TrackingManager,
    private val repository: AppRepository,
    private val runImageStore: RunImageStore,
    val batteryOptimizationManager: com.sdevprem.runtrack.background.tracking.battery.BatteryOptimizationManager,
    val aiCompanionManager: AIRunningCompanionManager,
    private val runSummaryGenerator: LocalRunSummaryGenerator,
    private val runAiAnnotationGenerator: RunAiAnnotationGenerator,
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
    val newsPlaybackState = aiCompanionManager.newsPlaybackState

    private val _runSaveState = MutableStateFlow(RunSaveState.IDLE)
    val runSaveState = _runSaveState.asStateFlow()
    
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
    private var lastRegularBroadcastTime = 0L  // 上次常规广播时间
    private val regularBroadcastInterval = 120000L // 2分钟间隔
    private var lastPaceReminderTime = 0L
    private val paceReminderInterval = 180000L // 配速提醒间隔：3分钟
    private var isFinishingRun = false
    
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
        aiCompanionManager.resetNewsSessionHistory()
        
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
        if (isFinishingRun) {
            Timber.w("finishRun already in progress, ignore duplicate request")
            return
        }
        isFinishingRun = true

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
            val completed = withTimeoutOrNull(SUMMARY_WAIT_TIMEOUT_MS) {
                while (true) {
                    val summaryState = aiCompanionManager.summaryBroadcastState.value
                    if (summaryState.broadcastState == AIBroadcastState.COMPLETED) {
                        return@withTimeoutOrNull true
                    }

                    val conn = aiConnectionState.value
                    if (conn == AIConnectionState.ERROR || conn == AIConnectionState.DISCONNECTED) {
                        return@withTimeoutOrNull false
                    }
                    delay(200L)
                }
            } == true

            if (!completed) {
                Timber.w("总结播报未完成（超时或连接中断），直接保存跑步数据")
            }

            saveRunAndFinish(bitmap)
        }
    }
    
    private fun saveRunAndFinish(bitmap: Bitmap) {
        aiCompanionManager.stopNewsReadout()
        val runState = currentRunStateWithCalories.value
        val duration = runningDurationInMillis.value
        val pathPoints = runState.currentRunState.pathPoints.toList()
        val cadenceSeries = trackingManager.getCadenceSeries()
        val strideLengthSeries = trackingManager.getStrideLengthSeries()
        val newsHistory = aiCompanionManager.consumeNewsSessionHistory()
        
        // 计算平均步频：如果跑步时间大于0，则计算平均值，否则使用当前值
        val avgStepsPerMinute = RunCompletionMetrics.averageCadence(
            totalSteps = runState.currentRunState.totalSteps,
            durationMs = duration,
            fallback = runState.currentRunState.stepsPerMinute
        )
        val averageSpeed = RunCompletionMetrics.averageSpeedKmh(
            distanceMeters = runState.currentRunState.distanceInMeters,
            durationMs = duration
        )
        val completedRun = Run(
                img = bitmap,
                avgSpeedInKMH = averageSpeed,
                distanceInMeters = runState.currentRunState.distanceInMeters,
                durationInMillis = duration,
                timestamp = Date(),
                caloriesBurned = runState.caloriesBurnt,
                totalSteps = runState.currentRunState.totalSteps,
                avgStepsPerMinute = avgStepsPerMinute,
                routePoints = RouteEncodingUtils.encodePathPoints(pathPoints)
        )
        persistCompletedRun(
            run = completedRun,
            pathPoints = pathPoints,
            duration = duration,
            cadenceSeries = cadenceSeries,
            strideLengthSeries = strideLengthSeries,
            newsHistory = newsHistory
        )
    }
    
    private fun createFinalRunningContext(): RunningContext {
        val runState = currentRunStateWithCalories.value
        val duration = runningDurationInMillis.value
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        val trendHistory = buildTrendHistory(runState, duration)
        
        return RunningContext(
            currentRunState = runState,
            durationInMillis = duration,
            isFirstRun = false,
            previousPace = previousPace,
            targetDistance = 5f,
            targetDuration = 30 * 60 * 1000L,
            weatherInfo = "",
            timeOfDay = currentTime,
            trendHistory = trendHistory
        )
    }

    private fun persistCompletedRun(
        run: Run,
        pathPoints: List<com.sdevprem.runtrack.domain.tracking.model.PathPoint>,
        duration: Long,
        cadenceSeries: List<com.sdevprem.runtrack.domain.model.MetricPoint>,
        strideLengthSeries: List<com.sdevprem.runtrack.domain.model.MetricPoint>,
        newsHistory: List<com.sdevprem.runtrack.ai.news.model.RunSessionNewsHistoryItem>
    ) {
        _runSaveState.value = RunSaveState.SAVING
        appCoroutineScope.launch(ioDispatcher) {
            var storedImagePath: String? = null
            try {
                val storedImage = runImageStore.save(run.img)
                storedImagePath = storedImage.path
                val persistedRun = run.copy(
                    img = storedImage.thumbnail,
                    imagePath = storedImage.path
                )
                val metrics = RunMetricsCalculator.calculate(
                    pathPoints = pathPoints,
                    totalDurationMs = duration
                )
                val annotations = runAiAnnotationGenerator.generate(
                    metrics = metrics,
                    pathPoints = pathPoints,
                    totalDurationMs = duration
                )
                val artifact = runSummaryGenerator.generate(runId = 0, run = persistedRun).copy(
                    traceAnnotationsJson = RunAiAnnotationCodec.encode(annotations)
                )
                repository.insertCompletedRun(
                    CompletedRunBundle(
                        run = persistedRun,
                        aiArtifact = artifact,
                        metrics = RunMetricsEntity(
                            runId = 0,
                            paceSeries = RunMetricsCodec.encodeMetricPoints(metrics.paceSeries),
                            heartRateSeries = RunMetricsCodec.encodeMetricPoints(metrics.heartRateSeries),
                            elevationSeries = RunMetricsCodec.encodeMetricPoints(metrics.elevationSeries),
                            splits = RunMetricsCodec.encodeSplits(metrics.splits),
                            cadenceSeries = RunMetricsCodec.encodeMetricPoints(cadenceSeries),
                            strideLengthSeries = RunMetricsCodec.encodeMetricPoints(strideLengthSeries)
                        ),
                        newsHistory = newsHistory.map { item ->
                            RunNewsHistoryEntity(
                                runId = 0,
                                title = item.title,
                                source = item.source,
                                publishedAtEpochMs = item.publishedAtEpochMs,
                                articleUrl = item.articleUrl,
                                playedAtEpochMs = item.playedAtEpochMs
                            )
                        }
                    )
                )
                trackingManager.stop()
                lastRegularBroadcastTime = 0L
                _integratedRunState.value = _integratedRunState.value.copy(
                    runningState = RunningState.STOPPED,
                    isGeneratingSummary = false
                )
                _runSaveState.value = RunSaveState.SAVED
            } catch (error: Exception) {
                runImageStore.delete(storedImagePath)
                Timber.e(error, "Failed to persist completed run")
                isFinishingRun = false
                _integratedRunState.value = _integratedRunState.value.copy(
                    runningState = RunningState.PAUSED,
                    isGeneratingSummary = false
                )
                _toastMessage.value = "保存跑步记录失败，请重试"
                _runSaveState.value = RunSaveState.ERROR
            }
        }
    }

    // AI陪跑相关方法
    fun connectAI() {
        aiCompanionManager.connect()
    }
    
    fun disconnectAI() {
        aiCompanionManager.disconnect()
    }

    fun toggleNewsPlayback() {
        when (newsPlaybackState.value.status) {
            NewsPlaybackStatus.RUNNING,
            NewsPlaybackStatus.FETCHING -> aiCompanionManager.pauseNewsReadout()
            NewsPlaybackStatus.PAUSED,
            NewsPlaybackStatus.INTERRUPTED -> aiCompanionManager.resumeNewsReadout()
            NewsPlaybackStatus.IDLE,
            NewsPlaybackStatus.NO_CONTENT,
            NewsPlaybackStatus.STOPPED,
            NewsPlaybackStatus.ERROR -> aiCompanionManager.startNewsReadout()
        }
    }

    fun skipNewsReadout() {
        aiCompanionManager.skipNewsReadout()
    }

    fun stopNewsReadout() {
        aiCompanionManager.stopNewsReadout()
    }
    
    // 调试方法 - 用于检查步数追踪状态
    fun debugStepTracking() {
        Timber.d("=== 步数追踪调试信息 ===")
        val currentState = currentRunStateWithCalories.value.currentRunState
        Timber.d("当前步数: ${currentState.totalSteps}")
        Timber.d("当前步频: ${currentState.stepsPerMinute}")
        Timber.d("跑步状态: ${currentState.isTracking}")
        Timber.d("距离: ${currentState.distanceInMeters}m")
        Timber.d("速度: ${currentState.speedInKMH} km/h")
        
        // 可以在这里手动设置一些测试数据
        // TODO: 如果需要的话，可以通过TrackingManager直接获取步数传感器状态
    }
    
    
    private fun handleRunningStateChange(runState: CurrentRunStateWithCalories, duration: Long) {
        if (!runState.currentRunState.isTracking) return

        val currentTime = System.currentTimeMillis()
        val currentDistance = runState.currentRunState.distanceInMeters / 1000f
        val currentPace = runState.currentRunState.speedInKMH

        // 每公里播报
        if (currentDistance > 0 && currentDistance - lastBroadcastDistance >= 1f) {
            val runningContext = createRunningContext(runState, duration)
            aiCompanionManager.triggerBroadcast(runningContext, AIBroadcastType.MILESTONE_CELEBRATION)
            lastBroadcastDistance = currentDistance
            // 标志性事件触发后，重置常规广播计时
            lastRegularBroadcastTime = currentTime
        }

        // 配速变化播报
        if (previousPace > 0) {
            val paceChange = kotlin.math.abs(currentPace - previousPace)
            if (paceChange > 2f && currentTime - lastPaceReminderTime >= paceReminderInterval) {
                val runningContext = createRunningContext(runState, duration)
                aiCompanionManager.triggerBroadcast(runningContext, AIBroadcastType.PACE_REMINDER)
                lastPaceReminderTime = currentTime
            }
        }

        previousPace = currentPace

        // 定时常规广播 - 每2分钟一次
        if (currentTime - lastRegularBroadcastTime >= regularBroadcastInterval) {
            Timber.d("触发自动AI广播，上次广播时间: $lastRegularBroadcastTime, 当前时间: $currentTime, 间隔: ${currentTime - lastRegularBroadcastTime}ms")
            val runningContext = createRunningContext(runState, duration)
            aiCompanionManager.triggerBroadcast(runningContext, AIBroadcastType.PROFESSIONAL_ADVICE)
            lastRegularBroadcastTime = currentTime
        }
    }
    
    private fun createRunningContext(
        runState: CurrentRunStateWithCalories = currentRunStateWithCalories.value,
        duration: Long = runningDurationInMillis.value
    ): RunningContext {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        val trendHistory = buildTrendHistory(runState, duration)
        
        return RunningContext(
            currentRunState = runState,
            durationInMillis = duration,
            isFirstRun = false, // 可以根据历史数据判断
            previousPace = previousPace,
            targetDistance = 5f, // 可以从用户设置中获取
            targetDuration = 30 * 60 * 1000L, // 30分钟目标
            weatherInfo = "", // 可以集成天气API
            timeOfDay = currentTime,
            trendHistory = trendHistory
        )
    }

    private fun buildTrendHistory(
        runState: CurrentRunStateWithCalories,
        duration: Long
    ): RunningTrendHistory {
        return try {
            RunningTrendHistoryBuilder.build(
                pathPoints = runState.currentRunState.pathPoints,
                totalDurationMs = duration,
                cadenceSeries = trackingManager.getCadenceSeries(),
                totalStepsSeries = trackingManager.getTotalStepsSeries()
            )
        } catch (e: Exception) {
            Timber.w(e, "构建历史趋势上下文失败，回退为空历史")
            RunningTrendHistory()
        }
    }
    
    
    override fun onCleared() {
        super.onCleared()
        // 在清除时重置AI广播间隔为默认值（2分钟）
        aiCompanionManager.setBroadcastInterval(2)
        aiCompanionManager.stopNewsReadout()
        aiCompanionManager.disconnect()
    }

    companion object {
        private const val SUMMARY_WAIT_TIMEOUT_MS = 10_000L
    }
}

enum class RunSaveState {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}
