package com.sdevprem.runtrack.ui.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.sdevprem.runtrack.common.extension.toLatLng
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import kotlinx.coroutines.delay


object GoogleMapUtils {

    private const val MAP_SNAPSHOT_DELAY = 500L

    suspend fun takeSnapshot(
        map: GoogleMap,
        pathPoints: List<PathPoint>,
        mapCenter: Offset,
        onSnapshot: (Bitmap) -> Unit,
        snapshotSideLength: Float
    ) {
        val side = snapshotSideLength.toInt().coerceAtLeast(1)
        val boundsBuilder = LatLngBounds.Builder()
        var hasPoint = false
        pathPoints.forEach {
            if (it is PathPoint.LocationPoint) {
                boundsBuilder.include(it.locationInfo.toLatLng())
                hasPoint = true
            }
        }

        if (hasPoint) {
            try {
                map.moveCamera(
                    CameraUpdateFactory
                        .newLatLngBounds(
                            boundsBuilder.build(),
                            side,
                            side,
                            (snapshotSideLength * 0.2).toInt()
                        )
                )
            } catch (_: Exception) {
                // Keep current camera when bounds cannot be applied.
            }
        }

        //since move camera bounds the map in the specified LocationInfo
        //from the center withing the bounding box (of side snapshotSideLength)
        //so get the coordinate of the starting point of the box
        val startOffset = mapCenter - Offset(snapshotSideLength / 2, snapshotSideLength / 2)

        //A delay to load the icons and map properly before snapshot
        delay(MAP_SNAPSHOT_DELAY)
        map.snapshot {
            val source = it ?: Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            onSnapshot(cropSnapshot(source, startOffset, side))
        }
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
}
