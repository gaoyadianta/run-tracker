package com.sdevprem.runtrack.ai.volcano.v3

import java.nio.ByteBuffer
import java.nio.ByteOrder

object VolcanoBinaryProtocol {
    const val VERSION = 0b0001
    const val HEADER_SIZE = 0b0001

    const val MESSAGE_TYPE_FULL_REQUEST = 0b0001
    const val MESSAGE_TYPE_AUDIO_ONLY_REQUEST = 0b0010
    const val MESSAGE_TYPE_FULL_RESPONSE = 0b1001
    const val MESSAGE_TYPE_AUDIO_ONLY_RESPONSE = 0b1011
    const val MESSAGE_TYPE_ERROR = 0b1111

    const val FLAG_NONE = 0b0000
    const val FLAG_WITH_EVENT = 0b0100
    const val FLAG_FINAL = 0b0010

    const val SERIALIZATION_RAW = 0b0000
    const val SERIALIZATION_JSON = 0b0001
    const val COMPRESSION_NONE = 0b0000

    data class Header(
        val version: Int,
        val headerSize: Int,
        val messageType: Int,
        val flags: Int,
        val serialization: Int,
        val compression: Int
    )

    fun buildHeader(
        messageType: Int,
        flags: Int,
        serialization: Int,
        compression: Int
    ): ByteArray {
        val byte0 = ((VERSION and 0x0F) shl 4) or (HEADER_SIZE and 0x0F)
        val byte1 = ((messageType and 0x0F) shl 4) or (flags and 0x0F)
        val byte2 = ((serialization and 0x0F) shl 4) or (compression and 0x0F)
        return byteArrayOf(byte0.toByte(), byte1.toByte(), byte2.toByte(), 0x00)
    }

    fun parseHeader(bytes: ByteArray): Header {
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        return Header(
            version = (b0 shr 4) and 0x0F,
            headerSize = b0 and 0x0F,
            messageType = (b1 shr 4) and 0x0F,
            flags = b1 and 0x0F,
            serialization = (b2 shr 4) and 0x0F,
            compression = b2 and 0x0F
        )
    }

    fun int32(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
    }

    fun uint32(value: Long): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.toInt()).array()
    }

    fun readInt32(buffer: ByteBuffer): Int {
        return buffer.int
    }

    fun readUint32(buffer: ByteBuffer): Long {
        return buffer.int.toLong() and 0xFFFFFFFFL
    }
}
