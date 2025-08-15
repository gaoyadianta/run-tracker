package com.sdevprem.runtrack.data.tracking.location

import android.app.Activity
import android.content.IntentSender
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient

object LocationUtils {
    private const val LOCATION_UPDATE_INTERVAL = 3000L // 减少到3秒，提高精度
    private const val FASTEST_UPDATE_INTERVAL = 1000L // 最快1秒更新一次
    private const val MAX_WAIT_TIME = 10000L // 最大等待时间10秒

    val locationRequestBuilder
        get() = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            setMaxUpdateDelayMillis(MAX_WAIT_TIME)
            // 设置为true，即使在后台也能获取位置更新
            setWaitForAccurateLocation(false)
        }

    const val LOCATION_ENABLE_REQUEST_CODE = 5234

    fun checkAndRequestLocationSetting(activity: Activity) {
        val locationRequest = locationRequestBuilder.build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(activity)

        client.checkLocationSettings(builder.build())
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(
                            activity,
                            LOCATION_ENABLE_REQUEST_CODE
                        )
                    } catch (_: IntentSender.SendIntentException) {
                    }
                }
            }
    }
}