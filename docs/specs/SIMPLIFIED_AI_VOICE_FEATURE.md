# AI跑步陪伴简化语音功能

## 功能概述

根据requirement2.md的要求，我们对AI跑步陪伴功能进行了大幅简化，实现了更直接、更符合Android通话场景的音频体验。

## 主要改进

### 1. **简化的音频设备优先级**
按照Android通话场景的标准优先级自动选择音频设备：
1. **有线耳机** (最高优先级)
2. **蓝牙SCO** (蓝牙耳机通话模式)
3. **听筒**
4. **扬声器** (最低优先级)

### 2. **自动双向实时语音通话**
- 点击"连接"按钮后，立即开始与Coze大模型的双向实时语音通话
- 无需额外的语音输入/输出控制
- 移除了复杂的语音识别和手动发送消息流程

### 3. **极简UI设计**
- **连接状态**：只保留连接/断开按钮
- **移除功能**：
  - 音频设备选择下拉菜单
  - 音频开关按钮
  - 语音输入按钮
  - 语音输入对话框
  - 复杂的音频控制界面

## 技术实现

### 音频路由管理优化

#### 简化的AudioDeviceType枚举
```kotlin
enum class AudioDeviceType(val displayName: String, val priority: Int) {
    WIRED_HEADSET("有线耳机", 1),        // 最高优先级
    BLUETOOTH_HEADSET("蓝牙SCO", 2),     // 蓝牙SCO
    EARPIECE("听筒", 3),                 // 听筒
    SPEAKER("扬声器", 4);                // 最低优先级
}
```

#### 音频设备优先级逻辑
```kotlin
private fun getAvailableAudioDevices(): List<AudioDeviceType> {
    val devices = mutableListOf<AudioDeviceType>()
    
    // 检查有线耳机（最高优先级）
    if (isWiredHeadsetConnected()) {
        devices.add(AudioDeviceType.WIRED_HEADSET)
    }
    
    // 检查蓝牙SCO耳机
    if (isBluetoothHeadsetConnected()) {
        devices.add(AudioDeviceType.BLUETOOTH_HEADSET)
    }
    
    // 听筒总是可用
    devices.add(AudioDeviceType.EARPIECE)
    
    // 扬声器总是可用（最低优先级）
    devices.add(AudioDeviceType.SPEAKER)
    
    return devices
}
```

### AI连接管理简化

#### 移除的功能
- `toggleAudio()` - 音频开关控制
- `sendUserMessage()` - 手动发送用户消息
- `getCurrentAudioDevice()` - 获取当前音频设备
- `getAvailableAudioDevices()` - 获取可用音频设备列表
- `switchAudioDevice()` - 手动切换音频设备

#### 保留的核心功能
- `connect()` - 连接AI并自动开始双向语音通话
- `disconnect()` - 断开AI连接
- `triggerBroadcast()` - 自动播报跑步状态

### UI组件简化

#### AICompanionCard简化
```kotlin
@Composable
fun AICompanionCard(
    modifier: Modifier = Modifier,
    connectionState: AIConnectionState,
    lastMessage: String,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
)
```

**移除的参数**：
- `isAudioEnabled`
- `currentAudioDevice`
- `availableAudioDevices`
- `onToggleAudio`
- `onVoiceInput`
- `onAudioDeviceChange`

## 用户体验

### 简化的交互流程
1. **开始AI陪跑**：点击"连接"按钮
2. **自动配置**：系统自动选择最优音频设备
3. **开始通话**：立即与AI开始双向实时语音对话
4. **结束通话**：点击"断开"按钮

### 自动化特性
- **音频路由**：根据Android通话标准自动选择最优设备
- **语音通话**：连接后立即开始，无需额外操作
- **设备切换**：插拔耳机时自动切换音频路由
- **状态播报**：根据跑步数据自动触发AI播报

## 技术优势

### 1. **符合Android标准**
- 遵循Android通话场景的音频路由优先级
- 使用标准的蓝牙SCO协议进行语音通话
- 兼容Android系统的音频管理机制

### 2. **简化的代码架构**
- 移除了复杂的UI控制逻辑
- 减少了用户交互的复杂性
- 降低了维护成本

### 3. **更好的用户体验**
- 一键连接即可开始语音对话
- 无需学习复杂的操作流程
- 符合用户对通话应用的使用习惯

## 兼容性说明

- **最低Android版本**: API 21 (Android 5.0)
- **推荐Android版本**: API 23+ (Android 6.0+)
- **音频设备支持**: 所有标准的有线耳机和蓝牙耳机
- **权限要求**: `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`

## 文件变更总结

### 主要修改文件
1. **AudioRouteManager.kt** - 简化音频设备类型和优先级逻辑
2. **AIRunningCompanionManager.kt** - 移除复杂的音频控制功能
3. **AICompanionCard.kt** - 大幅简化UI组件
4. **CurrentRunViewModel.kt** - 移除语音识别和音频控制相关代码
5. **CurrentRunScreen.kt** - 移除语音输入对话框和复杂交互

### 移除的组件
- 音频设备选择下拉菜单
- 语音输入对话框
- 音频开关控制
- 复杂的设备切换逻辑

---

此次简化大大提升了AI跑步陪伴功能的易用性，让用户能够更专注于跑步本身，而不是复杂的音频设置。