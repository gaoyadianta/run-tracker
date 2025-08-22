package com.sdevprem.runtrack.domain.tracking.step

import com.sdevprem.runtrack.domain.tracking.model.StepTrackingInfo
import kotlinx.coroutines.flow.StateFlow

interface StepTrackingManager {
    val stepTrackingInfo: StateFlow<StepTrackingInfo>
    
    fun startStepTracking(callback: StepCallback)
    fun stopStepTracking()
    fun resetStepTracking()
    
    interface StepCallback {
        fun onStepUpdate(stepInfo: StepTrackingInfo)
    }
}