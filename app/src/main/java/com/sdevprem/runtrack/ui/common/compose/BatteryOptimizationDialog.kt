package com.sdevprem.runtrack.ui.common.compose

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sdevprem.runtrack.R
import com.sdevprem.runtrack.background.tracking.battery.BatteryOptimizationManager

@Composable
fun BatteryOptimizationDialog(
    batteryOptimizationManager: BatteryOptimizationManager,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 检查结果并调用回调
        onConfirm()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "电池优化设置")
        },
        text = {
            Text(
                text = "为了确保在锁屏和后台状态下能够持续记录运动轨迹，建议将本应用加入电池优化白名单。\n\n" +
                        "这样可以防止系统自动停止位置追踪服务。"
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            batteryOptimizationManager.requestIgnoreBatteryOptimizations()
                        } else {
                            batteryOptimizationManager.openBatteryOptimizationSettings()
                        }
                        batteryOptimizationLauncher.launch(intent)
                    } catch (e: Exception) {
                        // 如果无法打开设置，则打开通用设置页面
                        val fallbackIntent = batteryOptimizationManager.openBatteryOptimizationSettings()
                        batteryOptimizationLauncher.launch(fallbackIntent)
                    }
                }
            ) {
                Text("去设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}