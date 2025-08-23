package com.sdevprem.runtrack.common.utils

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

object PermissionUtils {
    val locationPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
    }.toTypedArray()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val notificationPermission = Manifest.permission.POST_NOTIFICATIONS

    val bluetoothPermissions = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要新的蓝牙权限
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Android 11 及以下使用旧的蓝牙权限
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }.toTypedArray()

    val audioPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )

    val activityRecognitionPermissions = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }.toTypedArray()

    val allPermissions = mutableListOf<String>().apply {
        addAll(locationPermissions)
        addAll(bluetoothPermissions)
        addAll(audioPermissions)
        addAll(activityRecognitionPermissions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(notificationPermission)
        }
    }.toTypedArray()
}