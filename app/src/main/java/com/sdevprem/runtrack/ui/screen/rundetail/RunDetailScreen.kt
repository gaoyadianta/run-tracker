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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
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
    val onPlaybackToggle: () -> Unit = {
        if (playbackTimes.isNotEmpty()) {
            if (isPlaybackRunning) {
                isPlaybackRunning = false
            } else {
                highlightTimeMs = playbackTimes.firstOrNull() ?: 0L
                isPlaybackRunning = true
            }
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
            RunContextHeader(
                run = state.run,
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
                val collapsedHeight = (maxHeight * 0.42f).coerceIn(280.dp, 360.dp)

                val anchors = remember(collapsedHeight, maxHeightPx) {
                    SheetAnchors(
                        collapsed = maxHeightPx - with(density) { collapsedHeight.toPx() },
                        expanded = 0f
                    )
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
                            .padding(top = topInset + 10.dp, end = 8.dp),
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
                    Button(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = topInset + 58.dp, end = 8.dp),
                        enabled = playbackTimes.isNotEmpty(),
                        onClick = onPlaybackToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xD91B1F24),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(
                                id = if (isPlaybackRunning) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isPlaybackRunning) "暂停轨迹" else "动态轨迹")
                    }

                    RunHistoryBottomSheet(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        minOffsetPx = anchors.expanded,
                        maxOffsetPx = anchors.collapsed,
                        run = state.run,
                        metrics = state.metrics,
                        oneLiner = state.oneLiner,
                        summary = state.summary,
                        selectedAnnotation = selectedAnnotation,
                        annotations = state.aiAnnotations,
                        highlightTimeMs = highlightTimeMs,
                        onHighlightTimeChange = { highlightTimeMs = it },
                        isSharing = isSharing,
                        shareTarget = shareTarget,
                        shareMode = shareMode,
                        onShareTargetClick = { target ->
                            shareAction(target, shareMode)
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

private data class EnvironmentContext(
    val weatherLabel: String,
    val temperatureLabel: String,
    val pm25Label: String
)

@Composable
private fun RunContextHeader(
    run: com.sdevprem.runtrack.data.model.Run?,
    onNavigateUp: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = remember(run) { buildEnvironmentContext(run) }
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_backward),
                        contentDescription = "返回"
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI跑伴 · 跑步记录",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = textPrimary
                    )
                    Text(
                        text = "Run Summary",
                        style = MaterialTheme.typography.labelSmall,
                        color = textMuted
                    )
                }

                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除记录"
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ContextBadge(
                    label = "${context.weatherLabel} ${context.temperatureLabel}",
                    dotColor = Color(0xFFF7C54B),
                    modifier = Modifier.weight(1f)
                )
                ContextBadge(
                    label = "PM2.5 ${context.pm25Label}",
                    dotColor = Color(0xFF7FB4FF),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ContextBadge(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textPrimary
            )
        }
    }
}

private enum class ShareMode {
    STORY,
    QUOTE,
    COMPARE
}

private data class SheetAnchors(
    val collapsed: Float,
    val expanded: Float
)

@Composable
private fun RunHistoryBottomSheet(
    modifier: Modifier,
    minOffsetPx: Float,
    maxOffsetPx: Float,
    run: com.sdevprem.runtrack.data.model.Run?,
    metrics: com.sdevprem.runtrack.domain.model.RunMetricsData,
    oneLiner: String?,
    summary: String?,
    selectedAnnotation: RunAiAnnotationPoint?,
    annotations: List<RunAiAnnotationPoint>,
    highlightTimeMs: Long,
    onHighlightTimeChange: (Long) -> Unit,
    isSharing: Boolean,
    shareTarget: ShareTarget,
    shareMode: ShareMode,
    onShareTargetClick: (ShareTarget) -> Unit,
    onShareTargetChange: (ShareTarget) -> Unit,
    onShareModeChange: (ShareMode) -> Unit,
    onShareClick: () -> Unit
) {
    var sheetOffset by remember(maxOffsetPx) {
        mutableFloatStateOf(maxOffsetPx)
    }
    val scrollState = rememberScrollState()

    fun consumeSheetDelta(delta: Float): Float {
        val old = sheetOffset
        val newOffset = (old + delta).coerceIn(minOffsetPx, maxOffsetPx)
        sheetOffset = newOffset
        return newOffset - old
    }

    val nestedScrollConnection = remember(minOffsetPx, maxOffsetPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                if (delta < 0f && sheetOffset > minOffsetPx) {
                    return Offset(x = 0f, y = consumeSheetDelta(delta))
                }
                if (delta > 0f && scrollState.value == 0 && sheetOffset < maxOffsetPx) {
                    return Offset(x = 0f, y = consumeSheetDelta(delta))
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                if (delta > 0f && scrollState.value == 0 && sheetOffset < maxOffsetPx) {
                    return Offset(x = 0f, y = consumeSheetDelta(delta))
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(minOffsetPx, maxOffsetPx) {
        sheetOffset = sheetOffset.coerceIn(minOffsetPx, maxOffsetPx)
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                translationY = sheetOffset
            }
            .nestedScroll(nestedScrollConnection),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SheetHandle(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))

            PrimaryRunSummaryCard(
                run = run,
                metrics = metrics
            )
            selectedAnnotation?.let { annotation ->
                Spacer(modifier = Modifier.height(12.dp))
                AnnotationCard(annotation = annotation)
            }
            Spacer(modifier = Modifier.height(16.dp))
            RunMetricsSection(
                metrics = metrics,
                annotations = annotations,
                highlightTimeMs = highlightTimeMs,
                onHighlightTimeChange = onHighlightTimeChange
            )
            Spacer(modifier = Modifier.height(16.dp))
            AiRecapCard(oneLiner = oneLiner, summary = summary)
            Spacer(modifier = Modifier.height(16.dp))
            ShareOptionsSection(
                shareTarget = shareTarget,
                shareMode = shareMode,
                isSharing = isSharing,
                onShareTargetClick = onShareTargetClick,
                onShareTargetChange = onShareTargetChange,
                onShareModeChange = onShareModeChange
            )
            Spacer(modifier = Modifier.height(16.dp))
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
                Spacer(modifier = Modifier.height(16.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
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
private fun PrimaryRunSummaryCard(
    run: com.sdevprem.runtrack.data.model.Run?,
    metrics: com.sdevprem.runtrack.domain.model.RunMetricsData
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateLabel = run?.timestamp?.let { dateFormatter.format(it) } ?: "--"
    val timeLabel = run?.timestamp?.let { timeFormatter.format(it) } ?: "--"
    val distanceKm = run?.distanceInMeters?.div(1000f) ?: 0f
    val textPrimary = Color(0xFFEAF1FA)
    val textMuted = Color(0xFFA8B3C2)
    val paceLabel = run?.let {
        RunUtils.formatPace(RunUtils.convertSpeedToPace(it.avgSpeedInKMH))
    } ?: "--"
    val durationLabel = run?.let { DateTimeUtils.getFormattedStopwatchTime(it.durationInMillis) } ?: "--"
    val caloriesLabel = run?.caloriesBurned?.takeIf { it > 0 }?.toString() ?: "--"
    val cadenceLabel = run?.avgStepsPerMinute?.takeIf { it > 0f }?.let {
        String.format(Locale.US, "%.0f", it)
    } ?: "--"
    val stepsLabel = run?.totalSteps?.takeIf { it > 0 }?.toString() ?: "--"
    val elevationLabel = computeElevationGain(metrics.elevationSeries)?.let { "${it}m" } ?: "--"

    Surface(
        color = Color(0xFF1F2228),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
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
                        text = "${dateLabel}  ${timeLabel} 开始",
                        style = MaterialTheme.typography.labelSmall,
                        color = textMuted
                    )
                }
                Text(
                    text = "跑步基础信息",
                    style = MaterialTheme.typography.labelMedium,
                    color = textMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.2f", distanceKm),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 42.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "km",
                    style = MaterialTheme.typography.titleSmall,
                    color = textMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                PrimaryMetricItem(
                    label = "平均配速",
                    value = paceLabel,
                    textPrimary = textPrimary,
                    textMuted = textMuted,
                    modifier = Modifier.weight(1f)
                )
                PrimaryMetricItem(
                    label = "总用时",
                    value = durationLabel,
                    textPrimary = textPrimary,
                    textMuted = textMuted,
                    modifier = Modifier.weight(1f)
                )
                PrimaryMetricItem(
                    label = "消耗热量",
                    value = if (caloriesLabel == "--") "--" else "${caloriesLabel}kcal",
                    textPrimary = textPrimary,
                    textMuted = textMuted,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                PrimaryMetricItem(
                    label = "平均步频",
                    value = if (cadenceLabel == "--") "--" else "${cadenceLabel}步/分",
                    textPrimary = textPrimary,
                    textMuted = textMuted,
                    modifier = Modifier.weight(1f)
                )
                PrimaryMetricItem(
                    label = "总步数",
                    value = stepsLabel,
                    textPrimary = textPrimary,
                    textMuted = textMuted,
                    modifier = Modifier.weight(1f)
                )
                PrimaryMetricItem(
                    label = "累计爬升",
                    value = elevationLabel,
                    textPrimary = textPrimary,
                    textMuted = textMuted,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PrimaryMetricItem(
    label: String,
    value: String,
    textPrimary: Color,
    textMuted: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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

private fun buildEnvironmentContext(run: com.sdevprem.runtrack.data.model.Run?): EnvironmentContext {
    if (run == null) {
        return EnvironmentContext(
            weatherLabel = "天气未记录",
            temperatureLabel = "--°C",
            pm25Label = "--"
        )
    }

    return EnvironmentContext(
        weatherLabel = "天气未记录",
        temperatureLabel = "--°C",
        pm25Label = "--"
    )
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
