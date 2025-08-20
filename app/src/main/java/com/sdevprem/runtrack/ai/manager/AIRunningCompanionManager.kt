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
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mapper = ObjectMapper()
    
    private var rtcVideo: RTCVideo? = null
    private var rtcRoom: RTCRoom? = null
    private var roomInfo: CreateRoomResp? = null
    private var cozeAPI: CozeAPI? = null
    
    // 状态管理
    private val _connectionState = MutableStateFlow(AIConnectionState.DISCONNECTED)
    val connectionState: StateFlow<AIConnectionState> = _connectionState.asStateFlow()
    
    private val _lastMessage = MutableStateFlow("")
    val lastMessage: StateFlow<String> = _lastMessage.asStateFlow()
    
    private val _isAudioEnabled = MutableStateFlow(true)
    val isAudioEnabled: StateFlow<Boolean> = _isAudioEnabled.asStateFlow()
    
    // 播报控制
    private var lastBroadcastTime = 0L
    private var broadcastInterval = 120000L // 2分钟间隔
    
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
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBroadcastTime < broadcastInterval) {
            return // 播报间隔未到
        }
        
        val type = broadcastType ?: AIBroadcastType.getByContext(runningContext)
        val prompt = buildPrompt(type, runningContext)
        
        sendMessageToAI(prompt)
        lastBroadcastTime = currentTime
    }
    
    /**
     * 发送用户语音输入到AI
     */
    fun sendUserMessage(message: String, runningContext: RunningContext) {
        if (_connectionState.value != AIConnectionState.CONNECTED) {
            return
        }
        
        val prompt = buildInteractivePrompt(message, runningContext)
        sendMessageToAI(prompt)
    }
    
    /**
     * 设置播报间隔
     */
    fun setBroadcastInterval(intervalMinutes: Int) {
        broadcastInterval = intervalMinutes * 60000L
    }
    
    /**
     * 切换音频开关
     */
    fun toggleAudio() {
        val enabled = !_isAudioEnabled.value
        _isAudioEnabled.value = enabled
        
        if (enabled) {
            rtcVideo?.startAudioCapture()
        } else {
            rtcVideo?.stopAudioCapture()
        }
    }
    
    /**
     * 获取当前音频设备信息
     */
    fun getCurrentAudioDevice() = audioRouteManager.currentAudioDevice
    
    /**
     * 获取可用音频设备列表
     */
    fun getAvailableAudioDevices() = audioRouteManager.availableDevices
    
    /**
     * 手动切换音频设备
     */
    fun switchAudioDevice(deviceType: AudioRouteManager.AudioDeviceType) {
        audioRouteManager.switchToDevice(deviceType)
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
                audioRouteManager.setupForAICall()
                Timber.d("音频路由配置完成")
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
            
            // 开启音频采集
            try {
                rtcVideo?.startAudioCapture()
                Timber.d("音频采集启动成功")
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
            
        } catch (e: Exception) {
            Timber.e(e, "设置RTC引擎失败")
            _connectionState.value = AIConnectionState.ERROR
            
            // 清理资源
            cleanupRTCResources()
        }
    }
    
    /**
     * 清理RTC资源
     */
    private fun cleanupRTCResources() {
        try {
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
                Timber.d("收到用户消息: uid=$uid, message长度=${message.length}")
                
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
                
                when (jsonMap["event_type"]) {
                    ChatEventType.CONVERSATION_MESSAGE_DELTA.value -> {
                        val data = jsonMap["data"] as? String
                        data?.let { 
                            try {
                                // 简化消息解析，直接使用data内容
                                // 如果data是JSON字符串，尝试解析；否则直接使用
                                val messageContent = try {
                                    val dataMap = mapper.readValue<Map<String, Any>>(data, object : TypeReference<Map<String, Any>>() {})
                                    dataMap["content"] as? String ?: data
                                } catch (e: Exception) {
                                    // 如果解析失败，直接使用原始数据
                                    data
                                }
                                updateMessage(messageContent)
                            } catch (e: Exception) {
                                Timber.e(e, "解析消息内容失败")
                            }
                        }
                    }
                    ChatEventType.CONVERSATION_MESSAGE_COMPLETED.value -> {
                        Timber.d("AI消息播报完成")
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
                        if (_connectionState.value == AIConnectionState.ERROR) {
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
        }
    }
    
    private fun sendMessageToAI(prompt: String) {
        try {
            val data = mapOf(
                "id" to "broadcast_${System.currentTimeMillis()}",
                "event_type" to "conversation.chat.created",
                "data" to mapper.writeValueAsString(mapOf("message" to prompt))
            )
            
            rtcRoom?.sendUserMessage(
                roomInfo?.uid,
                mapper.writeValueAsString(data),
                MessageConfig.RELIABLE_ORDERED
            )
            
            // 清空上一条消息
            _lastMessage.value = ""
            
        } catch (e: Exception) {
            Timber.e(e, "发送消息到AI失败")
        }
    }
    
    private fun buildPrompt(type: AIBroadcastType, context: RunningContext): String {
        return "${type.prompt}\n\n${context.toAIContext()}"
    }
    
    private fun buildInteractivePrompt(userMessage: String, context: RunningContext): String {
        return "${AIBroadcastType.INTERACTIVE_RESPONSE.prompt}\n\n" +
                "用户说：\"$userMessage\"\n\n" +
                "${context.toAIContext()}"
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