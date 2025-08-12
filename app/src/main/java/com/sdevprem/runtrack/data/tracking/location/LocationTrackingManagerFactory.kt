package com.sdevprem.runtrack.data.tracking.location

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.sdevprem.runtrack.domain.tracking.location.LocationTrackingManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationTrackingManagerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    private val locationRequest: LocationRequest
) {
    
    fun createLocationTrackingManager(): LocationTrackingManager {
        return when (detectRegion()) {
            "amap" -> AmapLocationTrackingManager(context)
            else -> DefaultLocationTrackingManager(
                fusedLocationProviderClient = fusedLocationProviderClient,
                context = context,
                locationRequest = locationRequest
            )
        }
    }
    
    private fun detectRegion(): String {
        // 检查系统语言和地区
        val locale = Locale.getDefault()
        if (locale.country.equals("CN", ignoreCase = true) || 
            locale.language.equals("zh", ignoreCase = true)) {
            return "amap"
        }
        
        // 检查时区（中国时区）
        val timeZone = java.util.TimeZone.getDefault()
        if (timeZone.id.contains("Shanghai") || timeZone.id.contains("Asia/Shanghai")) {
            return "amap"
        }
        
        // 检测Google Play Services是否可用
        return if (isGoogleServicesAvailable()) {
            "google"
        } else {
            "amap"
        }
    }
    
    private fun isGoogleServicesAvailable(): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}