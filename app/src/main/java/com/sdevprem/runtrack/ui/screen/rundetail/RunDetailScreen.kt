package com.sdevprem.runtrack.ui.screen.rundetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdevprem.runtrack.R
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
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.sdevprem.runtrack.domain.tracking.model.PathPoint
import com.sdevprem.runtrack.ui.common.map.MapStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat

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
    var shareMode by remember { mutableStateOf(ShareMode.STORY) }
    var selectedAnnotation by remember { mutableStateOf<RunAiAnnotationPoint?>(null) }
    var isSharing by remember { mutableStateOf(false) }
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
    LaunchedEffect(isPlaybackRunning, playbackTimes, state.run) {
        if (!isPlaybackRunning || playbackTimes.isEmpty()) return@LaunchedEffect
        val totalDurationMs = (playbackTimes.last() - playbackTimes.first()).coerceAtLeast(1L)
        val targetDurationMs = targetPlaybackDurationMs(
            distanceMeters = state.run?.distanceInMeters ?: 0,
            totalDurationMs = totalDurationMs
        )
        val playbackScale = computePlaybackScale(totalDurationMs, targetDurationMs)
        val playbackStep = computePlaybackStep(playbackTimes.size, targetDurationMs)
        var startIndex = closestIndex(playbackTimes, highlightTimeMs) ?: 0
        if (startIndex >= playbackTimes.lastIndex) {
            startIndex = 0
        }
        while (startIndex < playbackTimes.lastIndex) {
            highlightTimeMs = playbackTimes[startIndex]
            val nextIndex = (startIndex + playbackStep).coerceAtMost(playbackTimes.lastIndex)
            val nextTime = playbackTimes[nextIndex]
            val rawDelta = (nextTime - playbackTimes[startIndex]).coerceAtLeast(0L)
            delay(computePlaybackDelayMs(rawDelta, playbackScale))
            startIndex = nextIndex
        }
        highlightTimeMs = playbackTimes.last()
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
        val topInset = paddingValues.calculateTopPadding()
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val maxHeightPx = with(density) { maxHeight.toPx() }
                val collapsedHeight = (maxHeight * 0.34f).coerceIn(220.dp, 280.dp)
                val halfHeight = (maxHeight * 0.58f).coerceIn(collapsedHeight + 80.dp, maxHeight * 0.75f)
                val expandedHeight = (maxHeight * 0.9f).coerceAtLeast(halfHeight + 120.dp)
                    .coerceAtMost(maxHeight)

                val anchors = remember(collapsedHeight, halfHeight, expandedHeight, maxHeightPx) {
                    SheetAnchors(
                        collapsed = maxHeightPx - with(density) { collapsedHeight.toPx() },
                        half = maxHeightPx - with(density) { halfHeight.toPx() },
                        expanded = maxHeightPx - with(density) { expandedHeight.toPx() }
                    )
                }

                var sheetOffset by remember { mutableStateOf(anchors.collapsed) }
                LaunchedEffect(anchors) {
                    sheetOffset = sheetOffset.coerceIn(anchors.expanded, anchors.collapsed)
                }
                val onSheetOffsetChange: (Float) -> Unit = { next ->
                    sheetOffset = next.coerceIn(anchors.expanded, anchors.collapsed)
                }

                val shareAction: (ShareTarget, ShareMode) -> Unit = shareAction@{ target, mode ->
                    val run = state.run ?: return@shareAction
                    if (isSharing) return@shareAction
                    coroutineScope.launch {
                        isSharing = true
                        val result = runCatching {
                            val bitmap = withContext(Dispatchers.Default) {
                                when (mode) {
                                    ShareMode.STORY -> ShareCardRenderer.renderStoryCard(
                                        context = context,
                                        run = run,
                                        oneLiner = state.oneLiner,
                                        summary = state.summary,
                                        target = target
                                    )
                                    ShareMode.QUOTE -> ShareCardRenderer.renderQuoteCard(
                                        context = context,
                                        run = run,
                                        quote = state.oneLiner ?: "Keep moving.",
                                        target = target
                                    )
                                    ShareMode.COMPARE -> ShareCardRenderer.renderCompareCard(
                                        context = context,
                                        run = run,
                                        compareRun = state.compareRun,
                                        target = target
                                    )
                                }
                            }
                            withContext(Dispatchers.IO) {
                                ShareImageUtils.shareBitmap(
                                    context = context,
                                    bitmap = bitmap,
                                    name = "run_${mode.name.lowercase()}_${run.id}_${target.label}.png",
                                    title = "Share Run"
                                )
                            }
                        }
                        isSharing = false
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                "Share failed: ${error.message ?: "Unknown error"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.pathPoints.isNotEmpty()) {
                        RunRouteMap(
                            modifier = Modifier.fillMaxSize(),
                            pathPoints = state.pathPoints,
                            playbackPathPoints = playbackPathPoints,
                            isRunningFinished = true,
                            annotations = state.aiAnnotations,
                            highlightLocation = highlightLocation,
                            mapStyle = mapStyle,
                            allowAutoFollow = false,
                            fitRouteOnLoad = true,
                            onSnapshot = {},
                            onAnnotationClick = { annotation ->
                                selectedAnnotation = annotation
                                highlightTimeMs = annotation.timeOffsetMs
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No route data",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = topInset + 8.dp, end = 8.dp),
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

                    RunHistoryBottomSheet(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset { IntOffset(0, sheetOffset.roundToInt()) }
                            .height(this@BoxWithConstraints.maxHeight)
                            .fillMaxWidth(),
                        sheetOffset = sheetOffset,
                        minOffsetPx = anchors.expanded,
                        maxOffsetPx = anchors.collapsed,
                        onSheetOffsetChange = onSheetOffsetChange,
                        run = state.run,
                        metrics = state.metrics,
                        oneLiner = state.oneLiner,
                        summary = state.summary,
                        selectedAnnotation = selectedAnnotation,
                        annotations = state.aiAnnotations,
                        highlightTimeMs = highlightTimeMs,
                        onHighlightTimeChange = { highlightTimeMs = it },
                        isPlaybackRunning = isPlaybackRunning,
                        isSharing = isSharing,
                        shareTarget = shareTarget,
                        shareMode = shareMode,
                        onShareTargetClick = { target ->
                            shareAction(target, shareMode)
                        },
                        onPlaybackToggle = {
                            if (playbackTimes.isEmpty()) return@RunHistoryBottomSheet
                            if (isPlaybackRunning) {
                                isPlaybackRunning = false
                            } else {
                                if (playbackIndex >= playbackTimes.lastIndex) {
                                    highlightTimeMs = 0L
                                }
                                isPlaybackRunning = true
                            }
                        },
                        onShareTargetChange = { shareTarget = it },
                        onShareModeChange = { shareMode = it },
                        onShareClick = { shareAction(shareTarget, shareMode) }
                    )
                }
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

private enum class ShareMode {
    STORY,
    QUOTE,
    COMPARE
}

private data class SheetAnchors(
    val collapsed: Float,
    val half: Float,
    val expanded: Float
) {
    fun offsetForCollapsed(): Float = collapsed
}

@Composable
private fun RunHistoryBottomSheet(
    modifier: Modifier,
    sheetOffset: Float,
    minOffsetPx: Float,
    maxOffsetPx: Float,
    onSheetOffsetChange: (Float) -> Unit,
    run: com.sdevprem.runtrack.data.model.Run?,
    metrics: com.sdevprem.runtrack.domain.model.RunMetricsData,
    oneLiner: String?,
    summary: String?,
    selectedAnnotation: RunAiAnnotationPoint?,
    annotations: List<RunAiAnnotationPoint>,
    highlightTimeMs: Long,
    onHighlightTimeChange: (Long) -> Unit,
    isPlaybackRunning: Boolean,
    isSharing: Boolean,
    shareTarget: ShareTarget,
    shareMode: ShareMode,
    onShareTargetClick: (ShareTarget) -> Unit,
    onPlaybackToggle: () -> Unit,
    onShareTargetChange: (ShareTarget) -> Unit,
    onShareModeChange: (ShareMode) -> Unit,
    onShareClick: () -> Unit
) {
    val sheetOffsetState = rememberUpdatedState(sheetOffset)
    val minOffsetState = rememberUpdatedState(minOffsetPx)
    val maxOffsetState = rememberUpdatedState(maxOffsetPx)
    val draggableState = rememberDraggableState { delta ->
        val newOffset = (sheetOffsetState.value + delta)
            .coerceIn(minOffsetState.value, maxOffsetState.value)
        onSheetOffsetChange(newOffset)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    enabled = true
                )
        ) {
            SheetHandle(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 96.dp)
            ) {
                RunSummaryHeader(
                    run = run,
                    isPlaybackRunning = isPlaybackRunning,
                    onPlaybackToggle = onPlaybackToggle
                )
                Spacer(modifier = Modifier.height(12.dp))
                RunDistanceBlock(run = run)
                Spacer(modifier = Modifier.height(12.dp))
                RunStatsRow(run = run, metrics = metrics)

                selectedAnnotation?.let { annotation ->
                    Spacer(modifier = Modifier.height(12.dp))
                    AnnotationCard(annotation = annotation)
                }

                Spacer(modifier = Modifier.height(16.dp))
                AiRecapCard(oneLiner = oneLiner, summary = summary)
                Spacer(modifier = Modifier.height(16.dp))
                RunMetricsSection(
                    metrics = metrics,
                    annotations = annotations,
                    highlightTimeMs = highlightTimeMs,
                    onHighlightTimeChange = onHighlightTimeChange
                )
                Spacer(modifier = Modifier.height(16.dp))
                ShareOptionsSection(
                    shareTarget = shareTarget,
                    shareMode = shareMode,
                    isSharing = isSharing,
                    onShareTargetClick = onShareTargetClick,
                    onShareTargetChange = onShareTargetChange,
                    onShareModeChange = onShareModeChange
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSharing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val textPrimary = MaterialTheme.colorScheme.onSurface
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = textPrimary
                        )
                        Text(
                            text = "Preparing share...",
                            style = MaterialTheme.typography.labelMedium,
                            color = textPrimary.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    enabled = !isSharing,
                    onClick = onShareClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE9424A),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "分享记录", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun SheetHandle(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun RunSummaryHeader(
    run: com.sdevprem.runtrack.data.model.Run?,
    isPlaybackRunning: Boolean,
    onPlaybackToggle: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val dateLabel = run?.timestamp?.let { formatter.format(it) } ?: "--"
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.demo_profile_pic),
            contentDescription = "profile",
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Runner",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = textPrimary
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = textMuted
            )
        }
        Button(
            onClick = onPlaybackToggle,
            enabled = run != null,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(
                    id = if (isPlaybackRunning) R.drawable.ic_pause else R.drawable.ic_play
                ),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = if (isPlaybackRunning) "暂停轨迹" else "动态轨迹")
        }
    }
}

@Composable
private fun RunDistanceBlock(
    run: com.sdevprem.runtrack.data.model.Run?
) {
    val distanceKm = run?.distanceInMeters?.div(1000f) ?: 0f
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = String.format(Locale.US, "%.2f", distanceKm),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp
            ),
            color = textPrimary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "公里",
            style = MaterialTheme.typography.titleSmall,
            color = textMuted
        )
    }
}

@Composable
private fun RunStatsRow(
    run: com.sdevprem.runtrack.data.model.Run?,
    metrics: com.sdevprem.runtrack.domain.model.RunMetricsData
) {
    val paceLabel = run?.let {
        RunUtils.formatPace(RunUtils.convertSpeedToPace(it.avgSpeedInKMH))
    } ?: "--"
    val durationLabel = run?.let { DateTimeUtils.getFormattedStopwatchTime(it.durationInMillis) } ?: "--"
    val elevationGain = computeElevationGain(metrics.elevationSeries)
    val elevationLabel = elevationGain?.let { "${it}m" } ?: "--"
    val stepsLabel = run?.totalSteps?.takeIf { it > 0 }?.toString() ?: "--"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatItem(
            label = "平均配速",
            value = paceLabel,
            modifier = Modifier.weight(1f)
        )
        StatItem(
            label = "用时",
            value = durationLabel,
            modifier = Modifier.weight(1f)
        )
        StatItem(
            label = "爬升",
            value = elevationLabel,
            modifier = Modifier.weight(1f)
        )
        StatItem(
            label = "步数",
            value = stepsLabel,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = textPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textMuted
        )
    }
}


@Composable
private fun AnnotationCard(
    annotation: RunAiAnnotationPoint
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        val textPrimary = MaterialTheme.colorScheme.onSurface
        val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "AI 注释",
                style = MaterialTheme.typography.labelLarge,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = annotation.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textMuted
            )
        }
    }
}

@Composable
private fun AiRecapCard(
    oneLiner: String?,
    summary: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        val textPrimary = MaterialTheme.colorScheme.onSurface
        val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "AI 复盘",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = textPrimary
            )
            if (!oneLiner.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = oneLiner,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = summary?.takeIf { it.isNotBlank() } ?: "AI 复盘内容暂未生成。",
                style = MaterialTheme.typography.bodySmall,
                color = textMuted,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShareOptionsSection(
    shareTarget: ShareTarget,
    shareMode: ShareMode,
    isSharing: Boolean,
    onShareTargetClick: (ShareTarget) -> Unit,
    onShareTargetChange: (ShareTarget) -> Unit,
    onShareModeChange: (ShareMode) -> Unit
) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    Column {
        Text(
            text = "分享方式",
            style = MaterialTheme.typography.labelLarge,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareTarget.values().forEach { target ->
                FilterChip(
                    selected = shareTarget == target,
                    onClick = {
                        if (isSharing) return@FilterChip
                        onShareTargetChange(target)
                        onShareTargetClick(target)
                    },
                    enabled = !isSharing,
                    label = { Text(text = target.label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "分享样式",
            style = MaterialTheme.typography.labelLarge,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareMode.values().forEach { mode ->
                FilterChip(
                    selected = shareMode == mode,
                    onClick = { onShareModeChange(mode) },
                    label = { Text(text = mode.toLabel()) }
                )
            }
        }
    }
}

private fun ShareMode.toLabel(): String = when (this) {
    ShareMode.STORY -> "故事卡"
    ShareMode.QUOTE -> "金句卡"
    ShareMode.COMPARE -> "对比卡"
}


private fun computeElevationGain(series: List<com.sdevprem.runtrack.domain.model.MetricPoint>): Int? {
    if (series.size < 2) return null
    var gain = 0f
    for (index in 1 until series.size) {
        val diff = series[index].value - series[index - 1].value
        if (diff > 0) gain += diff
    }
    return gain.roundToInt()
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
        val offsets = times.mapIndexed { index, time ->
            if (time > 0L) time - base else interval * index
        }
        return normalizeOffsets(offsets, interval)
    }
    val interval = estimateInterval(totalDurationMs, locations.size)
    return normalizeOffsets(List(locations.size) { index -> interval * index }, interval)
}

private fun estimateInterval(totalDurationMs: Long, size: Int): Long {
    if (size <= 1) return 1000L
    return (totalDurationMs / (size - 1)).coerceAtLeast(1000L)
}

private fun normalizeOffsets(offsets: List<Long>, fallbackInterval: Long): List<Long> {
    if (offsets.isEmpty()) return offsets
    val safeInterval = fallbackInterval.coerceAtLeast(1L)
    val result = ArrayList<Long>(offsets.size)
    var last = offsets.first().coerceAtLeast(0L)
    result.add(last)
    for (index in 1 until offsets.size) {
        val candidate = offsets[index].coerceAtLeast(0L)
        last = if (candidate <= last) {
            last + safeInterval
        } else {
            candidate
        }
        result.add(last)
    }
    return result
}

private const val MIN_PLAYBACK_DURATION_MS = 18_000L
private const val MAX_PLAYBACK_DURATION_MS = 90_000L
private const val MIN_PLAYBACK_STEP_DELAY_MS = 30L
private const val MAX_PLAYBACK_STEP_DELAY_MS = 650L

private fun targetPlaybackDurationMs(
    distanceMeters: Int,
    totalDurationMs: Long
): Long {
    val distanceKm = distanceMeters / 1000f
    val base = when {
        distanceKm <= 2f -> 18_000L
        distanceKm <= 5f -> 25_000L
        distanceKm <= 10f -> 35_000L
        distanceKm <= 15f -> 45_000L
        distanceKm <= 21f -> 55_000L
        distanceKm <= 42f -> 70_000L
        else -> 85_000L
    }
    val durationMinutes = totalDurationMs / 60_000f
    val adjusted = when {
        durationMinutes < 20f -> base - 3_000L
        durationMinutes > 120f -> base + 10_000L
        else -> base
    }
    return adjusted.coerceIn(MIN_PLAYBACK_DURATION_MS, MAX_PLAYBACK_DURATION_MS)
}

private fun computePlaybackScale(totalDurationMs: Long, targetDurationMs: Long): Float {
    val safeTarget = targetDurationMs.coerceAtLeast(1L)
    return totalDurationMs.toFloat() / safeTarget
}

private fun computePlaybackDelayMs(deltaMs: Long, scale: Float): Long {
    if (scale <= 0f) return MIN_PLAYBACK_STEP_DELAY_MS
    val scaled = deltaMs / scale
    return scaled.roundToLong()
        .coerceIn(MIN_PLAYBACK_STEP_DELAY_MS, MAX_PLAYBACK_STEP_DELAY_MS)
}

private fun computePlaybackStep(totalSteps: Int, targetDurationMs: Long): Int {
    if (totalSteps <= 1) return 1
    val maxSteps = (targetDurationMs / MIN_PLAYBACK_STEP_DELAY_MS).coerceAtLeast(1L).toInt()
    return kotlin.math.ceil(totalSteps / maxSteps.toFloat()).toInt().coerceAtLeast(1)
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
