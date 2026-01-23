package com.sdevprem.runtrack.ui.common.map

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint
import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.PathPoint

interface MapProvider {
    
    @Composable
    fun MapComposable(
        modifier: Modifier,
        pathPoints: List<PathPoint>,
        playbackPathPoints: List<PathPoint>,
        isRunningFinished: Boolean,
        annotations: List<RunAiAnnotationPoint>,
        highlightLocation: LocationInfo?,
        mapStyle: MapStyle,
        mapCenter: Offset,
        mapSize: Size,
        onMapLoaded: () -> Unit,
        onSnapshot: (Bitmap) -> Unit,
        onAnnotationClick: (RunAiAnnotationPoint) -> Unit
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
