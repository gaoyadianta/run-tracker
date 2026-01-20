package com.sdevprem.runtrack.ai.audio

import kotlin.math.sqrt

class SimpleVad(
    private val energyThreshold: Double = 200.0
) {
    fun isSpeech(buffer: ByteArray, bytesRead: Int): Boolean {
        if (bytesRead <= 1) return false
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < bytesRead) {
            val sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
            val signed = if (sample and 0x8000 != 0) sample or -0x10000 else sample
            sum += signed.toDouble() * signed.toDouble()
            count++
            i += 2
        }
        if (count == 0) return false
        val rms = sqrt(sum / count)
        return rms >= energyThreshold
    }
}
