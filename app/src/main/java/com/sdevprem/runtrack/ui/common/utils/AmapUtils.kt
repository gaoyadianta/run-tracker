package com.sdevprem.runtrack.ui.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
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
        mapCenter: Offset,
        onSnapshot: (Bitmap) -> Unit,
        snapshotSideLength: Float
    ) {
        val boundsBuilder = LatLngBounds.Builder()
        var hasPoint = false
        pathPoints.forEach {
            if (it is PathPoint.LocationPoint) {
                boundsBuilder.include(
                    LatLng(
                        it.locationInfo.latitude,
                        it.locationInfo.longitude
                    )
                )
                hasPoint = true
            }
        }
        if (!hasPoint) return

        try {
            val bounds = boundsBuilder.build()
            val padding = (snapshotSideLength * 0.15f).toInt().coerceAtLeast(48)
            map.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds,
                    snapshotSideLength.toInt(),
                    snapshotSideLength.toInt(),
                    padding
                )
            )
        } catch (e: Exception) {
            // Handle empty bounds
        }

        // Delay to load the icons and map properly before snapshot
        delay(MAP_SNAPSHOT_DELAY)
        
        val startOffset = mapCenter - Offset(snapshotSideLength / 2, snapshotSideLength / 2)
        val side = snapshotSideLength.toInt()

        map.getMapScreenShot(object : AMap.OnMapScreenShotListener {
            override fun onMapScreenShot(bitmap: Bitmap?) {
                bitmap?.let { onSnapshot(cropSnapshot(it, startOffset, side)) }
            }

            override fun onMapScreenShot(bitmap: Bitmap?, status: Int) {
                if (status == 0) {
                    bitmap?.let { onSnapshot(cropSnapshot(it, startOffset, side)) }
                }
            }
        })
    }

    private fun cropSnapshot(
        bitmap: Bitmap,
        startOffset: Offset,
        side: Int
    ): Bitmap {
        val safeSide = side.coerceAtMost(minOf(bitmap.width, bitmap.height))
        if (safeSide <= 0) return bitmap
        val maxX = (bitmap.width - safeSide).coerceAtLeast(0)
        val maxY = (bitmap.height - safeSide).coerceAtLeast(0)
        val safeX = startOffset.x.toInt().coerceIn(0, maxX)
        val safeY = startOffset.y.toInt().coerceIn(0, maxY)
        return Bitmap.createBitmap(bitmap, safeX, safeY, safeSide, safeSide)
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
