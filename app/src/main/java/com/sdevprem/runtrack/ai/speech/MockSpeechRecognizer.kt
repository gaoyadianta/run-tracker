package com.sdevprem.runtrack.ai.speech

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模拟语音识别器，用于测试AI陪跑功能
 * 在实际项目中，可以替换为真实的语音识别实现
 */
@Singleton
class MockSpeechRecognizer @Inject constructor() {
    
    private val mockPhrases = listOf(
        "我有点累了",
        "现在感觉很好",
        "配速怎么样",
        "还要跑多久",
        "我想加速",
        "需要休息一下",
        "今天天气不错",
        "我的表现如何",
        "给我一些鼓励",
        "我快不行了"
    )
    
    /**
     * 模拟语音识别过程
     * @param durationMs 识别持续时间
     * @return 识别结果文本
     */
    suspend fun recognizeSpeech(durationMs: Long = 3000L): String {
        // 模拟识别延迟
        delay(durationMs)
        
        // 随机返回一个模拟短语
        return mockPhrases.random()
    }
    
    /**
     * 检查是否支持语音识别
     */
    fun isAvailable(): Boolean {
        return true // 模拟器总是可用
    }
}