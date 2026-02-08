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
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.common.utils.LocationUtils
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint
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
        var mapView by remember { mutableStateOf<MapView?>(null) }
        var aMap by remember { mutableStateOf<AMap?>(null) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val density = LocalDensity.current
        var latestAnnotations by remember { mutableStateOf<List<RunAiAnnotationPoint>>(emptyList()) }
        latestAnnotations = annotations

        var basePolylines by remember { mutableStateOf<List<Polyline>>(emptyList()) }
        var playbackPolyline by remember { mutableStateOf<Polyline?>(null) }
        var startMarker by remember { mutableStateOf<Marker?>(null) }
        var endMarker by remember { mutableStateOf<Marker?>(null) }
        var runningOuterMarker by remember { mutableStateOf<Marker?>(null) }
        var runningInnerMarker by remember { mutableStateOf<Marker?>(null) }
        var annotationMarkers by remember { mutableStateOf<List<Marker>>(emptyList()) }
        var highlightMarker by remember { mutableStateOf<Marker?>(null) }
        
        val largeLocationIconSize = remember { with(density) { 32.dp.toPx().toInt() } }
        val smallLocationIconSize = remember { with(density) { 16.dp.toPx().toInt() } }
        val flagSize = remember { with(density) { 32.dp.toPx().toInt() } }
        val highlightIconSize = remember { with(density) { 16.dp.toPx().toInt() } }
        val startIcon = remember {
            AmapUtils.bitmapDescriptorFromVector(
                context = context,
                vectorResId = R.drawable.ic_location_marker,
                tint = RTColor.CHATEAU_GREEN.toArgb(),
                sizeInPx = flagSize
            )
        }
        val finishIcon = remember {
            AmapUtils.bitmapDescriptorFromVector(
                context = context,
                vectorResId = R.drawable.ic_location_marker,
                tint = Color.Red.toArgb(),
                sizeInPx = flagSize
            )
        }
        val runningOuterIcon = remember {
            AmapUtils.bitmapDescriptorFromVector(
                context = context,
                vectorResId = R.drawable.ic_circle,
                tint = md_theme_light_primary.copy(alpha = 0.4f).toArgb(),
                sizeInPx = largeLocationIconSize
            )
        }
        val runningInnerIcon = remember {
            AmapUtils.bitmapDescriptorFromVector(
                context = context,
                vectorResId = R.drawable.ic_circle,
                tint = md_theme_light_primary.toArgb(),
                sizeInPx = smallLocationIconSize
            )
        }
        val annotationIcon = remember {
            AmapUtils.bitmapDescriptorFromVector(
                context = context,
                vectorResId = R.drawable.ic_circle_hollow,
                tint = md_theme_light_primary.toArgb(),
                sizeInPx = smallLocationIconSize
            )
        }
        val highlightIcon = remember {
            AmapUtils.bitmapDescriptorFromVector(
                context = context,
                vectorResId = R.drawable.ic_circle,
                tint = md_theme_light_primary.toArgb(),
                sizeInPx = highlightIconSize
            )
        }
        
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
        LaunchedEffect(lastLocationPoint, allowAutoFollow, followLocationTrigger) {
            if (!allowAutoFollow) return@LaunchedEffect
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

        LaunchedEffect(aMap, pathPoints, fitRouteOnLoad, mapSize) {
            if (!fitRouteOnLoad || pathPoints.size < 2) return@LaunchedEffect
            if (mapSize.width <= 0f || mapSize.height <= 0f) return@LaunchedEffect
            val boundsBuilder = com.amap.api.maps.model.LatLngBounds.Builder()
            pathPoints.forEach { point ->
                if (point is PathPoint.LocationPoint) {
                    boundsBuilder.include(AmapUtils.toAmapLatLng(point.locationInfo))
                }
            }
            val padding = (kotlin.math.min(mapSize.width, mapSize.height) * 0.18f).toInt()
                .coerceAtLeast(48)
            try {
                val bounds = boundsBuilder.build()
                aMap?.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds,
                        mapSize.width.toInt(),
                        mapSize.height.toInt(),
                        padding
                    )
                )
            } catch (_: Exception) {
                // ignore invalid bounds
            }
        }

        // Handle path drawing and markers
        LaunchedEffect(aMap, pathPoints, isRunningFinished, annotations) {
            aMap?.let { map ->
                basePolylines.forEach { it.remove() }
                basePolylines = emptyList()

                startMarker?.remove()
                endMarker?.remove()
                runningOuterMarker?.remove()
                runningInnerMarker?.remove()
                annotationMarkers.forEach { it.remove() }
                startMarker = null
                endMarker = null
                runningOuterMarker = null
                runningInnerMarker = null
                annotationMarkers = emptyList()

                basePolylines = if (isRunningFinished) {
                    drawPaceColoredLines(
                        map,
                        pathPoints,
                        12f
                    )
                } else {
                    drawPathLines(
                        map,
                        pathPoints,
                        md_theme_light_primary.copy(alpha = 0.35f).toArgb(),
                        12f
                    )
                }

                firstLocationPoint?.let { startPoint ->
                    startMarker = map.addMarker(
                        MarkerOptions()
                            .position(AmapUtils.toAmapLatLng(startPoint.locationInfo))
                            .icon(startIcon)
                    )
                }

                lastLocationPoint?.let { endPoint ->
                    if (isRunningFinished) {
                        endMarker = map.addMarker(
                            MarkerOptions()
                                .position(AmapUtils.toAmapLatLng(endPoint.locationInfo))
                                .icon(finishIcon)
                        )
                    } else {
                        runningOuterMarker = map.addMarker(
                            MarkerOptions()
                                .position(AmapUtils.toAmapLatLng(endPoint.locationInfo))
                                .icon(runningOuterIcon)
                        )
                        runningInnerMarker = map.addMarker(
                            MarkerOptions()
                                .position(AmapUtils.toAmapLatLng(endPoint.locationInfo))
                                .icon(runningInnerIcon)
                        )
                    }
                }

                annotationMarkers = annotations.map { annotation ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(annotation.latitude, annotation.longitude))
                            .icon(annotationIcon)
                            .title(annotation.text)
                    )
                }
            }
        }

        LaunchedEffect(aMap, playbackPathPoints) {
            aMap?.let { map ->
                val playbackLocations = playbackPathPoints.filterIsInstance<PathPoint.LocationPoint>()
                if (playbackLocations.size < 2) {
                    playbackPolyline?.remove()
                    playbackPolyline = null
                    return@LaunchedEffect
                }
                val latLngs = playbackLocations.map { AmapUtils.toAmapLatLng(it.locationInfo) }
                if (playbackPolyline == null) {
                    playbackPolyline = map.addPolyline(
                        PolylineOptions()
                            .addAll(latLngs)
                            .color(md_theme_light_primary.toArgb())
                            .width(16f)
                    )
                } else {
                    playbackPolyline?.points = latLngs
                }
            }
        }

        LaunchedEffect(aMap, highlightLocation) {
            aMap?.let { map ->
                val target = highlightLocation
                if (target == null) {
                    highlightMarker?.remove()
                    highlightMarker = null
                    return@LaunchedEffect
                }
                val position = AmapUtils.toAmapLatLng(target)
                if (highlightMarker == null) {
                    highlightMarker = map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .icon(highlightIcon)
                    )
                } else {
                    highlightMarker?.position = position
                }
            }
        }

        // Handle screenshot
        LaunchedEffect(isRunningFinished, aMap) {
            if (isRunningFinished && aMap != null) {
                AmapUtils.takeSnapshot(
                    aMap!!,
                    pathPoints,
                    mapCenter,
                    onSnapshot,
                    kotlin.math.min(mapSize.width, mapSize.height)
                )
            }
        }
        
        LaunchedEffect(mapStyle, aMap) {
            aMap?.mapType = when (mapStyle) {
                MapStyle.SATELLITE -> AMap.MAP_TYPE_SATELLITE
                MapStyle.NIGHT -> AMap.MAP_TYPE_NIGHT
                else -> AMap.MAP_TYPE_NORMAL
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
                        mapInstance.uiSettings.isZoomGesturesEnabled = true
                        mapInstance.uiSettings.isScrollGesturesEnabled = true
                        mapInstance.uiSettings.isRotateGesturesEnabled = true
                        mapInstance.uiSettings.isTiltGesturesEnabled = true
                        mapInstance.setOnMapTouchListener {
                            onUserGesture()
                        }
                        mapInstance.setOnMarkerClickListener { marker ->
                            val match = findClosestAnnotation(marker.position, latestAnnotations)
                            if (match != null) {
                                onAnnotationClick(match)
                                true
                            } else {
                                false
                            }
                        }
                        
                        onMapLoaded()
                    }
                }
            }
        )
    }

    private fun drawPathLines(
        map: AMap,
        pathPoints: List<PathPoint>,
        color: Int,
        width: Float
    ): List<Polyline> {
        val locationInfoList = mutableListOf<LocationInfo>()
        val polylines = mutableListOf<Polyline>()
        
        pathPoints.forEach { pathPoint ->
            when (pathPoint) {
                is PathPoint.EmptyLocationPoint -> {
                    if (locationInfoList.isNotEmpty()) {
                        polylines.add(addPolyline(map, locationInfoList, color, width))
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
            polylines.add(addPolyline(map, locationInfoList, color, width))
        }
        return polylines
    }

    private fun addPolyline(
        map: AMap,
        locationInfoList: List<LocationInfo>,
        color: Int,
        width: Float
    ): Polyline {
        val polylineOptions = PolylineOptions()
            .addAll(locationInfoList.map { AmapUtils.toAmapLatLng(it) })
            .color(color)
            .width(width)
        
        return map.addPolyline(polylineOptions)
    }

    private data class PacePiece(
        val start: LocationInfo,
        val end: LocationInfo,
        val pace: Float,
        val breakBefore: Boolean
    )

    private data class ColoredSegment(
        val points: List<LocationInfo>,
        val color: Int
    )

    private fun drawPaceColoredLines(
        map: AMap,
        pathPoints: List<PathPoint>,
        width: Float
    ): List<Polyline> {
        val segments = buildPaceColoredSegments(pathPoints)
        if (segments.isEmpty()) {
            return drawPathLines(
                map,
                pathPoints,
                md_theme_light_primary.copy(alpha = 0.35f).toArgb(),
                width
            )
        }
        return segments.map { segment ->
            map.addPolyline(
                PolylineOptions()
                    .addAll(segment.points.map { AmapUtils.toAmapLatLng(it) })
                    .color(segment.color)
                    .width(width)
            )
        }
    }

    private fun buildPaceColoredSegments(pathPoints: List<PathPoint>): List<ColoredSegment> {
        val pieces = mutableListOf<PacePiece>()
        var prev: PathPoint.LocationPoint? = null
        var breakBefore = false
        val fallbackIntervalMs = 3000L

        pathPoints.forEach { point ->
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
        var currentColor: Int? = null

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

    private fun paceColorForBucket(bucket: Int, bucketCount: Int): Int {
        if (bucketCount <= 1) return Color(0xFFF5C542).toArgb()
        val t = (bucket.toFloat() / (bucketCount - 1)).coerceIn(0f, 1f)
        val green = Color(0xFF2ECC71)
        val yellow = Color(0xFFF5C542)
        val red = Color(0xFFE74C3C)
        val color = if (t <= 0.5f) {
            lerpColor(green, yellow, t / 0.5f)
        } else {
            lerpColor(yellow, red, (t - 0.5f) / 0.5f)
        }
        return color.toArgb()
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

    private fun findClosestAnnotation(
        position: LatLng,
        annotations: List<RunAiAnnotationPoint>
    ): RunAiAnnotationPoint? {
        if (annotations.isEmpty()) return null
        val threshold = 0.0006
        var best: RunAiAnnotationPoint? = null
        var bestDiff = Double.MAX_VALUE
        annotations.forEach { annotation ->
            val dx = annotation.latitude - position.latitude
            val dy = annotation.longitude - position.longitude
            val diff = dx * dx + dy * dy
            if (diff < bestDiff) {
                bestDiff = diff
                best = annotation
            }
        }
        return if (bestDiff <= threshold * threshold) best else null
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
                mapCenter,
                onSnapshot,
                snapshotSideLength
            )
        }
    }
}
