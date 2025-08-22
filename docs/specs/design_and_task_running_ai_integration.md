# 跑步-AI联动功能设计与任务拆分

## 1. 功能概述

### 当前问题
- **分离控制**: "开始跑步"和"连接AI"是两个独立按钮，用户体验不佳
- **缺少跑步结束总结**: 用户结束跑步时，AI没有生成跑步总结
- **连接失败无提示**: AI连接失败时缺少用户友好的错误提示

### 目标改进
- **自动联动**: 开始跑步时自动连接AI，提升用户体验
- **保留手动断开**: 用户可在跑步中手动断开AI连接
- **跑步结束总结**: 结束跑步时，如果AI处于连接状态，自动发送总结请求
- **总结完成后自动挂断**: AI总结语音播报完成后，自动断开AI连接
- **连接失败Toast提示**: AI连接失败时显示具体的失败原因

## 2. 技术方案设计

### 2.1 架构调整

#### 2.1.1 状态管理优化
```kotlin
// 在CurrentRunViewModel中添加跑步状态与AI状态的联动
enum class RunningState {
    STOPPED,     // 未开始
    STARTING,    // 开始中（准备连接AI）
    RUNNING,     // 跑步中
    PAUSED,      // 暂停
    FINISHING    // 结束中（生成总结）
}

data class IntegratedRunState(
    val runningState: RunningState,
    val aiConnectionState: AIConnectionState,
    val shouldAutoConnectAI: Boolean = true,
    val isGeneratingSummary: Boolean = false
)
```

#### 2.1.2 生命周期管理
```kotlin
// 跑步与AI生命周期的关联
开始跑步 -> 自动连接AI -> 跑步中（可手动断开AI）-> 结束跑步 -> 生成AI总结 -> 总结播报完成 -> 自动断开AI
```

### 2.2 AI总结播报完成检测机制

#### 2.2.1 播报完成状态监听
```kotlin
// 在AIRunningCompanionManager中添加播报完成监听
enum class AIBroadcastState {
    IDLE,           // 空闲状态
    BROADCASTING,   // 播报中
    COMPLETED       // 播报完成
}

data class SummaryBroadcastState(
    val isGeneratingSummary: Boolean = false,
    val broadcastState: AIBroadcastState = AIBroadcastState.IDLE,
    val shouldAutoDisconnectAfterSummary: Boolean = false
)
```

#### 2.2.2 自动断开机制
```kotlin
// 当AI总结播报完成时，自动断开连接
private fun onSummaryBroadcastCompleted() {
    if (summaryBroadcastState.shouldAutoDisconnectAfterSummary) {
        // 延迟一小段时间后断开，确保音频播放完整
        scope.launch {
            delay(1000) // 1秒缓冲时间
            disconnect()
        }
    }
}
```

### 2.3 UI交互设计

#### 2.3.1 简化的控制界面
- **开始跑步按钮**: 同时启动跑步和AI连接
- **AI状态卡片**: 显示连接状态，提供断开选项
- **结束跑步**: 自动生成AI总结后断开

#### 2.3.2 状态指示优化
```kotlin
// AI卡片状态显示
when (integratedState) {
    RunningState.STARTING -> "正在启动AI陪跑..."
    RunningState.RUNNING + CONNECTED -> "AI陪跑中"
    RunningState.RUNNING + DISCONNECTED -> "跑步中（AI已断开）"
    RunningState.FINISHING -> "AI正在生成跑步总结..."
}
```

## 3. 详细任务拆分

### 任务1: 实现跑步-AI自动联动
**优先级**: 🔴 最高
**预估时间**: 3-4小时

#### 3.1.1 状态管理重构
- [ ] 在`CurrentRunViewModel`中添加`IntegratedRunState`
- [ ] 实现跑步状态与AI状态的联动逻辑
- [ ] 添加自动连接AI的控制开关

#### 3.1.2 业务逻辑实现
```kotlin
// CurrentRunViewModel.kt
fun playPauseTrackingWithAI() {
    if (currentRunStateWithCalories.value.currentRunState.isTracking) {
        // 暂停跑步
        trackingManager.pauseTracking()
    } else {
        // 开始跑步时自动连接AI
        startRunWithAI()
    }
}

private fun startRunWithAI() {
    // 首先开始跑步追踪
    trackingManager.startResumeTracking()
    
    // 然后自动连接AI
    viewModelScope.launch {
        try {
            aiCompanionManager.connect()
        } catch (e: Exception) {
            // AI连接失败显示Toast提示，但不影响跑步
            Timber.w(e, "AI连接失败，跑步继续进行")
            showAIConnectionFailureToast(e)
        }
    }
}

private fun showAIConnectionFailureToast(exception: Exception) {
    val errorMessage = when (exception) {
        is NetworkException -> "网络连接失败，请检查网络设置"
        is PermissionException -> "缺少麦克风权限，无法启动AI陪跑"
        is ConfigurationException -> "AI配置错误，请检查设置"
        is ServerException -> "AI服务暂时不可用，请稍后重试"
        else -> "AI连接失败：${exception.message ?: "未知错误"}"
    }
    
    // 显示Toast提示
    _toastMessage.value = errorMessage
}
```

#### 3.1.3 AI连接失败Toast提示实现
- [ ] 在`CurrentRunViewModel`中添加`_toastMessage`状态管理
- [ ] 定义AI连接失败的具体异常类型和错误消息
- [ ] 在UI层监听Toast消息并显示给用户

#### 3.1.4 UI层适配
- [ ] 修改`CurrentRunStatsCard`的开始按钮逻辑，调用新的`playPauseTrackingWithAI()`方法
- [ ] 更新`AICompanionCard`的状态显示
- [ ] 添加AI自动连接的用户提示
- [ ] 在`CurrentRunScreen`中添加Toast消息监听和显示

### 任务2: 实现跑步结束时的AI总结与自动挂断
**优先级**: 🔴 最高  
**预估时间**: 4-5小时

#### 3.2.1 增强AI播报状态管理
```kotlin
// 在AIRunningCompanionManager中添加播报状态追踪
private val _summaryBroadcastState = MutableStateFlow(SummaryBroadcastState())
val summaryBroadcastState = _summaryBroadcastState.asStateFlow()

fun triggerRunSummary(runningContext: RunningContext) {
    _summaryBroadcastState.value = _summaryBroadcastState.value.copy(
        isGeneratingSummary = true,
        broadcastState = AIBroadcastState.BROADCASTING,
        shouldAutoDisconnectAfterSummary = true
    )
    
    triggerBroadcast(runningContext, AIBroadcastType.RUN_SUMMARY)
}
```

#### 3.2.2 播报完成检测机制
- [ ] 在`rtcRoomEventHandler`中监听`CONVERSATION_MESSAGE_COMPLETED`事件
- [ ] 区分普通播报完成和总结播报完成
- [ ] 实现总结播报完成后的自动断开逻辑

#### 3.2.3 新增AI播报类型
- [ ] 在`AIBroadcastType`中添加`RUN_SUMMARY`类型
- [ ] 实现跑步总结的提示词模板
- [ ] 处理总结播报完成的回调

#### 3.2.4 跑步结束流程重构
```kotlin
// CurrentRunViewModel.kt
fun finishRunWithSummary(bitmap: Bitmap) {
    trackingManager.pauseTracking()
    
    // 如果AI连接着，先生成总结
    if (aiConnectionState.value == AIConnectionState.CONNECTED) {
        generateRunSummaryAndWaitForCompletion(bitmap)
    } else {
        // 直接保存跑步数据并结束
        saveRunAndFinish(bitmap)
    }
}

private fun generateRunSummaryAndWaitForCompletion(bitmap: Bitmap) {
    val runningContext = createFinalRunningContext()
    aiCompanionManager.triggerRunSummary(runningContext)
    
    // 监听总结播报完成，然后保存数据
    viewModelScope.launch {
        aiCompanionManager.summaryBroadcastState
            .filter { it.broadcastState == AIBroadcastState.COMPLETED }
            .first()
        
        saveRunAndFinish(bitmap)
    }
}

private fun saveRunAndFinish(bitmap: Bitmap) {
    saveRun(createRunData(bitmap))
    trackingManager.stop()
    // 导航回主页面等其他清理工作
}
```

### 任务3: UI状态优化与用户体验提升
**优先级**: 🟡 高
**预估时间**: 2-3小时

#### 3.3.1 状态指示优化
- [ ] 添加AI总结生成中的加载状态显示
- [ ] 优化AI连接状态的视觉反馈
- [ ] 添加"正在生成跑步总结，请稍候..."的提示

#### 3.3.2 交互体验改进
- [ ] 跑步结束时禁用重复点击结束按钮
- [ ] 显示总结生成进度或动画
- [ ] 总结播报完成后的友好提示

#### 3.3.3 错误处理优化
- [ ] AI连接失败时的降级体验（直接结束跑步）
- [ ] 总结生成超时机制（设置30秒超时）
- [ ] 网络异常时的处理

### 任务4: 完整功能测试与验证
**优先级**: 🟢 中
**预估时间**: 2-3小时

#### 3.4.1 核心流程测试
- [ ] 完整跑步流程：开始→AI自动连接→跑步→结束→AI总结→自动断开
- [ ] 手动断开AI后的跑步结束流程
- [ ] AI连接失败时的降级体验

#### 3.4.2 边界条件测试
- [ ] 快速开始/结束跑步的处理
- [ ] 总结生成过程中的异常处理
- [ ] 多次快速切换状态的稳定性

#### 3.4.3 用户体验测试
- [ ] 各种状态下的UI反馈是否清晰
- [ ] 加载状态和进度提示是否合理
- [ ] 错误提示是否友好

## 4. 实施顺序建议

### 第一阶段: 实现核心联动功能
1. **任务1**: 跑步-AI自动联动
2. **任务2**: AI总结与自动挂断
3. 核心功能集成测试

### 第二阶段: 优化与完善
1. **任务3**: UI状态优化与用户体验提升
2. **任务4**: 完整功能测试与验证
3. 完整回归测试与性能优化

## 5. 技术风险与缓解

### 5.1 AI连接稳定性
**风险**: AI连接可能不稳定，影响跑步体验
**缓解**: 
- AI连接失败不阻断跑步功能
- 提供手动重连选项
- 实现连接超时和重试机制

### 5.2 资源管理
**风险**: 音频资源冲突或内存泄漏
**缓解**:
- 严格的资源生命周期管理
- 异常情况下的资源清理
- 内存泄漏检测和防护

### 5.3 用户体验一致性
**风险**: 自动联动可能让用户感到困惑
**缓解**:
- 清晰的状态提示
- 保留手动控制选项
- 提供设置开关控制自动联动

## 6. 测试用例设计

### 6.1 正常流程测试
- 开始跑步 → AI自动连接 → 跑步中播报 → 结束跑步 → AI总结 → 断开连接

### 6.2 异常场景测试
- AI连接失败时的跑步体验
- 跑步中手动断开AI后的行为
- 网络异常时的处理
- 权限缺失时的降级体验

### 6.3 边界条件测试
- 快速开始/结束跑步
- AI连接中途异常断开
- 总结生成超时
- 多次快速切换状态

## 7. 后续迭代建议

### 7.1 用户个性化设置
- AI自动连接开关设置
- 播报频率个性化调整
- 跑步总结详细程度选择

### 7.2 智能化增强
- 基于跑步历史的个性化播报
- 天气、时间等环境因素集成
- 健康数据关联分析

### 7.3 社交功能扩展
- 跑步总结分享功能
- AI陪跑成就系统
- 社区互动集成