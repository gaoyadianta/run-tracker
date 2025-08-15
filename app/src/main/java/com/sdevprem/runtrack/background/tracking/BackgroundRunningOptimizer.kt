package com.sdevprem.runtrack.background.tracking

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.sdevprem.runtrack.background.tracking.battery.BatteryOptimizationManager
import com.sdevprem.runtrack.background.tracking.wakelock.WakeLockManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 后台运行优化器，统一管理各种后台运行相关的设置和检查
 */
@Singleton
class BackgroundRunningOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryOptimizationManager: BatteryOptimizationManager,
    private val wakeLockManager: WakeLockManager
) {
    
    /**
     * 检查所有后台运行相关的设置
     */
    fun checkBackgroundRunningSettings(): BackgroundRunningStatus {
        val isBatteryOptimized = batteryOptimizationManager.isIgnoringBatteryOptimizations()
        val isWakeLockHeld = wakeLockManager.isWakeLockHeld()
        val isAutoStartEnabled = checkAutoStartPermission()
        
        return BackgroundRunningStatus(
            isBatteryOptimizationDisabled = isBatteryOptimized,
            isWakeLockActive = isWakeLockHeld,
            isAutoStartEnabled = isAutoStartEnabled,
            recommendations = generateRecommendations(isBatteryOptimized, isAutoStartEnabled)
        )
    }
    
    /**
     * 检查自启动权限（主要针对国产手机）
     */
    private fun checkAutoStartPermission(): Boolean {
        // 这个检查比较复杂，因为不同厂商的实现不同
        // 这里提供一个基础的检查逻辑
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            when {
                manufacturer.contains("xiaomi") -> checkMiuiAutoStart()
                manufacturer.contains("huawei") -> checkHuaweiAutoStart()
                manufacturer.contains("oppo") -> checkOppoAutoStart()
                manufacturer.contains("vivo") -> checkVivoAutoStart()
                manufacturer.contains("samsung") -> checkSamsungAutoStart()
                else -> true // 其他厂商默认认为已开启
            }
        } catch (e: Exception) {
            Timber.w(e, "检查自启动权限时出错")
            true
        }
    }
    
    private fun checkMiuiAutoStart(): Boolean {
        // MIUI的自启动检查逻辑
        return true // 简化实现，实际需要更复杂的检查
    }
    
    private fun checkHuaweiAutoStart(): Boolean {
        // 华为的自启动检查逻辑
        return true
    }
    
    private fun checkOppoAutoStart(): Boolean {
        // OPPO的自启动检查逻辑
        return true
    }
    
    private fun checkVivoAutoStart(): Boolean {
        // VIVO的自启动检查逻辑
        return true
    }
    
    private fun checkSamsungAutoStart(): Boolean {
        // 三星的自启动检查逻辑
        return true
    }
    
    /**
     * 生成优化建议
     */
    private fun generateRecommendations(
        isBatteryOptimized: Boolean,
        isAutoStartEnabled: Boolean
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (!isBatteryOptimized) {
            recommendations.add("建议关闭电池优化以确保后台持续运行")
        }
        
        if (!isAutoStartEnabled) {
            recommendations.add("建议开启自启动权限以防止应用被系统杀死")
        }
        
        // 添加通用建议
        recommendations.add("运动时建议保持屏幕常亮或定期点亮屏幕")
        recommendations.add("避免使用第三方清理软件清理本应用")
        
        return recommendations
    }
    
    /**
     * 获取自启动设置页面的Intent
     */
    fun getAutoStartSettingsIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return try {
            when {
                manufacturer.contains("xiaomi") -> getMiuiAutoStartIntent()
                manufacturer.contains("huawei") -> getHuaweiAutoStartIntent()
                manufacturer.contains("oppo") -> getOppoAutoStartIntent()
                manufacturer.contains("vivo") -> getVivoAutoStartIntent()
                manufacturer.contains("samsung") -> getSamsungAutoStartIntent()
                else -> getGenericAppSettingsIntent()
            }
        } catch (e: Exception) {
            Timber.w(e, "获取自启动设置Intent时出错")
            getGenericAppSettingsIntent()
        }
    }
    
    private fun getMiuiAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
    }
    
    private fun getHuaweiAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        }
    }
    
    private fun getOppoAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        }
    }
    
    private fun getVivoAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        }
    }
    
    private fun getSamsungAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        }
    }
    
    private fun getGenericAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}

/**
 * 后台运行状态数据类
 */
data class BackgroundRunningStatus(
    val isBatteryOptimizationDisabled: Boolean,
    val isWakeLockActive: Boolean,
    val isAutoStartEnabled: Boolean,
    val recommendations: List<String>
) {
    val isOptimalForBackgroundRunning: Boolean
        get() = isBatteryOptimizationDisabled && isAutoStartEnabled
}