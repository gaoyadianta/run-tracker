package com.sdevprem.runtrack.ui.screen.currentrun.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdevprem.runtrack.ai.model.AIConnectionState
import com.sdevprem.runtrack.ai.audio.AudioRouteManager

@Composable
fun AICompanionCard(
    modifier: Modifier = Modifier,
    connectionState: AIConnectionState,
    lastMessage: String,
    isAudioEnabled: Boolean,
    currentAudioDevice: AudioRouteManager.AudioDeviceType,
    availableAudioDevices: List<AudioRouteManager.AudioDeviceType>,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onToggleAudio: () -> Unit,
    onVoiceInput: () -> Unit,
    onAudioDeviceChange: (AudioRouteManager.AudioDeviceType) -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // AI状态指示器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle, // 使用AccountCircle图标替代SmartToy
                        contentDescription = "AI陪跑",
                        tint = when (connectionState) {
                            AIConnectionState.CONNECTED -> androidx.compose.ui.graphics.Color.Green
                            AIConnectionState.CONNECTING -> androidx.compose.ui.graphics.Color(0xFFFFA500) // Orange
                            AIConnectionState.ERROR -> androidx.compose.ui.graphics.Color.Red
                            AIConnectionState.DISCONNECTED -> androidx.compose.ui.graphics.Color.Gray
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (connectionState) {
                            AIConnectionState.CONNECTED -> "AI陪跑已连接"
                            AIConnectionState.CONNECTING -> "连接中..."
                            AIConnectionState.ERROR -> "连接失败"
                            AIConnectionState.DISCONNECTED -> "AI陪跑未连接"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 连接/断开按钮
                when (connectionState) {
                    AIConnectionState.DISCONNECTED, AIConnectionState.ERROR -> {
                        Button(
                            onClick = onConnectClick,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("连接", fontSize = 12.sp)
                        }
                    }
                    AIConnectionState.CONNECTED -> {
                        Button(
                            onClick = onDisconnectClick,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("断开", fontSize = 12.sp)
                        }
                    }
                    AIConnectionState.CONNECTING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            
            // AI消息显示
            AnimatedVisibility(
                visible = lastMessage.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = lastMessage,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // 音频设备选择
            AnimatedVisibility(
                visible = connectionState == AIConnectionState.CONNECTED
            ) {
                Column {
                    // 当前音频设备显示
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getAudioDeviceIcon(currentAudioDevice),
                                contentDescription = "当前音频设备",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentAudioDevice.displayName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // 音频设备切换按钮
                        var showDeviceMenu by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(
                                onClick = { showDeviceMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "切换音频设备",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showDeviceMenu,
                                onDismissRequest = { showDeviceMenu = false }
                            ) {
                                availableAudioDevices.forEach { device ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = getAudioDeviceIcon(device),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = device.displayName,
                                                    fontSize = 14.sp
                                                )
                                                if (device == currentAudioDevice) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Done,
                                                        contentDescription = "当前设备",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            onAudioDeviceChange(device)
                                            showDeviceMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // 控制按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 音频开关
                        IconButton(
                            onClick = onToggleAudio,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isAudioEnabled) 
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else 
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                )
                        ) {
                            Icon(
                                imageVector = if (isAudioEnabled) Icons.Default.Done else Icons.Default.Close,
                                contentDescription = if (isAudioEnabled) "关闭音频" else "开启音频",
                                tint = if (isAudioEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        
                        // 语音输入按钮
                        IconButton(
                            onClick = onVoiceInput,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "语音输入",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 根据音频设备类型返回对应的图标
 */
@Composable
private fun getAudioDeviceIcon(deviceType: AudioRouteManager.AudioDeviceType) = when (deviceType) {
    AudioRouteManager.AudioDeviceType.BLUETOOTH_HEADSET -> Icons.Default.Call
    AudioRouteManager.AudioDeviceType.WIRED_HEADSET -> Icons.Default.Call
    AudioRouteManager.AudioDeviceType.BLUETOOTH_A2DP -> Icons.Default.Call
    AudioRouteManager.AudioDeviceType.EARPIECE -> Icons.Default.Phone
    AudioRouteManager.AudioDeviceType.SPEAKER -> Icons.Default.AccountCircle
}