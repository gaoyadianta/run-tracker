package com.sdevprem.runtrack.ui.screen.currentrun.component

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint
import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.ui.common.map.MapProviderFactory
import com.sdevprem.runtrack.ui.common.map.MapStyle

@Composable
fun Map(
    modifier: Modifier = Modifier,
    pathPoints: List<PathPoint>,
    playbackPathPoints: List<PathPoint> = emptyList(),
    isRunningFinished: Boolean,
    annotations: List<RunAiAnnotationPoint> = emptyList(),
    highlightLocation: LocationInfo? = null,
    mapStyle: MapStyle = MapStyle.STANDARD,
    allowAutoFollow: Boolean = true,
    followLocationTrigger: Int = 0,
    onSnapshot: (Bitmap) -> Unit,
    onUserGesture: () -> Unit = {},
    onAnnotationClick: (RunAiAnnotationPoint) -> Unit = {}
) {
    var mapSize by remember { mutableStateOf(Size(0f, 0f)) }
    var mapCenter by remember { mutableStateOf(Offset(0f, 0f)) }
    var isMapLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val rect = it.boundsInRoot()
                mapSize = rect.size
                mapCenter = rect.center
            }
    ) {
        ShowMapLoadingProgressBar(!isMapLoaded)
        
        val context = LocalContext.current
        val mapProviderFactory = remember { MapProviderFactory(context) }
        val mapProvider = remember { mapProviderFactory.getMapProvider() }
        
        mapProvider.MapComposable(
            modifier = Modifier.fillMaxSize(),
            pathPoints = pathPoints,
            playbackPathPoints = playbackPathPoints,
            isRunningFinished = isRunningFinished,
            annotations = annotations,
            highlightLocation = highlightLocation,
            mapStyle = mapStyle,
            allowAutoFollow = allowAutoFollow,
            followLocationTrigger = followLocationTrigger,
            mapCenter = mapCenter,
            mapSize = mapSize,
            onMapLoaded = { isMapLoaded = true },
            onSnapshot = onSnapshot,
            onUserGesture = onUserGesture,
            onAnnotationClick = onAnnotationClick
        )
    }
}

@Composable
private fun ShowMapLoadingProgressBar(
    visible: Boolean = false
) {
    AnimatedVisibility(
        modifier = Modifier
            .fillMaxSize(),
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(),
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .wrapContentSize()
        )
    }
}
