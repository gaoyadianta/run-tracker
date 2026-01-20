# 基于WebSocket的可切换大模型实时语音方案设计

## 1. 设计目标

- **传输层可替换**：以 WebSocket 为首期落地方案，后续可无缝替换为 WebRTC/HTTP Streaming
- **模型平台可切换**：火山引擎与阿里云百炼共享统一接口与协议
- **业务层稳定**：跑步陪伴、状态播报、总结等业务逻辑无需感知平台差异
- **清晰职责边界**：VAD/STT/TTS职责明确，利于延迟、成本与质量控制

## 2. 总体架构

```
端侧录音 -> VAD -> 音频帧 -> WebSocket -> 云端AI
                                |-> STT -> LLM -> TTS -> 音频块 -> WebSocket -> 端侧播放
```

### 2.1 架构分层

- **业务层**：AI陪跑逻辑（触发播报、总结、状态同步）
- **会话层**：`AIConversationSession` 维护上下文、状态、错误、重连
- **传输层**：`AIRealtimeTransport` 负责连接与数据收发（首期 WebSocket）
- **平台适配层**：`ProviderAdapter` 将统一协议映射到火山/百炼

## 3. WebSocket 通信协议

### 3.1 基本原则

- **单通道双向**：语音输入与语音输出在同一连接上进行
- **事件驱动**：统一用 `type` 描述事件类别
- **可扩展**：`payload` 允许不同平台附加字段

### 3.2 统一事件模型（示例）

```json
{
  "type": "audio.input",
  "session_id": "uuid",
  "seq": 12,
  "payload": {
    "format": "pcm16",
    "sample_rate": 16000,
    "data": "base64..."
  }
}
```

### 3.3 事件类型建议

- **控制类**
  - `session.start` / `session.end`
  - `conversation.reset`
  - `error`
- **输入类**
  - `audio.input`
  - `text.input`（用于调试或文本直连）
- **输出类**
  - `text.delta`（LLM文本增量）
  - `audio.output`（TTS音频块）
  - `audio.completed`（播报完成）

### 3.4 端侧最小负担

- 端侧仅负责：
  - 采集音频 -> VAD -> 上行音频帧
  - 接收音频块 -> 播放
  - 接收文本增量 -> 更新字幕

## 4. VAD / STT / TTS 职责边界

### 4.1 端侧

- **VAD**：默认在端侧执行
  - 减少无效上行
  - 降低延迟与带宽

### 4.2 云端

- **STT**：云端完成，便于统一多语言与领域词
- **LLM**：云端执行，便于切换模型
- **TTS**：云端完成，统一音色/风格控制

### 4.3 可选策略

- 若端侧环境复杂、噪声大，可在云端增加二次 VAD
- 若需要本地离线模式，可提供端侧 STT/TTS 备选实现（非首期）

## 5. 平台适配设计

### 5.1 统一接口

```kotlin
interface AIRealtimeTransport {
    suspend fun connect(config: AIConnectionConfig)
    suspend fun disconnect()
    fun send(event: AIMessageEvent)
    fun observeEvents(): Flow<AIMessageEvent>
}
```

### 5.2 ProviderAdapter 职责

- 将统一事件转换为平台协议
- 处理鉴权、签名、重连策略
- 输出标准化事件回传业务层

### 5.3 首期对接

- **火山引擎**：沿用语音能力，但通过 WebSocket 解耦 RTC
- **阿里云百炼**：实现独立 Adapter 与鉴权策略

### 5.4 切换方式

- 配置层提供 `ai_provider = volcano | bailian`
- 运行时通过工厂创建对应 Adapter
- 业务层仅依赖统一接口，不感知差异

## 6. 端侧会话流程

1. 用户点击“连接”
2. `AIConversationSession` 创建，建立 WebSocket
3. 发送 `session.start` + 设备能力（采样率/编码）
4. 端侧 VAD 通过后发送 `audio.input`
5. 收到 `text.delta` 更新字幕
6. 收到 `audio.output` 播放音频
7. 收到 `audio.completed` 触发播报完成逻辑

## 7. 错误处理与重连

- **错误类型**：鉴权失败、网络中断、服务拒绝、协议不兼容
- **重连策略**：断线 1/2/4/8 秒指数退避，最多 3 次
- **降级策略**：AI不可用时仅保留跑步功能，不影响主流程

## 8. 实施阶段建议

### 8.1 Phase 1（WebSocket + 火山）

- 完成 `AIRealtimeTransport` WebSocket 实现
- 火山 Adapter 实现统一事件协议
- 验证实时语音链路

### 8.2 Phase 2（阿里云百炼接入）

- 新增百炼 Adapter
- 统一接口下完成切换测试

### 8.3 Phase 3（稳定性与体验优化）

- VAD 参数调优
- 字幕延迟与音频同步优化
- 重连与失败提示完善

## 9. 交付物清单

- `AIRealtimeTransport` WebSocket 实现
- `ProviderAdapter`（火山/百炼）
- `AIMessageEvent` 统一事件协议
- 文档：本方案设计与接口说明
