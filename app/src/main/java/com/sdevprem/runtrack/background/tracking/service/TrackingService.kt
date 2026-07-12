package com.sdevprem.runtrack.background.tracking.service

import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sdevprem.runtrack.background.tracking.service.notification.TrackingNotificationHelper
import com.sdevprem.runtrack.background.tracking.wakelock.WakeLockManager
import com.sdevprem.runtrack.domain.tracking.TrackingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : LifecycleService() {

    companion object {
        const val ACTION_PAUSE_TRACKING = "action_pause_tracking"
        const val ACTION_RESUME_TRACKING = "action_resume_tracking"
        const val ACTION_START_SERVICE = "action_start_service"
    }

    @Inject
    lateinit var trackingManager: TrackingManager

    @Inject
    lateinit var notificationHelper: TrackingNotificationHelper
    
    @Inject
    lateinit var wakeLockManager: WakeLockManager
    
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ensureForeground()
        when (intent?.action) {
            ACTION_PAUSE_TRACKING -> {
                observeTrackingState()
                trackingManager.pauseTracking()
                // 暂停时释放WakeLock以节省电量
                wakeLockManager.releaseWakeLock()
                Timber.d("Tracking paused, WakeLock released")
            }
            ACTION_RESUME_TRACKING -> {
                observeTrackingState()
                trackingManager.startResumeTracking()
                // 恢复时获取WakeLock
                wakeLockManager.acquireWakeLock()
                Timber.d("Tracking resumed, WakeLock acquired")
            }
            ACTION_START_SERVICE -> {
                // 启动服务时获取WakeLock
                wakeLockManager.acquireWakeLock()
                Timber.d("Tracking service started, WakeLock acquired")
                observeTrackingState()
            }
            null -> {
                lifecycleScope.launch {
                    if (trackingManager.restoreSessionIfNeeded()) {
                        if (trackingManager.currentRunState.value.isTracking) {
                            wakeLockManager.acquireWakeLock()
                        }
                        observeTrackingState()
                        Timber.d("Tracking session restored after service restart")
                    } else {
                        stopSelf(startId)
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun ensureForeground() {
        startForeground(
            TrackingNotificationHelper.TRACKING_NOTIFICATION_ID,
            notificationHelper.getDefaultNotification()
        )
    }

    private fun observeTrackingState() {
        if (job != null) return
        job = combine(
            trackingManager.trackingDurationInMs,
            trackingManager.currentRunState
        ) { duration, currentRunState ->
            // The lock uses a bounded lease. This observation runs as the timer
            // advances, so an active run renews it after timeout while paused
            // and stopped sessions cannot retain it indefinitely.
            if (currentRunState.isTracking) {
                wakeLockManager.acquireWakeLock()
            } else {
                wakeLockManager.releaseWakeLock()
            }
            notificationHelper.updateTrackingNotification(
                durationInMillis = duration,
                isTracking = currentRunState.isTracking
            )
        }.launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationHelper.removeTrackingNotification()
        
        // 服务销毁时释放WakeLock
        wakeLockManager.releaseWakeLock()
        Timber.d("Tracking service destroyed, WakeLock released")

        job?.cancel()
        job = null
    }
}
