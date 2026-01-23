package com.sdevprem.runtrack.ui.common.map

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.common.extension.toLatLng
import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import com.sdevprem.runtrack.domain.tracking.model.firstLocationPoint
import com.sdevprem.runtrack.domain.tracking.model.lasLocationPoint
import com.sdevprem.runtrack.ui.common.utils.GoogleMapUtils
import com.sdevprem.runtrack.ui.theme.RTColor
import com.sdevprem.runtrack.ui.theme.md_theme_light_primary
import kotlinx.coroutines.delay
import kotlin.math.min

class GoogleMapProvider(private val context: Context) : MapProvider {

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
        val mapUiSettings = remember {
            MapUiSettings(
                mapToolbarEnabled = false,
                compassEnabled = true,
                zoomControlsEnabled = false
            )
        }
        val cameraPositionState = rememberCameraPositionState {}
        val lastLocationPoint by remember(pathPoints) {
            derivedStateOf { pathPoints.lasLocationPoint() }
        }

        LaunchedEffect(key1 = lastLocationPoint) {
            lastLocationPoint?.let {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.fromLatLngZoom(it.locationInfo.toLatLng(), 15f)
                    )
                )
            }
        }

        GoogleMap(
            modifier = modifier.fillMaxSize(),
            uiSettings = mapUiSettings,
            cameraPositionState = cameraPositionState,
            onMapLoaded = onMapLoaded,
        ) {
            DrawPathPoints(pathPoints = pathPoints, isRunningFinished = isRunningFinished)

            TakeScreenShot(
                take = isRunningFinished,
                mapCenter = mapCenter,
                mapSize = mapSize,
                pathPoints = pathPoints,
                onSnapshot = onSnapshot
            )
        }
    }

    @OptIn(MapsComposeExperimentalApi::class)
    @Composable
    private fun TakeScreenShot(
        take: Boolean,
        mapCenter: Offset,
        mapSize: Size,
        pathPoints: List<PathPoint>,
        onSnapshot: (Bitmap) -> Unit
    ) {
        MapEffect(key1 = take) { map ->
            if (take) {
                val snapshotSide = min(mapSize.width, mapSize.height)
                if (snapshotSide <= 0f) return@MapEffect

                GoogleMapUtils.takeSnapshot(
                    map,
                    pathPoints,
                    mapCenter,
                    onSnapshot,
                    snapshotSideLength = snapshotSide
                )
            }
        }
    }

    @Composable
    @GoogleMapComposable
    private fun DrawPathPoints(
        pathPoints: List<PathPoint>,
        isRunningFinished: Boolean,
    ) {
        val context = LocalContext.current
        val lastMarkerState = rememberMarkerState()
        val largeLastMarkerState = rememberMarkerState()
        val lastLocationPoint by remember(pathPoints) {
            derivedStateOf { pathPoints.lasLocationPoint() }
        }
        val firstLocationPoint by remember(pathPoints) {
            derivedStateOf { pathPoints.firstLocationPoint() }
        }
        val density = LocalDensity.current
        val largeLocationIconSize = remember { with(density) { 32.dp.toPx().toInt() } }
        val smallLocationIconSize = remember { with(density) { 16.dp.toPx().toInt() } }
        val flagSize = remember { with(density) { 32.dp.toPx().toInt() } }
        val flagOffset = remember { Offset(0.5f, 0.8f) }

        LaunchedEffect(key1 = lastLocationPoint) {
            pathPoints.lasLocationPoint()?.let {
                val latLng = it.locationInfo.toLatLng()
                lastMarkerState.position = latLng
                largeLastMarkerState.position = latLng
            }
        }

        val locationInfoList = mutableListOf<LocationInfo>()
        pathPoints.fastForEach { pathPoint ->
            if (pathPoint is PathPoint.EmptyLocationPoint) {
                Polyline(
                    points = locationInfoList.map { it.toLatLng() },
                    color = md_theme_light_primary,
                )
                locationInfoList.clear()
            } else if (pathPoint is PathPoint.LocationPoint) {
                locationInfoList += pathPoint.locationInfo
            }
        }

        // Add the last path points
        if (locationInfoList.isNotEmpty())
            Polyline(
                points = locationInfoList.map { it.toLatLng() },
                color = md_theme_light_primary
            )

        val currentPosIcon = remember(isRunningFinished) {
            if (isRunningFinished.not()) {
                GoogleMapUtils.bitmapDescriptorFromVector(
                    context = context,
                    vectorResId = R.drawable.ic_circle,
                    tint = md_theme_light_primary.toArgb(),
                    sizeInPx = smallLocationIconSize
                )
            } else {
                GoogleMapUtils.bitmapDescriptorFromVector(
                    context = context,
                    vectorResId = R.drawable.ic_location_marker,
                    tint = Color.Red.toArgb(),
                    sizeInPx = flagSize
                )
            }
        }
        val currentPosLargeIcon = remember(isRunningFinished) {
            if (isRunningFinished) return@remember null

            GoogleMapUtils.bitmapDescriptorFromVector(
                context = context,
                vectorResId = R.drawable.ic_circle,
                tint = md_theme_light_primary.copy(alpha = 0.4f).toArgb(),
                sizeInPx = largeLocationIconSize
            )
        }

        currentPosLargeIcon?.let {
            Marker(
                icon = currentPosLargeIcon,
                state = largeLastMarkerState,
                anchor = Offset(0.5f, 0.5f),
                visible = lastLocationPoint != null
            )
        }

        Marker(
            icon = currentPosIcon,
            state = lastMarkerState,
            anchor = if (isRunningFinished) flagOffset else Offset(0.5f, 0.5f),
            visible = lastLocationPoint != null
        )

        firstLocationPoint?.let {
            val firstLocationIcon = remember(isRunningFinished) {
                GoogleMapUtils.bitmapDescriptorFromVector(
                    context = context,
                    vectorResId = R.drawable.ic_location_marker,
                    tint = RTColor.CHATEAU_GREEN.toArgb(),
                    sizeInPx = flagSize
                )
            }
            Marker(
                icon = firstLocationIcon,
                state = rememberMarkerState(position = it.locationInfo.toLatLng()),
                anchor = flagOffset,
            )
        }
    }

    override fun bitmapDescriptorFromVector(
        vectorResId: Int,
        tint: Int?,
        sizeInPx: Int?
    ): BitmapDescriptor {
        return GoogleMapUtils.bitmapDescriptorFromVector(
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
        if (mapInstance is GoogleMap) {
            GoogleMapUtils.takeSnapshot(
                mapInstance,
                pathPoints,
                mapCenter,
                onSnapshot,
                snapshotSideLength
            )
        }
    }
}
