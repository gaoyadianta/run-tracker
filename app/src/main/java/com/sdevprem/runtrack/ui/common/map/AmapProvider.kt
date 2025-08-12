package com.sdevprem.runtrack.ui.common.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import com.sdevprem.runtrack.domain.tracking.model.firstLocationPoint
import com.sdevprem.runtrack.domain.tracking.model.lasLocationPoint
import com.sdevprem.runtrack.ui.common.utils.AmapUtils
import com.sdevprem.runtrack.ui.theme.RTColor
import com.sdevprem.runtrack.ui.theme.md_theme_light_primary

class AmapProvider(private val context: Context) : MapProvider {

    @Composable
    override fun MapComposable(
        modifier: Modifier,
        pathPoints: List<PathPoint>,
        isRunningFinished: Boolean,
        mapCenter: Offset,
        mapSize: Size,
        onMapLoaded: () -> Unit,
        onSnapshot: (Bitmap) -> Unit
    ) {
        var mapView by remember { mutableStateOf<MapView?>(null) }
        var aMap by remember { mutableStateOf<AMap?>(null) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val density = LocalDensity.current
        
        val largeLocationIconSize = remember { with(density) { 32.dp.toPx().toInt() } }
        val smallLocationIconSize = remember { with(density) { 16.dp.toPx().toInt() } }
        val flagSize = remember { with(density) { 32.dp.toPx().toInt() } }
        
        val lastLocationPoint by remember(pathPoints) {
            derivedStateOf { pathPoints.lasLocationPoint() }
        }
        val firstLocationPoint by remember(pathPoints) {
            derivedStateOf { pathPoints.firstLocationPoint() }
        }

        // Lifecycle management
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> mapView?.onCreate(null)
                    Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                    Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                mapView?.onDestroy()
            }
        }

        // Camera follow last location
        LaunchedEffect(lastLocationPoint) {
            lastLocationPoint?.let { locationPoint ->
                aMap?.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition(
                            AmapUtils.toAmapLatLng(locationPoint.locationInfo),
                            15f, // zoom level
                            0f,  // tilt
                            0f   // bearing
                        )
                    )
                )
            }
        }

        // Handle path drawing and markers
        LaunchedEffect(pathPoints, isRunningFinished) {
            aMap?.let { map ->
                // Clear previous overlays
                map.clear()
                
                // Draw path lines
                drawPathLines(map, pathPoints)
                
                // Add markers
                addMarkers(
                    map,
                    firstLocationPoint,
                    lastLocationPoint,
                    isRunningFinished,
                    largeLocationIconSize,
                    smallLocationIconSize,
                    flagSize
                )
            }
        }

        // Handle screenshot
        LaunchedEffect(isRunningFinished) {
            if (isRunningFinished && aMap != null) {
                AmapUtils.takeSnapshot(
                    aMap!!,
                    pathPoints,
                    onSnapshot,
                    mapSize.width / 2f
                )
            }
        }

        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    mapView = this
                    onCreate(null)
                    map?.let { mapInstance ->
                        aMap = mapInstance
                        // Configure map settings
                        mapInstance.uiSettings.isZoomControlsEnabled = false
                        mapInstance.uiSettings.isCompassEnabled = true
                        mapInstance.uiSettings.isMyLocationButtonEnabled = false
                        mapInstance.uiSettings.isScaleControlsEnabled = false
                        
                        onMapLoaded()
                    }
                }
            }
        )
    }

    private fun drawPathLines(map: AMap, pathPoints: List<PathPoint>) {
        val locationInfoList = mutableListOf<LocationInfo>()
        
        pathPoints.forEach { pathPoint ->
            when (pathPoint) {
                is PathPoint.EmptyLocationPoint -> {
                    if (locationInfoList.isNotEmpty()) {
                        addPolyline(map, locationInfoList)
                        locationInfoList.clear()
                    }
                }
                is PathPoint.LocationPoint -> {
                    locationInfoList.add(pathPoint.locationInfo)
                }
            }
        }
        
        // Add the last segment
        if (locationInfoList.isNotEmpty()) {
            addPolyline(map, locationInfoList)
        }
    }

    private fun addPolyline(map: AMap, locationInfoList: List<LocationInfo>) {
        val polylineOptions = PolylineOptions()
            .addAll(locationInfoList.map { AmapUtils.toAmapLatLng(it) })
            .color(md_theme_light_primary.toArgb())
            .width(10f)
        
        map.addPolyline(polylineOptions)
    }

    private fun addMarkers(
        map: AMap,
        firstLocationPoint: PathPoint.LocationPoint?,
        lastLocationPoint: PathPoint.LocationPoint?,
        isRunningFinished: Boolean,
        largeLocationIconSize: Int,
        smallLocationIconSize: Int,
        flagSize: Int
    ) {
        // Add start marker
        firstLocationPoint?.let { startPoint ->
            val startIcon = AmapUtils.bitmapDescriptorFromVector(
                context = context,
                vectorResId = R.drawable.ic_location_marker,
                tint = RTColor.CHATEAU_GREEN.toArgb(),
                sizeInPx = flagSize
            )
            
            map.addMarker(
                MarkerOptions()
                    .position(AmapUtils.toAmapLatLng(startPoint.locationInfo))
                    .icon(startIcon)
            )
        }

        // Add current position marker
        lastLocationPoint?.let { endPoint ->
            val currentPosIcon = if (!isRunningFinished) {
                // Large circle for running state
                AmapUtils.bitmapDescriptorFromVector(
                    context = context,
                    vectorResId = R.drawable.ic_circle,
                    tint = md_theme_light_primary.copy(alpha = 0.4f).toArgb(),
                    sizeInPx = largeLocationIconSize
                )
            } else {
                // Flag for finished state
                AmapUtils.bitmapDescriptorFromVector(
                    context = context,
                    vectorResId = R.drawable.ic_location_marker,
                    tint = Color.Red.toArgb(),
                    sizeInPx = flagSize
                )
            }
            
            map.addMarker(
                MarkerOptions()
                    .position(AmapUtils.toAmapLatLng(endPoint.locationInfo))
                    .icon(currentPosIcon)
            )
            
            // Add small circle for running state
            if (!isRunningFinished) {
                val smallIcon = AmapUtils.bitmapDescriptorFromVector(
                    context = context,
                    vectorResId = R.drawable.ic_circle,
                    tint = md_theme_light_primary.toArgb(),
                    sizeInPx = smallLocationIconSize
                )
                
                map.addMarker(
                    MarkerOptions()
                        .position(AmapUtils.toAmapLatLng(endPoint.locationInfo))
                        .icon(smallIcon)
                )
            }
        }
    }

    override fun bitmapDescriptorFromVector(
        vectorResId: Int,
        tint: Int?,
        sizeInPx: Int?
    ): BitmapDescriptor {
        return AmapUtils.bitmapDescriptorFromVector(
            context = context,
            vectorResId = vectorResId,
            tint = tint,
            sizeInPx = sizeInPx
        )
    }

    override suspend fun takeSnapshot(
        mapInstance: Any,
        pathPoints: List<PathPoint>,
        mapCenter: Offset,
        onSnapshot: (Bitmap) -> Unit,
        snapshotSideLength: Float
    ) {
        if (mapInstance is AMap) {
            AmapUtils.takeSnapshot(
                mapInstance,
                pathPoints,
                onSnapshot,
                snapshotSideLength
            )
        }
    }
}