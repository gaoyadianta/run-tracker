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
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.common.extension.toLatLng
import com.sdevprem.runtrack.common.utils.LocationUtils
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint
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
        playbackPathPoints: List<PathPoint>,
        isRunningFinished: Boolean,
        annotations: List<RunAiAnnotationPoint>,
        highlightLocation: LocationInfo?,
        mapStyle: MapStyle,
        allowAutoFollow: Boolean,
        followLocationTrigger: Int,
        fitRouteOnLoad: Boolean,
        mapCenter: Offset,
        mapSize: Size,
        onMapLoaded: () -> Unit,
        onSnapshot: (Bitmap) -> Unit,
        onUserGesture: () -> Unit,
        onAnnotationClick: (RunAiAnnotationPoint) -> Unit
    ) {
        val mapUiSettings = remember {
            MapUiSettings(
                mapToolbarEnabled = false,
                compassEnabled = true,
                zoomControlsEnabled = false,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true,
                rotationGesturesEnabled = true,
                tiltGesturesEnabled = true
            )
        }
        val cameraPositionState = rememberCameraPositionState {}
        val lastLocationPoint by remember(pathPoints) {
            derivedStateOf { pathPoints.lasLocationPoint() }
        }
        val mapProperties = remember(mapStyle) {
            MapProperties(
                mapType = when (mapStyle) {
                    MapStyle.SATELLITE -> MapType.SATELLITE
                    else -> MapType.NORMAL
                },
                mapStyleOptions = if (mapStyle == MapStyle.NIGHT) {
                    MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_night)
                } else {
                    null
                }
            )
        }

        LaunchedEffect(key1 = lastLocationPoint, allowAutoFollow, followLocationTrigger) {
            if (!allowAutoFollow) return@LaunchedEffect
            lastLocationPoint?.let {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.fromLatLngZoom(it.locationInfo.toLatLng(), 15f)
                    )
                )
            }
        }

        LaunchedEffect(pathPoints, fitRouteOnLoad, mapSize) {
            if (!fitRouteOnLoad || pathPoints.size < 2) return@LaunchedEffect
            if (mapSize.width <= 0f || mapSize.height <= 0f) return@LaunchedEffect
            val boundsBuilder = LatLngBounds.Builder()
            pathPoints.forEach { point ->
                if (point is PathPoint.LocationPoint) {
                    boundsBuilder.include(point.locationInfo.toLatLng())
                }
            }
            val padding = (kotlin.math.min(mapSize.width, mapSize.height) * 0.18f).toInt()
                .coerceAtLeast(48)
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(
                    boundsBuilder.build(),
                    mapSize.width.toInt(),
                    mapSize.height.toInt(),
                    padding
                )
            )
        }

        GoogleMap(
            modifier = modifier.fillMaxSize(),
            uiSettings = mapUiSettings,
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            onMapLoaded = onMapLoaded,
        ) {
            DrawPathPoints(
                pathPoints = pathPoints,
                playbackPathPoints = playbackPathPoints,
                isRunningFinished = isRunningFinished,
                annotations = annotations,
                highlightLocation = highlightLocation,
                onAnnotationClick = onAnnotationClick
            )

            MapEffect(key1 = onUserGesture) { map ->
                map.setOnCameraMoveStartedListener { reason ->
                    if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        onUserGesture()
                    }
                }
            }

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
        playbackPathPoints: List<PathPoint>,
        isRunningFinished: Boolean,
        annotations: List<RunAiAnnotationPoint>,
        highlightLocation: LocationInfo?,
        onAnnotationClick: (RunAiAnnotationPoint) -> Unit
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
        val annotationIconSize = remember { with(density) { 20.dp.toPx().toInt() } }
        val highlightIconSize = remember { with(density) { 18.dp.toPx().toInt() } }

        LaunchedEffect(key1 = lastLocationPoint) {
            pathPoints.lasLocationPoint()?.let {
                val latLng = it.locationInfo.toLatLng()
                lastMarkerState.position = latLng
                largeLastMarkerState.position = latLng
            }
        }

        drawPathSegments(
            pathPoints = pathPoints,
            color = md_theme_light_primary.copy(alpha = 0.35f),
            width = 12f,
            colorByPace = isRunningFinished
        )
        if (playbackPathPoints.isNotEmpty()) {
            drawPathSegments(
                pathPoints = playbackPathPoints,
                color = md_theme_light_primary,
                width = 16f,
                colorByPace = false
            )
        }

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

        val annotationIcon = remember {
            GoogleMapUtils.bitmapDescriptorFromVector(
                context = context,
                vectorResId = R.drawable.ic_circle_hollow,
                tint = md_theme_light_primary.toArgb(),
                sizeInPx = annotationIconSize
            )
        }
        annotations.forEach { annotation ->
            Marker(
                icon = annotationIcon,
                state = rememberMarkerState(
                    position = com.google.android.gms.maps.model.LatLng(
                        annotation.latitude,
                        annotation.longitude
                    )
                ),
                title = annotation.text,
                anchor = Offset(0.5f, 0.5f),
                onClick = {
                    onAnnotationClick(annotation)
                    true
                }
            )
        }

        highlightLocation?.let { location ->
            val highlightIcon = remember {
                GoogleMapUtils.bitmapDescriptorFromVector(
                    context = context,
                    vectorResId = R.drawable.ic_circle,
                    tint = md_theme_light_primary.toArgb(),
                    sizeInPx = highlightIconSize
                )
            }
            Marker(
                icon = highlightIcon,
                state = rememberMarkerState(position = location.toLatLng()),
                anchor = Offset(0.5f, 0.5f)
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

    @Composable
    @GoogleMapComposable
    private fun drawPathSegments(
        pathPoints: List<PathPoint>,
        color: Color,
        width: Float,
        colorByPace: Boolean
    ) {
        if (pathPoints.isEmpty()) return
        if (colorByPace) {
            val coloredSegments = buildPaceColoredSegments(pathPoints)
            if (coloredSegments.isNotEmpty()) {
                coloredSegments.fastForEach { segment ->
                    Polyline(
                        points = segment.points.map { it.toLatLng() },
                        color = segment.color,
                        width = width
                    )
                }
                return
            }
        }
        val locationInfoList = mutableListOf<LocationInfo>()
        pathPoints.fastForEach { pathPoint ->
            if (pathPoint is PathPoint.EmptyLocationPoint) {
                if (locationInfoList.isNotEmpty()) {
                    Polyline(
                        points = locationInfoList.map { it.toLatLng() },
                        color = color,
                        width = width
                    )
                    locationInfoList.clear()
                }
            } else if (pathPoint is PathPoint.LocationPoint) {
                locationInfoList += pathPoint.locationInfo
            }
        }
        if (locationInfoList.isNotEmpty()) {
            Polyline(
                points = locationInfoList.map { it.toLatLng() },
                color = color,
                width = width
            )
        }
    }

    private data class PacePiece(
        val start: LocationInfo,
        val end: LocationInfo,
        val pace: Float,
        val breakBefore: Boolean
    )

    private data class ColoredSegment(
        val points: List<LocationInfo>,
        val color: Color
    )

    private fun buildPaceColoredSegments(pathPoints: List<PathPoint>): List<ColoredSegment> {
        val pieces = mutableListOf<PacePiece>()
        var prev: PathPoint.LocationPoint? = null
        var breakBefore = false
        val fallbackIntervalMs = 3000L

        pathPoints.fastForEach { point ->
            when (point) {
                is PathPoint.EmptyLocationPoint -> {
                    prev = null
                    breakBefore = true
                }
                is PathPoint.LocationPoint -> {
                    val previous = prev
                    if (previous != null) {
                        val distance = LocationUtils.getDistanceBetweenPathPoints(previous, point)
                        val rawDelta = point.locationInfo.timeMs - previous.locationInfo.timeMs
                        val deltaTime = if (rawDelta > 0L) rawDelta else fallbackIntervalMs
                        if (distance > 0) {
                            val pace = (deltaTime / 60000f) / (distance / 1000f)
                            pieces.add(PacePiece(previous.locationInfo, point.locationInfo, pace, breakBefore))
                        }
                    }
                    breakBefore = false
                    prev = point
                }
            }
        }

        if (pieces.isEmpty()) return emptyList()

        val minPace = pieces.minOf { it.pace }
        val maxPace = pieces.maxOf { it.pace }
        val bucketCount = 6
        val segments = mutableListOf<ColoredSegment>()
        var currentBucket = -1
        var currentPoints = mutableListOf<LocationInfo>()
        var currentColor: Color? = null

        pieces.forEach { piece ->
            val t = if (maxPace == minPace) 0.5f else ((piece.pace - minPace) / (maxPace - minPace))
                .coerceIn(0f, 1f)
            val bucket = kotlin.math.round(t * (bucketCount - 1)).toInt()
            val color = paceColorForBucket(bucket, bucketCount)

            if (piece.breakBefore || currentBucket == -1 || bucket != currentBucket) {
                if (currentPoints.size >= 2 && currentColor != null) {
                    segments.add(ColoredSegment(currentPoints.toList(), currentColor!!))
                }
                currentPoints = mutableListOf(piece.start, piece.end)
                currentBucket = bucket
                currentColor = color
            } else {
                currentPoints.add(piece.end)
            }
        }

        if (currentPoints.size >= 2 && currentColor != null) {
            segments.add(ColoredSegment(currentPoints.toList(), currentColor!!))
        }

        return segments
    }

    private fun paceColorForBucket(bucket: Int, bucketCount: Int): Color {
        if (bucketCount <= 1) return Color(0xFFF5C542)
        val t = (bucket.toFloat() / (bucketCount - 1)).coerceIn(0f, 1f)
        val green = Color(0xFF2ECC71)
        val yellow = Color(0xFFF5C542)
        val red = Color(0xFFE74C3C)
        return if (t <= 0.5f) {
            lerpColor(green, yellow, t / 0.5f)
        } else {
            lerpColor(yellow, red, (t - 0.5f) / 0.5f)
        }
    }

    private fun lerpColor(start: Color, end: Color, t: Float): Color {
        val clamped = t.coerceIn(0f, 1f)
        return Color(
            red = start.red + (end.red - start.red) * clamped,
            green = start.green + (end.green - start.green) * clamped,
            blue = start.blue + (end.blue - start.blue) * clamped,
            alpha = 1f
        )
    }
}
