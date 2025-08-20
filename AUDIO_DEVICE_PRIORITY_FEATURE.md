# AI跑步陪伴音频设备优先级功能

## 功能概述

为了改善用户体验，我们为AI跑步陪伴功能添加了智能音频设备选择功能。现在系统会自动优先选择蓝牙耳机等外接音频设备，而不是默认使用扬声器。

## 主要改进

### 1. 音频设备优先级
系统按以下优先级自动选择音频设备：
1. **蓝牙耳机** (最高优先级)
2. **有线耳机**
3. **蓝牙音箱**
4. **听筒**
5. **扬声器** (最低优先级)

### 2. 智能设备切换
- 当连接新的音频设备时，系统会自动切换到优先级更高的设备
- 当断开当前设备时，系统会自动切换到下一个可用的最高优先级设备
- 支持手动切换音频设备

### 3. 用户界面增强
- 在AI陪跑卡片中显示当前使用的音频设备
- 提供音频设备切换菜单
- 实时显示可用的音频设备列表
- 使用直观的图标区分不同类型的音频设备

## 技术实现

### 新增组件

#### AudioRouteManager
- 负责管理音频路由和设备选择
- 监听蓝牙和有线耳机的连接状态变化
- 提供音频设备切换API

#### 主要功能
```kotlin
class AudioRouteManager {
    // 初始化音频路由管理器
    fun initialize()
    
    // 为AI通话配置音频路由
    fun setupForAICall()
    
    // 手动切换到指定音频设备
    fun switchToDevice(deviceType: AudioDeviceType)
    
    // 恢复原始音频设置
    fun restoreOriginalSettings()
    
    // 清理资源
    fun cleanup()
}
```

### 集成到AI陪跑管理器

#### AIRunningCompanionManager增强
```kotlin
class AIRunningCompanionManager {
    // 获取当前音频设备信息
    fun getCurrentAudioDevice(): StateFlow<AudioDeviceType>
    
    // 获取可用音频设备列表
    fun getAvailableAudioDevices(): StateFlow<List<AudioDeviceType>>
    
    // 手动切换音频设备
    fun switchAudioDevice(deviceType: AudioDeviceType)
}
```

### UI组件更新

#### AICompanionCard增强
- 显示当前音频设备状态
- 提供音频设备切换下拉菜单
- 支持一键切换音频设备

## 使用方法

### 自动模式
1. 启动AI陪跑功能
2. 系统自动检测并选择最优音频设备
3. 连接/断开音频设备时自动切换

### 手动切换
1. 在AI陪跑卡片中点击音频设备切换按钮（齿轮图标）
2. 从下拉菜单中选择想要使用的音频设备
3. 系统立即切换到选定的设备

## 权限要求

应用需要以下权限来支持音频设备管理：
- `RECORD_AUDIO` - 录音权限
- `MODIFY_AUDIO_SETTINGS` - 修改音频设置权限
- `BLUETOOTH` - 蓝牙权限（隐式）

## 兼容性

- **最低Android版本**: API 21 (Android 5.0)
- **推荐Android版本**: API 23+ (Android 6.0+) 以获得最佳体验
- 支持所有主流蓝牙耳机和有线耳机

## 注意事项

1. **蓝牙连接**: 确保蓝牙耳机已正确配对并连接
2. **权限授予**: 首次使用时需要授予录音和音频设置权限
3. **设备兼容性**: 某些特殊音频设备可能需要额外配置

## 故障排除

### 常见问题

**Q: 蓝牙耳机已连接但系统仍使用扬声器**
A: 请检查蓝牙耳机是否支持通话功能，并尝试手动切换音频设备

**Q: 音频设备切换后没有声音**
A: 请检查设备音量设置，并确保音频设备正常工作

**Q: 无法看到音频设备切换选项**
A: 请确保已连接到AI陪跑服务，只有在连接状态下才显示音频设备选项

## 开发者信息

### 相关文件
- `AudioRouteManager.kt` - 音频路由管理器
- `AIRunningCompanionManager.kt` - AI陪跑管理器
- `AICompanionCard.kt` - AI陪跑UI组件
- `CurrentRunViewModel.kt` - 当前跑步视图模型
- `CurrentRunScreen.kt` - 当前跑步界面

### 测试建议
1. 测试不同类型音频设备的自动切换
2. 验证手动切换功能
3. 测试设备连接/断开时的行为
4. 验证权限处理逻辑

---

此功能大大提升了AI跑步陪伴的用户体验，让用户能够更方便地使用自己喜欢的音频设备进行跑步训练。