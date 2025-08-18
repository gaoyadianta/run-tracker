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
import com.ss.bytertc.engine.RTCRoom
import com.ss.bytertc.engine.RTCVideo
import com.ss.bytertc.engine.UserInfo
import com.ss.bytertc.engine.data.StreamIndex
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler
import com.ss.bytertc.engine.type.ChannelProfile
import com.ss.bytertc.engine.type.MessageConfig
import com.ss.bytertc.engine.type.RTCRoomConfig
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

@Singleton
class AIRunningCompanionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cozeConfig: CozeConfig,
    private val cozeAPIManager: CozeAPIManager
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
        
        cozeAPI = cozeAPIManager.getCozeAPI()
        Timber.d("AI陪跑管理器初始化完成")
    }
    
    /**
     * 连接AI陪跑服务
     */
    fun connect() {
        if (_connectionState.value == AIConnectionState.CONNECTING || 
            _connectionState.value == AIConnectionState.CONNECTED) {
            return
        }
        
        _connectionState.value = AIConnectionState.CONNECTING
        
        scope.launch(Dispatchers.IO) {
            try {
                // 创建Coze房间
                val req = CreateRoomReq.builder()
                    .botID(cozeConfig.botID)
                    .voiceID(cozeConfig.voiceID)
                    .build()
                
                roomInfo = cozeAPI?.audio()?.rooms()?.create(req)
                
                scope.launch(Dispatchers.Main) {
                    setupRTCEngine()
                }
            } catch (e: Exception) {
                Timber.e(e, "连接AI陪跑服务失败")
                _connectionState.value = AIConnectionState.ERROR
            }
        }
    }
    
    /**
     * 断开AI陪跑服务
     */
    fun disconnect() {
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
            roomInfo = null
            
            _connectionState.value = AIConnectionState.DISCONNECTED
            Timber.d("AI陪跑服务已断开")
        } catch (e: Exception) {
            Timber.e(e, "断开AI陪跑服务时出错")
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
    
    private fun setupRTCEngine() {
        try {
            // 创建RTC引擎
            rtcVideo = RTCVideo.createRTCVideo(
                context,
                roomInfo?.appID,
                rtcVideoEventHandler,
                null,
                null
            )
            
            // 开启音频采集
            rtcVideo?.startAudioCapture()
            
            // 创建房间
            rtcRoom = rtcVideo?.createRTCRoom(roomInfo?.roomID)?.apply {
                setRTCRoomEventHandler(rtcRoomEventHandler)
                
                val userInfo = UserInfo(roomInfo?.uid, "")
                val roomConfig = RTCRoomConfig(
                    ChannelProfile.CHANNEL_PROFILE_CHAT_ROOM,
                    true, true, true
                )
                
                joinRoom(roomInfo?.token, userInfo, roomConfig)
            }
            
            _connectionState.value = AIConnectionState.CONNECTED
            Timber.d("RTC引擎设置完成，已连接到AI陪跑服务")
            
        } catch (e: Exception) {
            Timber.e(e, "设置RTC引擎失败")
            _connectionState.value = AIConnectionState.ERROR
        }
    }
    
    private val rtcVideoEventHandler = object : IRTCVideoEventHandler() {
        override fun onWarning(warn: Int) {
            Timber.w("RTC警告: $warn")
        }
        
        override fun onError(err: Int) {
            Timber.e("RTC错误: $err")
            _connectionState.value = AIConnectionState.ERROR
        }
    }
    
    private val rtcRoomEventHandler = object : IRTCRoomEventHandler() {
        override fun onRoomStateChanged(roomId: String, uid: String, state: Int, extraInfo: String) {
            Timber.d("房间状态变化: roomId=$roomId, uid=$uid, state=$state")
        }
        
        override fun onUserMessageReceived(uid: String, message: String) {
            try {
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
                            val msg = mapper.readValue(it, com.coze.openapi.client.connversations.message.model.Message::class.java)
                            updateMessage(msg.content)
                        }
                    }
                    ChatEventType.CONVERSATION_MESSAGE_COMPLETED.value -> {
                        // 消息完成，可以进行后续处理
                        Timber.d("AI消息播报完成")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "解析AI消息失败")
            }
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
}