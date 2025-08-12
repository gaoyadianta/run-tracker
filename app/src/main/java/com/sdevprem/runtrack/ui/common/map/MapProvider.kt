package com.sdevprem.runtrack.ui.common.map

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sdevprem.runtrack.domain.tracking.model.PathPoint

interface MapProvider {
    
    @Composable
    fun MapComposable(
        modifier: Modifier,
        pathPoints: List<PathPoint>,
        isRunningFinished: Boolean,
        mapCenter: Offset,
        mapSize: Size,
        onMapLoaded: () -> Unit,
        onSnapshot: (Bitmap) -> Unit
    )

    fun bitmapDescriptorFromVector(
        vectorResId: Int,
        tint: Int? = null,
        sizeInPx: Int? = null
    ): Any

    suspend fun takeSnapshot(
        mapInstance: Any,
        pathPoints: List<PathPoint>,
        mapCenter: Offset,
        onSnapshot: (Bitmap) -> Unit,
        snapshotSideLength: Float
    )

    companion object {
        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_AMAP = "amap"
    }
}