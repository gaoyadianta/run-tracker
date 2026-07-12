package com.sdevprem.runtrack

import android.app.Application
import com.sdevprem.runtrack.background.tracking.service.notification.TrackingNotificationHelper
import com.sdevprem.runtrack.common.privacy.PrivacyConsentManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class RunTrackApp : Application() {
    @Inject
    lateinit var notificationHelper: TrackingNotificationHelper
    @Inject
    lateinit var privacyConsentManager: PrivacyConsentManager
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        notificationHelper.createNotificationChannel()
        
        // 初始化高德地图隐私合规
        initAmapPrivacyCompliance()
    }
    
    private fun initAmapPrivacyCompliance() {
        try {
            privacyConsentManager.initializeMapPrivacyState()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Amap privacy compliance")
        }
    }
}
