package com.sdevprem.runtrack

import android.app.Application
import com.amap.api.maps.MapsInitializer
import com.sdevprem.runtrack.background.tracking.service.notification.TrackingNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class RunTrackApp : Application() {
    @Inject
    lateinit var notificationHelper: TrackingNotificationHelper
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        notificationHelper.createNotificationChannel()
        
        // 初始化高德地图隐私合规
        initAmapPrivacyCompliance()
    }
    
    private fun initAmapPrivacyCompliance() {
        try {
            // 设置隐私权政策是否弹窗告知用户
            MapsInitializer.updatePrivacyShow(this, true, true)
            // 设置隐私权政策是否取得用户同意
            MapsInitializer.updatePrivacyAgree(this, true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Amap privacy compliance")
        }
    }
}