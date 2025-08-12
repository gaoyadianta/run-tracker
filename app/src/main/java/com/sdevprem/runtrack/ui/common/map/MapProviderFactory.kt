package com.sdevprem.runtrack.ui.common.map

import android.content.Context
import android.location.LocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapProviderFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    fun getMapProvider(): MapProvider {
        return when (detectRegion()) {
            MapProvider.PROVIDER_AMAP -> AmapProvider(context)
            else -> GoogleMapProvider(context)
        }
    }
    
    private fun detectRegion(): String {
        // 方法1: 检查系统语言和地区
        val locale = Locale.getDefault()
        if (locale.country.equals("CN", ignoreCase = true) || 
            locale.language.equals("zh", ignoreCase = true)) {
            return MapProvider.PROVIDER_AMAP
        }
        
        // 方法2: 检查时区（中国时区）
        val timeZone = java.util.TimeZone.getDefault()
        if (timeZone.id.contains("Shanghai") || timeZone.id.contains("Asia/Shanghai")) {
            return MapProvider.PROVIDER_AMAP
        }
        
        // 方法3: 尝试检测网络可达性（可选，需要网络权限）
        return if (isGoogleServicesAvailable()) {
            MapProvider.PROVIDER_GOOGLE
        } else {
            MapProvider.PROVIDER_AMAP
        }
    }
    
    private fun isGoogleServicesAvailable(): Boolean {
        return try {
            // 简单检测Google Play Services是否可用
            val pm = context.packageManager
            pm.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}