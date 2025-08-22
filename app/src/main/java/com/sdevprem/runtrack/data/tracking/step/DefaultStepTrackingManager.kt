package com.sdevprem.runtrack.data.tracking.step

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    
    private val _stepTrackingInfo = MutableStateFlow(StepTrackingInfo())
    override val stepTrackingInfo = _stepTrackingInfo.asStateFlow()
    
    private var callback: StepTrackingManager.StepCallback? = null
    private var initialStepCount: Int? = null
    private var stepCountBuffer = mutableListOf<Long>() // 用于计算步频
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
        if (isTracking) return
        
        this.callback = callback
        isTracking = true
        
        val isAvailable = stepCounterSensor != null || stepDetectorSensor != null
        
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
        stepCountBuffer.clear()
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
        stepCountBuffer.add(currentTime)
        
        // 保持最近60秒的数据用于计算步频
        stepCountBuffer.removeAll { currentTime - it > 60_000 }
        
        val stepsPerMinute = if (stepCountBuffer.size > 1) {
            val timeSpanMinutes = (stepCountBuffer.last() - stepCountBuffer.first()) / 1000f / 60f
            if (timeSpanMinutes > 0) stepCountBuffer.size / timeSpanMinutes else 0f
        } else 0f
        
        val stepInfo = StepTrackingInfo(
            totalSteps = steps,
            stepsPerMinute = stepsPerMinute,
            isStepSensorAvailable = true
        )
        
        _stepTrackingInfo.value = stepInfo
        callback?.onStepUpdate(stepInfo)
    }
}