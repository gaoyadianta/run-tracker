package com.sdevprem.runtrack.ui.screen.currentrun

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.common.extension.hasLocationPermission
import com.sdevprem.runtrack.common.extension.hasRunTrackingPermissions
import com.sdevprem.runtrack.common.utils.PermissionUtils
import com.sdevprem.runtrack.data.tracking.location.LocationUtils
import com.sdevprem.runtrack.ui.common.compose.BatteryOptimizationDialog
import com.sdevprem.runtrack.ui.common.compose.animation.ComposeUtils
import com.sdevprem.runtrack.ui.common.map.MapStyle
import com.sdevprem.runtrack.ui.screen.currentrun.component.AICompanionCard
import com.sdevprem.runtrack.ui.screen.currentrun.component.CurrentRunStatsCard
import com.sdevprem.runtrack.ui.screen.currentrun.component.Map
import com.sdevprem.runtrack.ui.screen.currentrun.component.NewsNowPlayingCard
import com.sdevprem.runtrack.ui.theme.AppTheme
import kotlinx.coroutines.delay
import android.os.SystemClock

@Composable
@Preview(showBackground = true)
private fun CurrentRunComposable() {
    AppTheme { Surface { CurrentRunScreen(rememberNavController()) } }
}

@Composable
fun CurrentRunScreen(
        navController: NavController,
        viewModel: CurrentRunViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val permissionDeniedMessage = stringResource(R.string.permission_denied_message)
    val permissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionMap ->
                if (!permissionMap.values.all { it })
                        Toast.makeText(
                                        context,
                                        permissionDeniedMessage,
                                        Toast.LENGTH_SHORT
                                )
                                .show()
            }
    val aiPermissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionMap ->
                if (permissionMap[android.Manifest.permission.RECORD_AUDIO] == true) {
                    viewModel.connectAI()
                } else {
                    Toast.makeText(
                            context,
                            "需要麦克风权限才能使用 AI 语音",
                            Toast.LENGTH_SHORT
                    ).show()
                }
            }
    LaunchedEffect(key1 = true) {
        LocationUtils.checkAndRequestLocationSetting(context as Activity)
    }
    var isRunningFinished by rememberSaveable { mutableStateOf(false) }
    var finishRequested by rememberSaveable { mutableStateOf(false) }
    var finishHandled by rememberSaveable { mutableStateOf(false) }
    var shouldShowRunningCard by rememberSaveable { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var allowAutoFollow by rememberSaveable { mutableStateOf(true) }
    var followLocationTrigger by rememberSaveable { mutableStateOf(0) }
    var lastUserGestureAt by remember { mutableStateOf(0L) }
    
    val runState by viewModel.currentRunStateWithCalories.collectAsStateWithLifecycle()
    val runningDurationInMillis by viewModel.runningDurationInMillis.collectAsStateWithLifecycle()
    
    // AI陪跑状态
    val aiLastMessage by viewModel.aiLastMessage.collectAsStateWithLifecycle()
    val integratedRunState by viewModel.integratedRunState.collectAsStateWithLifecycle()
    val newsPlaybackState by viewModel.newsPlaybackState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val runSaveState by viewModel.runSaveState.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = "location_acquisition") {
        if (context.hasLocationPermission()) {
            viewModel.startLocationAcquisition()
        }
    }

    LaunchedEffect(key1 = "show_running_card") {
        delay(ComposeUtils.slideDownInDuration + 200L)
        shouldShowRunningCard = true
    }
    
    LaunchedEffect(lastUserGestureAt) {
        if (lastUserGestureAt == 0L) return@LaunchedEffect
        delay(8_000L)
        allowAutoFollow = true
        followLocationTrigger += 1
    }

    LaunchedEffect(finishRequested, finishHandled) {
        if (!finishRequested || finishHandled) return@LaunchedEffect
        delay(FINISH_SNAPSHOT_TIMEOUT_MS)
        if (!finishHandled) {
            finishHandled = true
            viewModel.finishRun(createFallbackRunBitmap())
        }
    }

    LaunchedEffect(runSaveState) {
        when (runSaveState) {
            RunSaveState.SAVED -> navController.navigateUp()
            RunSaveState.ERROR -> {
                finishRequested = false
                finishHandled = false
                isRunningFinished = false
            }
            else -> Unit
        }
    }

    // 检查电池优化设置
    LaunchedEffect(key1 = "battery_optimization_check") {
        if (viewModel.batteryOptimizationManager.shouldRequestBatteryOptimization()) {
            showBatteryOptimizationDialog = true
        }
    }

    // 显示Toast消息
    LaunchedEffect(key1 = toastMessage) {
        toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    val playPauseButtonOnClick = {
        if (context.hasRunTrackingPermissions()) {
            viewModel.playPauseTracking()
        } else {
            permissionLauncher.launch(
                PermissionUtils.runTrackingPermissions
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Map(
                pathPoints = runState.currentRunState.pathPoints,
                isRunningFinished = isRunningFinished,
                mapStyle = MapStyle.STANDARD,
                allowAutoFollow = allowAutoFollow,
                followLocationTrigger = followLocationTrigger,
                onSnapshot = { bitmap ->
                    if (!finishHandled) {
                        finishHandled = true
                        viewModel.finishRun(bitmap)
                    }
                },
                onUserGesture = {
                    allowAutoFollow = false
                    lastUserGestureAt = SystemClock.elapsedRealtime()
                }
        )
        FloatingActionButton(
                onClick = {
                    allowAutoFollow = true
                    followLocationTrigger += 1
                    lastUserGestureAt = 0L
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = if (shouldShowRunningCard) 140.dp else 24.dp)
                        .size(44.dp)
        ) {
            Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_location_marker),
                    contentDescription = "Follow location"
            )
        }
        TopBar(
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
                onNavigateUp = navController::navigateUp
        )
        
        // AI陪跑卡片
        ComposeUtils.SlideUpAnimatedVisibility(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp, start = 24.dp, end = 24.dp),
                visible = shouldShowRunningCard
        ) {
            Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AICompanionCard(
                        integratedRunState = integratedRunState,
                        lastMessage = aiLastMessage,
                        onConnectClick = {
                            val permissions = PermissionUtils.aiVoicePermissions
                            val allGranted = permissions.all { permission ->
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        permission
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            }
                            if (allGranted) viewModel.connectAI()
                            else aiPermissionLauncher.launch(permissions)
                        },
                        onDisconnectClick = { viewModel.disconnectAI() }
                )
                NewsNowPlayingCard(
                        state = newsPlaybackState,
                        onPrimaryActionClick = { viewModel.toggleNewsPlayback() },
                        onSkipClick = { viewModel.skipNewsReadout() },
                        onStopClick = { viewModel.stopNewsReadout() }
                )
            }
        }
        ComposeUtils.SlideUpAnimatedVisibility(
                modifier = Modifier.align(Alignment.BottomCenter),
                visible = shouldShowRunningCard
        ) {
            CurrentRunStatsCard(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                    onPlayPauseButtonClick = playPauseButtonOnClick,
                    runState = runState,
                    durationInMillis = runningDurationInMillis,
                    onFinish = {
                        if (!finishRequested) {
                            finishRequested = true
                            isRunningFinished = true
                        }
                    }
            )
        }

        // 电池优化对话框
        if (showBatteryOptimizationDialog) {
            BatteryOptimizationDialog(
                    batteryOptimizationManager = viewModel.batteryOptimizationManager,
                    onDismiss = { showBatteryOptimizationDialog = false }
            )
        }
        
    }
}

private fun createFallbackRunBitmap(): Bitmap {
    return Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
}

private const val FINISH_SNAPSHOT_TIMEOUT_MS = 4_000L

@Composable
private fun TopBar(modifier: Modifier = Modifier, onNavigateUp: () -> Unit) {
    IconButton(
            onClick = onNavigateUp,
            modifier =
                    modifier.size(32.dp)
                            .shadow(
                                    elevation = 4.dp,
                                    shape = MaterialTheme.shapes.medium,
                                    clip = true
                            )
                            .background(
                                    color = MaterialTheme.colorScheme.surface,
                            )
                            .padding(4.dp)
    ) {
        Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_back),
                contentDescription = stringResource(id = R.string.navigate_back_button_desc),
                tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
