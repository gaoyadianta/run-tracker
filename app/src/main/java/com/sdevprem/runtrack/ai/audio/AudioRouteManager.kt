package com.sdevprem.runtrack.ai.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频路由管理器
 * 负责管理音频输入输出设备的选择，优先使用蓝牙耳机等外接设备
 */
@Singleton
class AudioRouteManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioRouteManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothHeadset: BluetoothHeadset? = null
    
    // 当前音频设备状态
    private val _currentAudioDevice = MutableStateFlow(AudioDeviceType.SPEAKER)
    val currentAudioDevice: StateFlow<AudioDeviceType> = _currentAudioDevice.asStateFlow()
    
    // 可用音频设备列表
    private val _availableDevices = MutableStateFlow<List<AudioDeviceType>>(emptyList())
    val availableDevices: StateFlow<List<AudioDeviceType>> = _availableDevices.asStateFlow()
    
    private var isInitialized = false
    private var originalAudioMode = AudioManager.MODE_NORMAL
    private var originalSpeakerphoneState = false

    /**
     * 音频设备类型
     */
    enum class AudioDeviceType(val displayName: String, val priority: Int) {
        BLUETOOTH_HEADSET("蓝牙耳机", 1),
        WIRED_HEADSET("有线耳机", 2),
        BLUETOOTH_A2DP("蓝牙音箱", 3),
        EARPIECE("听筒", 4),
        SPEAKER("扬声器", 5);
        
        companion object {
            fun getByPriority(): List<AudioDeviceType> {
                return values().sortedBy { it.priority }
            }
        }
    }

    /**
     * 蓝牙状态监听器
     */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED)
                    Timber.d("蓝牙耳机连接状态变化: $state")
                    updateAvailableDevices()
                    selectOptimalAudioDevice()
                }
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED)
                    Timber.d("蓝牙耳机音频状态变化: $state")
                    if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                        _currentAudioDevice.value = AudioDeviceType.BLUETOOTH_HEADSET
                    }
                }
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    Timber.d("有线耳机插拔状态: $state")
                    updateAvailableDevices()
                    selectOptimalAudioDevice()
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
                    Timber.d("SCO音频状态更新: $state")
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            _currentAudioDevice.value = AudioDeviceType.BLUETOOTH_HEADSET
                        }
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                            selectOptimalAudioDevice()
                        }
                    }
                }
            }
        }
    }

    /**
     * 蓝牙配置文件监听器
     */
    private val bluetoothProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                Timber.d("蓝牙耳机服务连接成功")
                updateAvailableDevices()
                selectOptimalAudioDevice()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
                Timber.d("蓝牙耳机服务断开连接")
                updateAvailableDevices()
                selectOptimalAudioDevice()
            }
        }
    }

    /**
     * 初始化音频路由管理器
     */
    fun initialize() {
        if (isInitialized) {
            Timber.d("音频路由管理器已初始化")
            return
        }

        try {
            Timber.d("开始初始化音频路由管理器")
            
            // 保存原始音频设置
            originalAudioMode = audioManager.mode
            originalSpeakerphoneState = audioManager.isSpeakerphoneOn
            
            // 初始化蓝牙适配器
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            
            // 注册蓝牙状态监听器
            registerBluetoothReceivers()
            
            // 连接蓝牙耳机服务
            bluetoothAdapter?.getProfileProxy(context, bluetoothProfileListener, BluetoothProfile.HEADSET)
            
            // 更新可用设备列表
            updateAvailableDevices()
            
            // 选择最优音频设备
            selectOptimalAudioDevice()
            
            isInitialized = true
            Timber.d("音频路由管理器初始化完成")
            
        } catch (e: Exception) {
            Timber.e(e, "初始化音频路由管理器失败")
        }
    }

    /**
     * 为AI通话配置音频路由
     */
    fun setupForAICall() {
        try {
            Timber.d("为AI通话配置音频路由")
            
            // 设置音频模式为通信模式
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            
            // 选择最优音频设备
            selectOptimalAudioDevice()
            
        } catch (e: Exception) {
            Timber.e(e, "配置AI通话音频路由失败")
        }
    }

    /**
     * 选择最优音频设备
     */
    private fun selectOptimalAudioDevice() {
        try {
            val availableDevices = getAvailableAudioDevices()
            val optimalDevice = availableDevices.minByOrNull { it.priority }
            
            if (optimalDevice != null) {
                setAudioDevice(optimalDevice)
            } else {
                // 如果没有可用设备，使用扬声器作为默认
                setAudioDevice(AudioDeviceType.SPEAKER)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "选择最优音频设备失败")
        }
    }

    /**
     * 设置音频设备
     */
    private fun setAudioDevice(deviceType: AudioDeviceType) {
        try {
            Timber.d("设置音频设备: ${deviceType.displayName}")
            
            when (deviceType) {
                AudioDeviceType.BLUETOOTH_HEADSET -> {
                    // 启用蓝牙SCO
                    audioManager.isBluetoothScoOn = true
                    audioManager.startBluetoothSco()
                    audioManager.isSpeakerphoneOn = false
                }
                AudioDeviceType.WIRED_HEADSET -> {
                    // 有线耳机会自动路由，关闭扬声器和蓝牙
                    audioManager.isSpeakerphoneOn = false
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                }
                AudioDeviceType.EARPIECE -> {
                    // 使用听筒
                    audioManager.isSpeakerphoneOn = false
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                }
                AudioDeviceType.SPEAKER -> {
                    // 使用扬声器
                    audioManager.isSpeakerphoneOn = true
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                }
                AudioDeviceType.BLUETOOTH_A2DP -> {
                    // A2DP设备处理
                    audioManager.isSpeakerphoneOn = false
                    audioManager.isBluetoothScoOn = false
                }
            }
            
            _currentAudioDevice.value = deviceType
            Timber.d("音频设备设置完成: ${deviceType.displayName}")
            
        } catch (e: Exception) {
            Timber.e(e, "设置音频设备失败: ${deviceType.displayName}")
        }
    }

    /**
     * 获取可用音频设备列表
     */
    private fun getAvailableAudioDevices(): List<AudioDeviceType> {
        val devices = mutableListOf<AudioDeviceType>()
        
        try {
            // 检查蓝牙耳机
            if (isBluetoothHeadsetConnected()) {
                devices.add(AudioDeviceType.BLUETOOTH_HEADSET)
            }
            
            // 检查有线耳机
            if (isWiredHeadsetConnected()) {
                devices.add(AudioDeviceType.WIRED_HEADSET)
            }
            
            // 检查蓝牙A2DP设备
            if (isBluetoothA2dpConnected()) {
                devices.add(AudioDeviceType.BLUETOOTH_A2DP)
            }
            
            // 听筒总是可用（在手机上）
            devices.add(AudioDeviceType.EARPIECE)
            
            // 扬声器总是可用
            devices.add(AudioDeviceType.SPEAKER)
            
        } catch (e: Exception) {
            Timber.e(e, "获取可用音频设备失败")
            // 至少返回扬声器作为备选
            devices.add(AudioDeviceType.SPEAKER)
        }
        
        return devices
    }

    /**
     * 检查蓝牙耳机是否连接
     */
    private fun isBluetoothHeadsetConnected(): Boolean {
        return try {
            bluetoothHeadset?.connectedDevices?.isNotEmpty() == true
        } catch (e: Exception) {
            Timber.w(e, "检查蓝牙耳机连接状态失败")
            false
        }
    }

    /**
     * 检查有线耳机是否连接
     */
    private fun isWiredHeadsetConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                isWiredHeadsetConnectedApi23()
            } else {
                // 对于较低版本，使用AudioManager的方法
                audioManager.isWiredHeadsetOn
            }
        } catch (e: Exception) {
            Timber.w(e, "检查有线耳机连接状态失败")
            false
        }
    }

    /**
     * API 23+检查有线耳机连接状态
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun isWiredHeadsetConnectedApi23(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    /**
     * 检查蓝牙A2DP设备是否连接
     */
    private fun isBluetoothA2dpConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
            } else {
                audioManager.isBluetoothA2dpOn
            }
        } catch (e: Exception) {
            Timber.w(e, "检查蓝牙A2DP连接状态失败")
            false
        }
    }

    /**
     * 更新可用设备列表
     */
    private fun updateAvailableDevices() {
        val devices = getAvailableAudioDevices()
        _availableDevices.value = devices
        Timber.d("可用音频设备更新: ${devices.map { it.displayName }}")
    }

    /**
     * 注册蓝牙状态监听器
     */
    private fun registerBluetoothReceivers() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            }
            context.registerReceiver(bluetoothReceiver, filter)
            Timber.d("蓝牙状态监听器注册成功")
        } catch (e: Exception) {
            Timber.e(e, "注册蓝牙状态监听器失败")
        }
    }

    /**
     * 手动切换到指定音频设备
     */
    fun switchToDevice(deviceType: AudioDeviceType) {
        if (_availableDevices.value.contains(deviceType)) {
            setAudioDevice(deviceType)
        } else {
            Timber.w("尝试切换到不可用的音频设备: ${deviceType.displayName}")
        }
    }

    /**
     * 恢复原始音频设置
     */
    fun restoreOriginalSettings() {
        try {
            Timber.d("恢复原始音频设置")
            
            // 停止蓝牙SCO
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            
            // 恢复原始设置
            audioManager.mode = originalAudioMode
            audioManager.isSpeakerphoneOn = originalSpeakerphoneState
            
            Timber.d("原始音频设置恢复完成")
            
        } catch (e: Exception) {
            Timber.e(e, "恢复原始音频设置失败")
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            Timber.d("清理音频路由管理器资源")
            
            // 恢复原始设置
            restoreOriginalSettings()
            
            // 注销监听器
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                Timber.w(e, "注销蓝牙监听器失败")
            }
            
            // 断开蓝牙服务
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
            bluetoothHeadset = null
            
            isInitialized = false
            Timber.d("音频路由管理器资源清理完成")
            
        } catch (e: Exception) {
            Timber.e(e, "清理音频路由管理器资源失败")
        }
    }
}