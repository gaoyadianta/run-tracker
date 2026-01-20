package com.sdevprem.runtrack.ai.manager

import android.content.Context
import android.util.Log
import com.coze.openapi.client.audio.rooms.CreateRoomReq
import com.coze.openapi.client.audio.rooms.CreateRoomResp
import com.coze.openapi.client.chat.model.ChatEventType
import com.coze.openapi.service.service.CozeAPI
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sdevprem.runtrack.ai.config.CozeConfig
import com.sdevprem.runtrack.ai.model.AIBroadcastType
import com.sdevprem.runtrack.ai.model.AIConnectionState
import com.sdevprem.runtrack.ai.model.RunningContext
import com.sdevprem.runtrack.ai.model.AIBroadcastState
import com.sdevprem.runtrack.ai.model.SummaryBroadcastState
import com.sdevprem.runtrack.ai.audio.AudioRouteManager
import com.ss.bytertc.engine.RTCRoom
import com.ss.bytertc.engine.RTCVideo
import com.ss.bytertc.engine.RTCRoomConfig
import com.ss.bytertc.engine.UserInfo
import com.ss.bytertc.engine.data.StreamIndex
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler
import com.ss.bytertc.engine.type.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelChildren

@Singleton
class AIRunningCompanionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cozeConfig: CozeConfig,
    private val cozeAPIManager: CozeAPIManager,
    private val audioRouteManager: AudioRouteManager
) {
    companion object {
        private const val TAG = "AIRunningCompanion"
        // Coze 用户无操作超时策略不可控，这里通过定期发送 session.update 进行保活
        // 参考示例项目实现：每10分钟刷新一次完整会话配置
        private const val KEEP_ALIVE_INTERVAL_MS = 600_000L // 10分钟心跳
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mapper = ObjectMapper()
    
    private var rtcVideo: RTCVideo? = null
    private var rtcRoom: RTCRoom? = null
    private var roomInfo: CreateRoomResp? = null
    private var cozeAPI: CozeAPI? = null
    private var keepAliveJob: Job? = null
    
    // 状态管理
    private val _connectionState = MutableStateFlow(AIConnectionState.DISCONNECTED)
    val connectionState: StateFlow<AIConnectionState> = _connectionState.asStateFlow()
    
    private val _lastMessage = MutableStateFlow("")
    val lastMessage: StateFlow<String> = _lastMessage.asStateFlow()
    
    // 总结播报状态管理
    private val _summaryBroadcastState = MutableStateFlow(SummaryBroadcastState())
    val summaryBroadcastState: StateFlow<SummaryBroadcastState> = _summaryBroadcastState.asStateFlow()
    
    // 播报控制
    private var lastRegularBroadcastTime = 0L  // 上次常规广播时间（常规/互动类播报）
    private var lastSpecialBroadcastTime = 0L  // 上次特殊广播时间（里程碑、配速提醒等）
    private var broadcastInterval = 120000L // 常规播报间隔：2分钟
    private var specialBroadcastInterval = 30000L // 特殊广播间隔：30秒，避免过于频繁
    private var isProcessingSummary = false // 标记是否正在处理总结播报
    
    /**
     * 初始化AI陪跑功能
     */
    fun initialize() {
        if (!cozeConfig.isConfigured()) {
            Timber.w("Coze配置不完整，无法初始化AI陪跑功能")
            return
        }
        
        // 初始化音频路由管理器
        audioRouteManager.initialize()
        
        cozeAPI = cozeAPIManager.getCozeAPI()
        Timber.d("AI陪跑管理器初始化完成")
    }
    
    /**
     * 连接AI陪跑服务
     */
    fun connect() {
        if (_connectionState.value == AIConnectionState.CONNECTING || 
            _connectionState.value == AIConnectionState.CONNECTED) {
            Timber.d("已在连接中或已连接，忽略重复连接请求")
            return
        }
        
        // 检查录音权限
        if (!hasAudioPermission()) {
            Timber.e("缺少录音权限，无法连接AI陪跑服务")
            _connectionState.value = AIConnectionState.ERROR
            return
        }
        
        // 确保初始化完成
        if (cozeAPI == null) {
            initialize()
        }
        
        if (!cozeConfig.isConfigured()) {
            Timber.e("Coze配置不完整，无法连接AI陪跑服务")
            _connectionState.value = AIConnectionState.ERROR
            return
        }
        
        if (cozeAPI == null) {
            Timber.e("CozeAPI未初始化，无法连接AI陪跑服务")
            _connectionState.value = AIConnectionState.ERROR
            return
        }
        
        _connectionState.value = AIConnectionState.CONNECTING
        Timber.d("开始连接AI陪跑服务...")
        
        scope.launch(Dispatchers.IO) {
            try {
                // 创建Coze房间
                val req = CreateRoomReq.builder()
                    .botID(cozeConfig.botID)
                    .voiceID(cozeConfig.voiceID)
                    .build()
                
                Timber.d("创建Coze房间请求: botID=${cozeConfig.botID}, voiceID=${cozeConfig.voiceID}")
                
                val tempRoomInfo = cozeAPI!!.audio().rooms().create(req)
                
                if (tempRoomInfo == null) {
                    throw Exception("创建Coze房间失败，返回null")
                }
                
                Timber.d("Coze房间创建成功: roomID=${tempRoomInfo.roomID}, appID=${tempRoomInfo.appID}")
                
                roomInfo = tempRoomInfo
                
                scope.launch(Dispatchers.Main) {
                    setupRTCEngine()
                }
            } catch (e: Exception) {
                Timber.e(e, "连接AI陪跑服务失败")
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = AIConnectionState.ERROR
                }
            }
        }
    }
    
    /**
     * 断开AI陪跑服务
     */
    fun disconnect() {
        try {
            Timber.d("开始断开AI陪跑服务")
            
            // 更新状态
            _connectionState.value = AIConnectionState.DISCONNECTED
            
            // 停止心跳保活
            stopKeepAlive()

            // 恢复原始音频设置
            audioRouteManager.restoreOriginalSettings()
            
            // 清理RTC资源
            cleanupRTCResources()
            
            // 清理房间信息
            roomInfo = null
            
            Timber.d("AI陪跑服务已断开")
        } catch (e: Exception) {
            Timber.e(e, "断开AI陪跑服务时出错")
            // 即使出错也要确保状态正确
            _connectionState.value = AIConnectionState.DISCONNECTED
        }
    }
    
    /**
     * 检查RTC资源状态
     */
    private fun checkRTCResources(): Boolean {
        val rtcVideoValid = rtcVideo != null
        val rtcRoomValid = rtcRoom != null
        val roomInfoValid = roomInfo != null
        
        Timber.d("RTC资源状态检查: RTCVideo=$rtcVideoValid, RTCRoom=$rtcRoomValid, RoomInfo=$roomInfoValid")
        
        return rtcVideoValid && rtcRoomValid && roomInfoValid
    }
    
    /**
     * 安全的资源销毁
     */
    fun destroy() {
        try {
            Timber.d("开始销毁AI陪跑管理器")
            
            // 先断开连接
            disconnect()
            
            // 清理音频路由管理器
            audioRouteManager.cleanup()
            
            // 取消所有协程
            scope.coroutineContext.cancelChildren()
            
            Timber.d("AI陪跑管理器销毁完成")
        } catch (e: Exception) {
            Timber.e(e, "销毁AI陪跑管理器时出错")
        }
    }
    
    /**
     * 根据跑步数据触发AI播报
     */
    fun triggerBroadcast(runningContext: RunningContext, broadcastType: AIBroadcastType? = null) {
        if (_connectionState.value != AIConnectionState.CONNECTED) {
            return
        }

        val type = broadcastType ?: AIBroadcastType.getByContext(runningContext)

        // 根据广播类型检查时间间隔
        val currentTime = System.currentTimeMillis()
        val shouldSend = when (type) {
            AIBroadcastType.RUN_SUMMARY -> {
                // 总结播报不受时间间隔限制，但完成一次总结后
                // 也视为一次深度交互，重置常规播报计时
                lastRegularBroadcastTime = currentTime
                true
            }
            AIBroadcastType.MILESTONE_CELEBRATION,
            AIBroadcastType.PACE_REMINDER -> {
                // 里程碑和配速提醒使用特殊间隔
                if (currentTime - lastSpecialBroadcastTime >= specialBroadcastInterval) {
                    lastSpecialBroadcastTime = currentTime
                    // 标志性事件触发后，重置常规播报计时
                    lastRegularBroadcastTime = currentTime
                    true
                } else {
                    false
                }
            }
            else -> {
                // 常规广播（鼓励、专业建议等）使用常规间隔
                if (currentTime - lastRegularBroadcastTime >= broadcastInterval) {
                    Timber.d("允许发送常规AI广播，上次广播时间: $lastRegularBroadcastTime, 当前时间: $currentTime, 类型: ${type.name}")
                    lastRegularBroadcastTime = currentTime
                    true
                } else {
                    Timber.d("拒绝发送AI广播，上次广播时间: $lastRegularBroadcastTime, 当前时间: $currentTime, 类型: ${type.name}, 间隔: ${currentTime - lastRegularBroadcastTime}ms")
                    false
                }
            }
        }

        if (!shouldSend) {
            return // 播报间隔未到
        }

        val prompt = buildPrompt(type, runningContext)
        sendMessageToAI(prompt)
    }
    
    /**
     * 触发跑步总结播报
     */
    fun triggerRunSummary(runningContext: RunningContext) {
        if (_connectionState.value != AIConnectionState.CONNECTED) {
            Timber.w("AI未连接，无法触发跑步总结")
            return
        }
        
        Timber.d("开始触发跑步总结播报")
        
        // 更新总结播报状态
        _summaryBroadcastState.value = _summaryBroadcastState.value.copy(
            isGeneratingSummary = true,
            broadcastState = AIBroadcastState.BROADCASTING,
            shouldAutoDisconnectAfterSummary = true
        )
        
        isProcessingSummary = true
        
        // 清空之前的消息，准备接收总结内容
        _lastMessage.value = ""
        
        // 触发总结播报
        triggerBroadcast(runningContext, AIBroadcastType.RUN_SUMMARY)
    }
    
    
    /**
     * 设置播报间隔
     */
    fun setBroadcastInterval(intervalMinutes: Int) {
        broadcastInterval = intervalMinutes * 60000L
    }

    /**
     * 设置调试模式下的播报间隔（以秒为单位）
     */
    fun setDebugBroadcastInterval(intervalSeconds: Int) {
        broadcastInterval = intervalSeconds * 1000L
        specialBroadcastInterval = intervalSeconds * 1000L
    }
    
    
    
    private fun setupRTCEngine() {
        try {
            val currentRoomInfo = roomInfo
            if (currentRoomInfo == null) {
                throw Exception("房间信息为空，无法设置RTC引擎")
            }
            
            if (currentRoomInfo.appID.isNullOrBlank()) {
                throw Exception("AppID为空，无法创建RTC引擎")
            }
            
            if (currentRoomInfo.roomID.isNullOrBlank()) {
                throw Exception("RoomID为空，无法创建RTC房间")
            }
            
            if (currentRoomInfo.token.isNullOrBlank()) {
                throw Exception("Token为空，无法加入RTC房间")
            }
            
            if (currentRoomInfo.uid.isNullOrBlank()) {
                throw Exception("UID为空，无法加入RTC房间")
            }
            
            Timber.d("开始创建RTC引擎，AppID: ${currentRoomInfo.appID}")
            
            // 先清理之前的实例
            try {
                rtcRoom?.apply {
                    leaveRoom()
                    destroy()
                }
                rtcVideo?.apply {
                    stopAudioCapture()
                    RTCVideo.destroyRTCVideo()
                }
                rtcRoom = null
                rtcVideo = null
            } catch (e: Exception) {
                Timber.w(e, "清理之前的RTC实例时出错，继续创建新实例")
            }
            
            // 创建RTC引擎
            rtcVideo = RTCVideo.createRTCVideo(
                context.applicationContext, // 使用applicationContext避免内存泄漏
                currentRoomInfo.appID,
                rtcVideoEventHandler,
                null,
                null
            )
            
            if (rtcVideo == null) {
                throw Exception("创建RTC引擎失败，返回null")
            }
            
            Timber.d("RTC引擎创建成功，开始配置音频场景")
            
            // 配置音频路由，优先选择蓝牙耳机等外接设备
            try {
                Timber.d("开始配置AI通话音频路由...")
                audioRouteManager.setupForAICall()
                Timber.d("音频路由配置完成")
                
                // 延迟检查音频设备状态，给蓝牙SCO时间建立连接
                scope.launch {
                    delay(500)
                    val currentDevice = audioRouteManager.currentAudioDevice.value
                    val availableDevices = audioRouteManager.availableDevices.value
                    Timber.d("音频路由配置后状态: 当前设备=${currentDevice.displayName}, 可用设备=${availableDevices.map { it.displayName }}")
                }
                
            } catch (e: Exception) {
                Timber.w(e, "配置音频路由失败，使用默认配置")
            }
            
            // 设置音频场景为通信模式，这是最稳定的配置
            try {
                rtcVideo?.setAudioScenario(com.ss.bytertc.engine.type.AudioScenarioType.AUDIO_SCENARIO_COMMUNICATION)
                Timber.d("音频场景设置完成：通信模式")
            } catch (e: Exception) {
                Timber.w(e, "设置音频场景失败，使用默认配置")
            }
            
            Timber.d("开始音频采集")
            
            // 开启音频采集，连接后自动开始双向实时语音通话
            try {
                rtcVideo?.startAudioCapture()
                Timber.d("音频采集启动成功，已开始双向实时语音通话")
            } catch (e: Exception) {
                Timber.e(e, "启动音频采集失败")
                throw Exception("音频采集启动失败: ${e.message}")
            }
            
            // 开始创建RTC房间，RoomID: ${currentRoomInfo.roomID}")
            rtcRoom = rtcVideo?.createRTCRoom(currentRoomInfo.roomID)
            rtcRoom?.let { room ->
                room.setRTCRoomEventHandler(rtcRoomEventHandler)
                Timber.d("RTC房间创建成功，开始配置房间参数")
                
                // 创建并配置房间配置对象
                val roomConfig = RTCRoomConfig(
                    ChannelProfile.CHANNEL_PROFILE_COMMUNICATION,
                    true, true, true
                )
                
                // 创建用户信息
                val userInfo = UserInfo(currentRoomInfo.uid, "")
                
                Timber.d("房间配置完成，开始加入房间，UID: ${currentRoomInfo.uid}")
                room.joinRoom(currentRoomInfo.token, userInfo, roomConfig)
            } ?: run {
                throw Exception("RTC房间创建失败")
            }
            
            _connectionState.value = AIConnectionState.CONNECTED
            Timber.d("RTC引擎设置完成，已连接到AI陪跑服务")

            // 更新房间配置，将最长沉默时间设置为30分钟（1800000毫秒）
            // 参考 SessionManager 方案：使用完整的会话配置结构
            updateRoomConfig()

            // 连接稳定后再补发一次配置，提升生效概率
            scope.launch {
                delay(3000)
                if (_connectionState.value == AIConnectionState.CONNECTED) {
                    Timber.d("连接稳定后再次更新会话配置")
                    updateRoomConfig()
                }
            }

            // 启动会话保活心跳，定期刷新会话配置，避免被重置为默认3分钟
            startKeepAlive()

        } catch (e: Exception) {
            Timber.e(e, "设置RTC引擎失败")
            _connectionState.value = AIConnectionState.ERROR

            // 清理资源
            cleanupRTCResources()
        }
    }

    /**
     * 更新房间配置，设置最长沉默时间为30分钟
     * 参考官方示例中的 SessionManager 实现，构造完整的会话配置结构
     */
    private fun updateRoomConfig() {
        try {
            Timber.d("=== 更新会话配置，尝试将沉默超时时间延长到30分钟 ===")

            // 构建完整的会话配置数据
            val sessionData = mapOf(
                "longest_silence_ms" to 1_800_000,  // 30分钟 = 30 * 60 * 1000 毫秒
                "speech_rate" to 0,                 // 正常语速
                "event_subscriptions" to listOf(    // 订阅常用事件，方便调试
                    "error",
                    "session.updated",
                    "conversation.message.delta",
                    "conversation.message.completed"
                ),
                "turn_detection" to mapOf(          // 语音检测配置
                    "type" to "server_vad",
                    "prefix_padding_ms" to 600,
                    "silence_duration_ms" to 500
                ),
                "asr_config" to mapOf(              // 语音识别配置
                    "enable_itn" to true,
                    "enable_punc" to true,
                    "enable_ddc" to true
                ),
                "chat_config" to mapOf(             // 会话配置
                    "allow_voice_interrupt" to true,
                    "interrupt_config" to mapOf(
                        "mode" to "all"
                    )
                )
            )

            val eventData = mapOf(
                "id" to "session_update_${System.currentTimeMillis()}",
                "event_type" to "session.update",
                "data" to sessionData
            )

            val messageStr = mapper.writeValueAsString(eventData)
            Timber.d("发送房间配置更新消息: $messageStr")

            // 发送给房间内的智能体（bot_id）
            val targetUserId = cozeConfig.botID

            val result = rtcRoom?.sendUserMessage(
                targetUserId,
                messageStr,
                MessageConfig.RELIABLE_ORDERED
            )

            Timber.d("房间配置更新消息发送结果: $result, targetUserId=$targetUserId")
        } catch (e: Exception) {
            Timber.e(e, "更新房间配置失败")
        }
    }

    /**
     * 启动会话保活心跳，通过定期发送 session.update 保持会话活跃
     */
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                if (_connectionState.value == AIConnectionState.CONNECTED) {
                    Timber.d("定期刷新会话配置，保持沉默超时时间为30分钟")
                    updateRoomConfig()
                }
            }
        }
    }

    /**
     * 停止心跳协程
     */
    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    /**
     * 清理RTC资源
     */
    private fun cleanupRTCResources() {
        try {
            // 确保在资源清理时停止心跳协程
            stopKeepAlive()

            rtcRoom?.apply {
                try {
                    leaveRoom()
                } catch (e: Exception) {
                    Timber.w(e, "离开房间时出错")
                }
                try {
                    destroy()
                } catch (e: Exception) {
                    Timber.w(e, "销毁房间时出错")
                }
            }
            
            rtcVideo?.apply {
                try {
                    stopAudioCapture()
                } catch (e: Exception) {
                    Timber.w(e, "停止音频采集时出错")
                }
                try {
                    RTCVideo.destroyRTCVideo()
                } catch (e: Exception) {
                    Timber.w(e, "销毁RTC引擎时出错")
                }
            }
            
            rtcRoom = null
            rtcVideo = null
            
            Timber.d("RTC资源清理完成")
        } catch (e: Exception) {
            Timber.e(e, "清理RTC资源时出错")
        }
    }
    
    private val rtcVideoEventHandler = object : IRTCVideoEventHandler() {
        override fun onWarning(warn: Int) {
            Timber.w("RTC警告: $warn")
        }
        
        override fun onError(err: Int) {
            Timber.e("RTC错误: $err")
            // 根据错误码提供更详细的错误信息
            val errorMessage = getRTCErrorMessage(err)
            Timber.e("RTC错误详情: $errorMessage")
            
            // 对于严重错误，更新连接状态
            if (isSeriousRTCError(err)) {
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = AIConnectionState.ERROR
                    // 清理资源
                    cleanupRTCResources()
                }
            }
        }
    }
    
    private val rtcRoomEventHandler = object : IRTCRoomEventHandler() {
        override fun onRoomStateChanged(roomId: String, uid: String, state: Int, extraInfo: String) {
            Timber.d("房间状态变化: roomId=$roomId, uid=$uid, state=$state, extraInfo=$extraInfo")
            
            // 根据房间状态更新连接状态
            when (state) {
                1 -> { // 加入房间成功
                    Timber.d("成功加入房间")
                    scope.launch(Dispatchers.Main) {
                        _connectionState.value = AIConnectionState.CONNECTED
                    }
                }
                2 -> { // 离开房间
                    Timber.d("离开房间")
                    scope.launch(Dispatchers.Main) {
                        _connectionState.value = AIConnectionState.DISCONNECTED
                    }
                }
                3 -> { // 房间连接失败
                    Timber.e("房间连接失败")
                    scope.launch(Dispatchers.Main) {
                        _connectionState.value = AIConnectionState.ERROR
                    }
                }
            }
        }
        
        override fun onUserMessageReceived(uid: String, message: String) {
            try {
                Timber.d("收到用户消息: uid=$uid, message长度=${message.length}, message内容=$message")

                val messageMap = mapper.readValue<Map<String, Any>>(
                    message,
                    object : TypeReference<Map<String, Any>>() {}
                )

                val jsonMap = messageMap.mapValues { (_, value) ->
                    when (value) {
                        is String -> value
                        else -> mapper.writeValueAsString(value)
                    }
                }

                Timber.d("解析后的事件类型: ${jsonMap["event_type"]}, 完整数据: $jsonMap")

                when (jsonMap["event_type"]) {
                    ChatEventType.CONVERSATION_MESSAGE_DELTA.value -> {
                        val data = jsonMap["data"] as? String
                        data?.let {
                            try {
                                Timber.d("接收到CONVERSATION_MESSAGE_DELTA事件，原始数据: $data")

                                // 简化消息解析，直接使用data内容
                                // 如果data是JSON字符串，尝试解析；否则直接使用
                                val messageContent = try {
                                    val dataMap = mapper.readValue<Map<String, Any>>(data, object : TypeReference<Map<String, Any>>() {})
                                    dataMap["content"] as? String ?: data
                                } catch (e: Exception) {
                                    // 如果解析失败，直接使用原始数据
                                    Timber.d("解析消息JSON失败，使用原始数据: ${e.message}")
                                    data
                                }
                                Timber.d("解析后的消息内容: $messageContent")
                                updateMessage(messageContent)
                            } catch (e: Exception) {
                                Timber.e(e, "解析消息内容失败")
                            }
                        }
                    }
                    ChatEventType.CONVERSATION_MESSAGE_COMPLETED.value -> {
                        Timber.d("AI消息播报完成")

                        // 检查是否是总结播报完成
                        if (isProcessingSummary) {
                            Timber.d("检测到跑步总结播报完成")
                            onSummaryBroadcastCompleted()
                        }

                        // 任意一次完整对话/播报完成后，重置常规播报计时，
                        // 避免在短时间内再次触发自动常规播报
                        lastRegularBroadcastTime = System.currentTimeMillis()
                    }
                    // 添加对智能体语音事件的处理
                    "audio.agent.speech_started" -> {
                        Timber.d("智能体开始说话")
                    }
                    "audio.agent.speech_stopped" -> {
                        Timber.d("智能体结束说话")
                    }
                    "conversation.message.completed" -> {
                        Timber.d("消息完成事件")
                    }
                    "conversation.chat.completed" -> {
                        Timber.d("对话完成事件")
                    }
                    "error" -> {
                        // 错误事件的 data 在 jsonMap 中总是被序列化为字符串，这里按字符串 JSON 解析
                        val dataStr = jsonMap["data"] as? String
                        var errorCode: Int? = null
                        var errorMsg: String? = null

                        if (!dataStr.isNullOrEmpty()) {
                            try {
                                val dataMap = mapper.readValue<Map<String, Any>>(
                                    dataStr,
                                    object : TypeReference<Map<String, Any>>() {}
                                )
                                errorCode = (dataMap["code"] as? Number)?.toInt()
                                errorMsg = dataMap["msg"] as? String
                            } catch (e: Exception) {
                                Timber.e(e, "解析错误事件data失败, 原始data: $dataStr")
                            }
                        }

                        Timber.e("收到错误事件: code=$errorCode, msg=$errorMsg")

                        // 4029：The connection was closed due to prolonged user inactivity.
                        if (errorCode == 4029) {
                            Timber.w("检测到用户长时间无操作导致会话被服务端关闭(code=4029)，后续将依赖自动重连与心跳进行恢复")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "解析AI消息失败")
            }
        }
        
        override fun onRoomWarning(warn: Int) {
            Timber.w("房间警告: $warn")
        }
        
        override fun onRoomError(err: Int) {
            Timber.e("房间错误: $err")
            val errorMessage = getRoomErrorMessage(err)
            Timber.e("房间错误详情: $errorMessage")
            
            scope.launch(Dispatchers.Main) {
                _connectionState.value = AIConnectionState.ERROR
                // 对于可恢复的错误，尝试重新连接
                if (isRecoverableRoomError(err)) {
                    Timber.d("检测到可恢复错误，准备重连...")
                    // 延迟重连，避免立即重连可能导致的循环
                    scope.launch(Dispatchers.Main) {
                        delay(3000) // 3秒后重连
                        // 只要当前不在已连接状态（可能是 ERROR 或 DISCONNECTED），都尝试重连
                        if (_connectionState.value != AIConnectionState.CONNECTED) {
                            Timber.d("开始自动重连...")
                            connect()
                        }
                    }
                }
            }
        }
        
        override fun onLeaveRoom(stats: com.ss.bytertc.engine.type.RTCRoomStats) {
            Timber.d("离开房间，统计信息: $stats")
            scope.launch(Dispatchers.Main) {
                // 离开房间后停止心跳
                stopKeepAlive()
                _connectionState.value = AIConnectionState.DISCONNECTED
            }
        }
    }
    
    /**
     * 获取RTC错误信息
     */
    private fun getRTCErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            -1001 -> "网络连接失败"
            -1002 -> "服务器连接失败"
            -1003 -> "参数错误"
            -1004 -> "权限不足"
            -1005 -> "资源不足"
            -1006 -> "操作超时"
            -1007 -> "设备不支持"
            -1008 -> "版本不兼容"
            else -> "未知RTC错误: $errorCode"
        }
    }
    
    /**
     * 获取房间错误信息
     */
    private fun getRoomErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            -1006 -> "连接超时或Token过期"
            -1001 -> "网络连接失败"
            -1002 -> "服务器连接失败"
            -1003 -> "房间不存在或已关闭"
            -1004 -> "用户被踢出房间"
            -1005 -> "房间人数已满"
            -1007 -> "权限不足"
            -1008 -> "Token无效"
            else -> "房间连接错误: $errorCode"
        }
    }
    
    /**
     * 判断是否为严重的RTC错误
     */
    private fun isSeriousRTCError(errorCode: Int): Boolean {
        return when (errorCode) {
            -1001, -1002, -1003, -1004, -1005, -1006 -> true
            else -> false
        }
    }
    
    /**
     * 判断是否为可恢复的房间错误
     */
    private fun isRecoverableRoomError(errorCode: Int): Boolean {
        return when (errorCode) {
            -1006, -1001, -1002 -> true // 网络相关错误可以重试
            else -> false
        }
    }
    
    private fun updateMessage(content: String) {
        scope.launch {
            val currentMessage = _lastMessage.value
            _lastMessage.value = currentMessage + content
            Timber.d("更新AI消息: $content, 当前完整消息: ${_lastMessage.value}")

            // 添加调试信息，检查是否触发了音频播放
            Timber.d("AI消息更新后，当前连接状态: ${_connectionState.value}")
        }
    }
    
    private fun sendMessageToAI(prompt: String) {
        try {
            Timber.d("发送消息到AI: $prompt")

            // 根据 Coze Realtime 文档构造 conversation.message.create 事件：
            // data 字段必须是一个对象，而不是 JSON 字符串
            val messageData = mapOf(
                "role" to "user",          // 表示是用户发送的消息
                "content_type" to "text",  // 纯文本内容
                "content" to prompt         // 实际消息内容
            )

            val payload = mapOf(
                "id" to "broadcast_${System.currentTimeMillis()}",
                "event_type" to "conversation.message.create",  // 创建对话消息
                "data" to messageData
            )

            val messageStr = mapper.writeValueAsString(payload)
            Timber.d("发送的消息内容: $messageStr")

            // 根据 Coze 文档，目标 user_id 应该是创建房间时传入的 bot_id
            val targetUserId = cozeConfig.botID

            val result = rtcRoom?.sendUserMessage(
                targetUserId,
                messageStr,
                MessageConfig.RELIABLE_ORDERED
            )

            Timber.d("发送消息结果: $result, targetUserId=$targetUserId")

            if (result != null && result != 0L) {
                Timber.w("发送消息到AI失败，返回码: $result, 请检查user_id是否正确以及房间状态是否正常")
            }

            // 清空上一条消息（用于 UI 展示）
            _lastMessage.value = ""

        } catch (e: Exception) {
            Timber.e(e, "发送消息到AI失败")
        }
    }
    
    private fun buildPrompt(type: AIBroadcastType, context: RunningContext): String {
        return "${type.prompt}\n\n${context.toAIContext()}"
    }
    
    /**
     * 处理总结播报完成
     */
    private fun onSummaryBroadcastCompleted() {
        Timber.d("处理总结播报完成")
        
        // 更新总结播报状态为完成
        _summaryBroadcastState.value = _summaryBroadcastState.value.copy(
            broadcastState = AIBroadcastState.COMPLETED,
            isGeneratingSummary = false
        )
        
        isProcessingSummary = false
        
        // 如果需要自动断开连接
        if (_summaryBroadcastState.value.shouldAutoDisconnectAfterSummary) {
            Timber.d("总结播报完成，准备自动断开AI连接")
            
            // 延迟断开，确保音频播放完整
            scope.launch {
                delay(1000) // 1秒缓冲时间
                
                Timber.d("自动断开AI连接")
                disconnect()
                
                // 重置总结播报状态
                _summaryBroadcastState.value = SummaryBroadcastState()
            }
        }
    }
    

    /**
     * 检查是否有录音权限
     */
    private fun hasAudioPermission(): Boolean {
        return try {
            val permission = android.Manifest.permission.RECORD_AUDIO
            val granted = context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Timber.d("录音权限检查结果: $granted")
            granted
        } catch (e: Exception) {
            Timber.e(e, "检查录音权限时出错")
            false
        }
    }
}
