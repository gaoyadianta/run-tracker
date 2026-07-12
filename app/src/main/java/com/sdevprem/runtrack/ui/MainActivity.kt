package com.sdevprem.runtrack.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.sdevprem.runtrack.data.tracking.location.LocationUtils
import com.sdevprem.runtrack.common.privacy.PrivacyConsentManager
import com.sdevprem.runtrack.ui.screen.main.MainScreen
import com.sdevprem.runtrack.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var privacyConsentManager: PrivacyConsentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var privacyAccepted by remember {
                    mutableStateOf(privacyConsentManager.isAccepted)
                }
                if (!privacyAccepted) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("隐私与权限说明") },
                        text = {
                            Text(
                                "为了记录跑步，本应用会在你开始跑步后使用位置、身体活动和通知权限；" +
                                    "只有在你启用 AI 语音时才使用麦克风和已配对蓝牙设备权限。" +
                                    "跑步路线与统计默认保存在本机。"
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    privacyConsentManager.accept()
                                    privacyAccepted = true
                                }
                            ) { Text("同意并继续") }
                        },
                        dismissButton = {
                            TextButton(onClick = ::finish) { Text("退出") }
                        }
                    )
                }
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (privacyAccepted) {
                        MainScreen(rememberNavController())
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LocationUtils.LOCATION_ENABLE_REQUEST_CODE && resultCode != Activity.RESULT_OK) {
            Toast.makeText(
                this,
                "Please enable GPS to get proper running statistics.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AppTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen(rememberNavController())
        }
    }
}
