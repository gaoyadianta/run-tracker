package com.sdevprem.runtrack.data.tracking.location

import android.annotation.SuppressLint
import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.sdevprem.runtrack.common.extension.hasLocationPermission
import com.sdevprem.runtrack.domain.tracking.location.LocationTrackingManager
import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.LocationTrackingInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@SuppressLint("MissingPermission")
class AmapLocationTrackingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationTrackingManager {

    private var locationCallback: LocationTrackingManager.LocationCallback? = null
    private var amapLocationClient: AMapLocationClient? = null
    private var locationClientOption: AMapLocationClientOption? = null
    
    private val amapLocationListener = AMapLocationListener { location ->
        if (location != null && location.errorCode == 0) {
            // 定位成功
            val locationTrackingInfo = LocationTrackingInfo(
                locationInfo = LocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = location.altitude,
                    timeMs = location.time
                ),
                speedInMS = location.speed // 高德返回的速度单位是m/s
            )
            locationCallback?.onLocationUpdate(listOf(locationTrackingInfo))
        }
        // 定位失败的情况可以在这里处理日志
    }

    override fun setCallback(locationCallback: LocationTrackingManager.LocationCallback) {
        if (!context.hasLocationPermission()) {
            return
        }

        this.locationCallback = locationCallback
        
        // 初始化定位
        amapLocationClient = AMapLocationClient(context).apply {
            // 设置定位回调监听
            setLocationListener(amapLocationListener)
        }
        
        // 初始化定位参数
        locationClientOption = AMapLocationClientOption().apply {
            // 设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            // 设置是否返回地址信息（默认返回地址信息）
            isNeedAddress = false
            // 设置是否只定位一次,默认为false
            isOnceLocation = false
            // 设置是否强制刷新WIFI，默认为false
            isWifiActiveScan = false
            // 设置是否允许模拟位置,默认为true，允许模拟位置
            isMockEnable = false
            // 设置定位间隔,单位毫秒,默认为2000ms
            interval = 1000
            // 设置定位超时时间，单位是毫秒，默认30000毫秒，建议超时时间不要低于8000毫秒
            httpTimeOut = 30000
            // 关闭缓存机制，高精度定位希望设置为false
            isLocationCacheEnable = false
        }
        
        // 给定位客户端对象设置定位参数
        amapLocationClient?.setLocationOption(locationClientOption)
        
        // 启动定位
        amapLocationClient?.startLocation()
    }

    override fun removeCallback() {
        this.locationCallback = null
        // 停止定位
        amapLocationClient?.stopLocation()
        amapLocationClient?.onDestroy()
        amapLocationClient = null
    }
}
