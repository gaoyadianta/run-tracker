package com.sdevprem.runtrack.ui.screen.rundetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.common.extension.getDisplayDate
import com.sdevprem.runtrack.common.utils.DateTimeUtils
import com.sdevprem.runtrack.common.utils.RunUtils
import com.sdevprem.runtrack.domain.model.RunAiAnnotationPoint
import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.ui.share.ShareCardRenderer
import com.sdevprem.runtrack.ui.share.ShareImageUtils
import com.sdevprem.runtrack.ui.share.ShareTarget
import com.sdevprem.runtrack.ui.screen.currentrun.component.Map as RunRouteMap
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.abs
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import com.sdevprem.runtrack.ui.common.map.MapStyle
import kotlinx.coroutines.delay

@Composable
fun RunDetailScreen(
    navigateUp: () -> Unit,
    viewModel: RunDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var highlightTimeMs by remember { mutableStateOf(0L) }
    var shareTarget by remember { mutableStateOf(ShareTarget.WECHAT) }
    var selectedAnnotation by remember { mutableStateOf<RunAiAnnotationPoint?>(null) }
    val isDarkTheme = isSystemInDarkTheme()
    var mapStyle by remember(isDarkTheme) {
        mutableStateOf(if (isDarkTheme) MapStyle.NIGHT else MapStyle.STANDARD)
    }
    var isPlaybackRunning by remember { mutableStateOf(false) }

    val locationPoints = remember(state.pathPoints) {
        state.pathPoints.filterIsInstance<PathPoint.LocationPoint>()
    }
    val playbackTimes = remember(locationPoints, state.run) {
        buildTimeOffsets(locationPoints, state.run?.durationInMillis ?: 0L)
    }
    val playbackIndex = remember(playbackTimes, highlightTimeMs) {
        closestIndex(playbackTimes, highlightTimeMs) ?: 0
    }
    val playbackPathPoints = remember(state.pathPoints, playbackIndex, isPlaybackRunning) {
        if (isPlaybackRunning) {
            buildPlaybackPathPoints(state.pathPoints, playbackIndex)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(state.metrics, playbackTimes) {
        if (highlightTimeMs == 0L) {
            val lastTime = state.metrics.paceSeries.lastOrNull()?.timeOffsetMs
                ?: state.metrics.elevationSeries.lastOrNull()?.timeOffsetMs
                ?: playbackTimes.lastOrNull()
                ?: 0L
            highlightTimeMs = lastTime
        }
    }

    val highlightLocation = remember(state.pathPoints, highlightTimeMs, state.run) {
        val duration = state.run?.durationInMillis ?: 0L
        findLocationForTime(state.pathPoints, highlightTimeMs, duration)
    }
    LaunchedEffect(highlightTimeMs, state.aiAnnotations) {
        selectedAnnotation = findClosestAnnotation(
            annotations = state.aiAnnotations,
            targetTimeMs = highlightTimeMs,
            thresholdMs = 60_000L
        )
    }
    LaunchedEffect(isPlaybackRunning, playbackTimes) {
        if (!isPlaybackRunning || playbackTimes.isEmpty()) return@LaunchedEffect
        var startIndex = closestIndex(playbackTimes, highlightTimeMs) ?: 0
        if (startIndex >= playbackTimes.lastIndex) {
            startIndex = 0
        }
        while (startIndex < playbackTimes.size) {
            highlightTimeMs = playbackTimes[startIndex]
            delay(700L)
            startIndex += 1
        }
        isPlaybackRunning = false
    }

    Scaffold(
        topBar = {
            RunDetailTopBar(
                onNavigateUp = navigateUp,
                onDelete = if (state.run == null) null else { { showDeleteDialog = true } }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (state.pathPoints.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        RunRouteMap(
                            modifier = Modifier.fillMaxSize(),
                            pathPoints = state.pathPoints,
                            playbackPathPoints = playbackPathPoints,
                            isRunningFinished = true,
                            annotations = state.aiAnnotations,
                            highlightLocation = highlightLocation,
                            mapStyle = mapStyle,
                            onSnapshot = {},
                            onAnnotationClick = { annotation ->
                                selectedAnnotation = annotation
                                highlightTimeMs = annotation.timeOffsetMs
                            }
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MapStyle.values().forEach { style ->
                                FilterChip(
                                    selected = mapStyle == style,
                                    onClick = { mapStyle = style },
                                    label = {
                                        Text(
                                            text = when (style) {
                                                MapStyle.STANDARD -> "Standard"
                                                MapStyle.SATELLITE -> "Satellite"
                                                MapStyle.NIGHT -> "Night"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                enabled = playbackTimes.isNotEmpty(),
                                onClick = {
                                    if (isPlaybackRunning) {
                                        isPlaybackRunning = false
                                    } else {
                                        if (playbackIndex >= playbackTimes.lastIndex) {
                                            highlightTimeMs = 0L
                                        }
                                        isPlaybackRunning = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(
                                        id = if (isPlaybackRunning) R.drawable.ic_pause else R.drawable.ic_play
                                    ),
                                    contentDescription = if (isPlaybackRunning) "Pause playback" else "Start playback"
                                )
                            }
                        }
                    }
                }

                selectedAnnotation?.let { annotation ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "AI Note",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = annotation.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                state.run?.let { run ->
                    Text(
                        text = run.timestamp.getDisplayDate(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Distance: %.2f km",
                                    run.distanceInMeters / 1000f
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Duration: ${DateTimeUtils.getFormattedStopwatchTime(run.durationInMillis)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val pace = RunUtils.formatPace(
                                RunUtils.convertSpeedToPace(run.avgSpeedInKMH)
                            )
                            Text(
                                text = "Avg pace: $pace /km",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Steps: ${run.totalSteps} • ${String.format(Locale.US, "%.1f", run.avgStepsPerMinute)} spm",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!state.oneLiner.isNullOrBlank()) {
                    Text(
                        text = "AI: ${state.oneLiner}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "AI Recap",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!state.summary.isNullOrBlank()) {
                    Text(
                        text = state.summary ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "AI recap is not available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                RunMetricsSection(
                    metrics = state.metrics,
                    annotations = state.aiAnnotations,
                    highlightTimeMs = highlightTimeMs,
                    onHighlightTimeChange = { highlightTimeMs = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Share",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareTarget.values().forEach { target ->
                        FilterChip(
                            selected = shareTarget == target,
                            onClick = { shareTarget = target },
                            label = { Text(text = target.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val run = state.run ?: return@Button
                            coroutineScope.launch(Dispatchers.Default) {
                                val bitmap = ShareCardRenderer.renderStoryCard(
                                    context = context,
                                    run = run,
                                    oneLiner = state.oneLiner,
                                    summary = state.summary,
                                    target = shareTarget
                                )
                                ShareImageUtils.shareBitmap(
                                    context = context,
                                    bitmap = bitmap,
                                    name = "run_story_${run.id}_${shareTarget.label}.png",
                                    title = "Share Run Story"
                                )
                            }
                        }
                    ) {
                        Text(text = "Story")
                    }
                    Button(
                        onClick = {
                            val run = state.run ?: return@Button
                            coroutineScope.launch(Dispatchers.Default) {
                                val bitmap = ShareCardRenderer.renderQuoteCard(
                                    context = context,
                                    run = run,
                                    quote = state.oneLiner ?: "Keep moving.",
                                    target = shareTarget
                                )
                                ShareImageUtils.shareBitmap(
                                    context = context,
                                    bitmap = bitmap,
                                    name = "run_quote_${run.id}_${shareTarget.label}.png",
                                    title = "Share Run Quote"
                                )
                            }
                        }
                    ) {
                        Text(text = "Quote")
                    }
                    Button(
                        onClick = {
                            val run = state.run ?: return@Button
                            coroutineScope.launch(Dispatchers.Default) {
                                val bitmap = ShareCardRenderer.renderCompareCard(
                                    context = context,
                                    run = run,
                                    compareRun = state.compareRun,
                                    target = shareTarget
                                )
                                ShareImageUtils.shareBitmap(
                                    context = context,
                                    bitmap = bitmap,
                                    name = "run_compare_${run.id}_${shareTarget.label}.png",
                                    title = "Share Run Compare"
                                )
                            }
                        }
                    ) {
                        Text(text = "Compare")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "Delete this run?") },
            text = { Text(text = "This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        state.run?.let {
                            viewModel.deleteRun(it)
                            navigateUp()
                        }
                    }
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunDetailTopBar(
    onNavigateUp: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    TopAppBar(
        title = { Text(text = "Run Details") },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_backward),
                    contentDescription = "Navigate back"
                )
            }
        },
        actions = {
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete run"
                    )
                }
            }
        }
    )
}

private fun findLocationForTime(
    pathPoints: List<com.sdevprem.runtrack.domain.tracking.model.PathPoint>,
    timeOffsetMs: Long,
    totalDurationMs: Long
): LocationInfo? {
    val locations = pathPoints.mapNotNull { it as? com.sdevprem.runtrack.domain.tracking.model.PathPoint.LocationPoint }
    if (locations.isEmpty()) return null
    val offsets = buildTimeOffsets(locations, totalDurationMs)

    var bestIndex = 0
    var bestDiff = Long.MAX_VALUE
    offsets.forEachIndexed { index, offset ->
        val diff = kotlin.math.abs(offset - timeOffsetMs)
        if (diff < bestDiff) {
            bestDiff = diff
            bestIndex = index
        }
    }
    return locations[bestIndex].locationInfo
}

private fun buildTimeOffsets(
    locations: List<com.sdevprem.runtrack.domain.tracking.model.PathPoint.LocationPoint>,
    totalDurationMs: Long
): List<Long> {
    if (locations.isEmpty()) return emptyList()
    val times = locations.map { it.locationInfo.timeMs }
    val hasTimes = times.any { it > 0L }
    if (hasTimes) {
        val base = times.firstOrNull { it > 0L } ?: 0L
        val interval = estimateInterval(totalDurationMs, locations.size)
        return times.mapIndexed { index, time ->
            if (time > 0L) time - base else interval * index
        }
    }
    val interval = estimateInterval(totalDurationMs, locations.size)
    return List(locations.size) { index -> interval * index }
}

private fun estimateInterval(totalDurationMs: Long, size: Int): Long {
    if (size <= 1) return 1000L
    return (totalDurationMs / (size - 1)).coerceAtLeast(1000L)
}

private fun buildPlaybackPathPoints(
    pathPoints: List<com.sdevprem.runtrack.domain.tracking.model.PathPoint>,
    locationIndex: Int
): List<com.sdevprem.runtrack.domain.tracking.model.PathPoint> {
    if (locationIndex < 0) return emptyList()
    val result = mutableListOf<com.sdevprem.runtrack.domain.tracking.model.PathPoint>()
    var currentIndex = 0
    for (point in pathPoints) {
        when (point) {
            is com.sdevprem.runtrack.domain.tracking.model.PathPoint.LocationPoint -> {
                if (currentIndex > locationIndex) break
                result.add(point)
                currentIndex += 1
            }
            is com.sdevprem.runtrack.domain.tracking.model.PathPoint.EmptyLocationPoint -> {
                result.add(point)
            }
        }
    }
    return result
}

private fun closestIndex(times: List<Long>, target: Long): Int? {
    if (times.isEmpty()) return null
    var bestIndex = 0
    var bestDiff = Long.MAX_VALUE
    times.forEachIndexed { index, time ->
        val diff = kotlin.math.abs(time - target)
        if (diff < bestDiff) {
            bestDiff = diff
            bestIndex = index
        }
    }
    return bestIndex
}

private fun findClosestAnnotation(
    annotations: List<RunAiAnnotationPoint>,
    targetTimeMs: Long,
    thresholdMs: Long
): RunAiAnnotationPoint? {
    if (annotations.isEmpty()) return null
    var best: RunAiAnnotationPoint? = null
    var bestDiff = Long.MAX_VALUE
    annotations.forEach { annotation ->
        val diff = abs(annotation.timeOffsetMs - targetTimeMs)
        if (diff < bestDiff) {
            bestDiff = diff
            best = annotation
        }
    }
    return if (bestDiff <= thresholdMs) best else null
}
