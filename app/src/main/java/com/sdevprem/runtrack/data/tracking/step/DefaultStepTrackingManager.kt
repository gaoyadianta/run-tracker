package com.sdevprem.runtrack.data.tracking.step

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sdevprem.runtrack.domain.tracking.model.StepTrackingInfo
import com.sdevprem.runtrack.domain.tracking.step.StepTrackingManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultStepTrackingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : StepTrackingManager {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    
    init {
        Timber.d("StepTrackingManager initialized")
        Timber.d("Available sensors:")
        sensorManager.getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
            if (sensor.type == Sensor.TYPE_STEP_COUNTER || sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                Timber.d("- ${sensor.name} (Type: ${sensor.type}, Vendor: ${sensor.vendor})")
            }
        }
        Timber.d("Step Counter Sensor: ${stepCounterSensor?.name ?: "Not available"}")
        Timber.d("Step Detector Sensor: ${stepDetectorSensor?.name ?: "Not available"}")
    }
    
    private val _stepTrackingInfo = MutableStateFlow(StepTrackingInfo())
    override val stepTrackingInfo = _stepTrackingInfo.asStateFlow()
    
    private var callback: StepTrackingManager.StepCallback? = null
    private var initialStepCount: Int? = null
    private var stepDataBuffer = mutableListOf<Pair<Long, Int>>() // 存储时间戳和对应的步数
    private var isTracking = false
    
    private val stepSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let { handleSensorEvent(it) }
        }
        
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Timber.d("Step sensor accuracy changed: $accuracy")
        }
    }
    
    override fun startStepTracking(callback: StepTrackingManager.StepCallback) {
        if (isTracking) {
            Timber.d("Step tracking already in progress")
            return
        }
        
        // 检查ACTIVITY_RECOGNITION权限
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 10 以下不需要此权限
        }
        
        Timber.d("ACTIVITY_RECOGNITION permission granted: $hasPermission")
        
        if (!hasPermission) {
            Timber.w("ACTIVITY_RECOGNITION permission not granted, step tracking cannot start")
            _stepTrackingInfo.value = _stepTrackingInfo.value.copy(
                isStepSensorAvailable = false
            )
            return
        }
        
        this.callback = callback
        isTracking = true
        
        val isAvailable = stepCounterSensor != null || stepDetectorSensor != null
        
        Timber.d("Starting step tracking...")
        Timber.d("Step sensors availability - Counter: ${stepCounterSensor != null}, Detector: ${stepDetectorSensor != null}")
        
        _stepTrackingInfo.value = _stepTrackingInfo.value.copy(
            isStepSensorAvailable = isAvailable
        )
        
        if (!isAvailable) {
            Timber.w("Step sensors not available on this device")
            return
        }
        
        // 优先使用 STEP_COUNTER，降级使用 STEP_DETECTOR
        val sensor = stepCounterSensor ?: stepDetectorSensor
        sensor?.let {
            val registered = sensorManager.registerListener(
                stepSensorListener, 
                it, 
                SensorManager.SENSOR_DELAY_UI
            )
            Timber.d("Step sensor registration result: $registered for sensor type: ${it.type}")
            if (registered) {
                Timber.d("Step tracking started successfully")
            } else {
                Timber.w("Failed to register step sensor listener")
            }
        }
    }
    
    override fun stopStepTracking() {
        if (!isTracking) return
        
        isTracking = false
        sensorManager.unregisterListener(stepSensorListener)
        callback = null
        Timber.d("Step tracking stopped")
    }
    
    override fun resetStepTracking() {
        initialStepCount = null
        stepDataBuffer.clear()
        _stepTrackingInfo.value = StepTrackingInfo()
        Timber.d("Step tracking reset")
    }
    
    private fun handleSensorEvent(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> handleStepCounter(event.values[0].toInt())
            Sensor.TYPE_STEP_DETECTOR -> handleStepDetector()
        }
    }
    
    private fun handleStepCounter(totalSystemSteps: Int) {
        if (initialStepCount == null) {
            initialStepCount = totalSystemSteps
            Timber.d("Initial step count set: $totalSystemSteps")
        }
        
        val sessionSteps = totalSystemSteps - (initialStepCount ?: 0)
        updateStepInfo(sessionSteps)
        Timber.d("Step counter update - System: $totalSystemSteps, Session: $sessionSteps")
    }
    
    private fun handleStepDetector() {
        // 对于 STEP_DETECTOR，每次调用表示检测到一步
        val currentSessionSteps = _stepTrackingInfo.value.totalSteps + 1
        updateStepInfo(currentSessionSteps)
        Timber.d("Step detector - New step detected, total: $currentSessionSteps")
    }
    
    private fun updateStepInfo(steps: Int) {
        val currentTime = System.currentTimeMillis()
        
        // 添加当前时间和步数的记录
        stepDataBuffer.add(currentTime to steps)
        
        // 保持最近60秒的数据用于计算步频
        stepDataBuffer.removeAll { (time, _) -> currentTime - time > 60_000 }
        
        val stepsPerMinute = calculateStepsPerMinute()
        
        val stepInfo = StepTrackingInfo(
            totalSteps = steps,
            stepsPerMinute = stepsPerMinute,
            isStepSensorAvailable = true
        )
        
        _stepTrackingInfo.value = stepInfo
        callback?.onStepUpdate(stepInfo)
        
        Timber.d("步频计算详情: 缓冲区大小=${stepDataBuffer.size}, 步频=$stepsPerMinute")
    }
    
    private fun calculateStepsPerMinute(): Float {
        if (stepDataBuffer.size < 2) return 0f
        
        val firstEntry = stepDataBuffer.first()
        val lastEntry = stepDataBuffer.last()
        
        val timeSpanMillis = lastEntry.first - firstEntry.first
        val timeSpanMinutes = timeSpanMillis / 1000f / 60f
        
        val stepsDifference = lastEntry.second - firstEntry.second
        
        // 避免除零和异常值
        if (timeSpanMinutes <= 0 || timeSpanMinutes > 1.0f) return 0f
        if (stepsDifference <= 0) return 0f
        
        val stepsPerMinute = stepsDifference / timeSpanMinutes
        
        Timber.d("步频计算: 步数差值=$stepsDifference, 时间跨度=${timeSpanMinutes}分钟, 步频=$stepsPerMinute")
        
        // 限制合理范围 (30-300步/分钟)
        return when {
            stepsPerMinute > 300f -> 0f  // 异常高值，返回0
            stepsPerMinute < 30f -> stepsPerMinute  // 保留低值，可能是正常的慢走
            else -> stepsPerMinute
        }
    }
}