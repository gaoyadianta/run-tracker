package com.sdevprem.runtrack.ui.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import kotlinx.coroutines.delay

object AmapUtils {

    private const val MAP_SNAPSHOT_DELAY = 500L

    suspend fun takeSnapshot(
        map: AMap,
        pathPoints: List<PathPoint>,
        onSnapshot: (Bitmap) -> Unit,
        snapshotSideLength: Float
    ) {
        val boundsBuilder = LatLngBounds.Builder()
        pathPoints.forEach {
            if (it is PathPoint.LocationPoint) {
                boundsBuilder.include(
                    LatLng(
                        it.locationInfo.latitude,
                        it.locationInfo.longitude
                    )
                )
            }
        }

        try {
            val bounds = boundsBuilder.build()
            map.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds,
                    snapshotSideLength.toInt()
                )
            )
        } catch (e: Exception) {
            // Handle empty bounds
        }

        // Delay to load the icons and map properly before snapshot
        delay(MAP_SNAPSHOT_DELAY)
        
        map.getMapScreenShot(object : AMap.OnMapScreenShotListener {
            override fun onMapScreenShot(bitmap: Bitmap?) {
                bitmap?.let { onSnapshot(it) }
            }

            override fun onMapScreenShot(bitmap: Bitmap?, status: Int) {
                if (status == 0) {
                    bitmap?.let { onSnapshot(it) }
                }
            }
        })
    }

    fun bitmapDescriptorFromVector(
        context: Context,
        @DrawableRes vectorResId: Int,
        tint: Int? = null,
        sizeInPx: Int? = null,
    ): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)!!
        tint?.let { vectorDrawable.setTint(it) }

        vectorDrawable.setBounds(
            0,
            0,
            sizeInPx ?: vectorDrawable.intrinsicWidth,
            sizeInPx ?: vectorDrawable.intrinsicHeight
        )

        val bitmap = Bitmap.createBitmap(
            sizeInPx ?: vectorDrawable.intrinsicWidth,
            sizeInPx ?: vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun toAmapLatLng(locationInfo: com.sdevprem.runtrack.domain.tracking.model.LocationInfo): LatLng {
        return LatLng(locationInfo.latitude, locationInfo.longitude)
    }
}