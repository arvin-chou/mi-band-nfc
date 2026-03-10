package com.mibandnfc.ble.proto

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Minimal protobuf wire format encoder/decoder.
 * Wire types: 0 = varint, 2 = length-delimited.
 */
object WireFormat {

    const val WIRE_VARINT = 0
    const val WIRE_LENGTH_DELIMITED = 2

    fun encodeVarint(value: Long): ByteArray {
        val out = ByteArrayOutputStream(10)
        var v = value
        while (v and 0x7F.inv().toLong() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
        return out.toByteArray()
    }

    fun encodeTag(fieldNumber: Int, wireType: Int): ByteArray =
        encodeVarint(((fieldNumber shl 3) or wireType).toLong())

    fun encodeInt32(fieldNumber: Int, value: Int): ByteArray =
        encodeTag(fieldNumber, WIRE_VARINT) + encodeVarint(value.toLong())

    fun encodeBool(fieldNumber: Int, value: Boolean): ByteArray =
        encodeTag(fieldNumber, WIRE_VARINT) + encodeVarint(if (value) 1L else 0L)

    fun encodeBytes(fieldNumber: Int, value: ByteArray): ByteArray =
        encodeTag(fieldNumber, WIRE_LENGTH_DELIMITED) +
                encodeVarint(value.size.toLong()) +
                value

    fun encodeString(fieldNumber: Int, value: String): ByteArray =
        encodeBytes(fieldNumber, value.toByteArray(StandardCharsets.UTF_8))

    fun encodeMessage(fieldNumber: Int, message: ByteArray): ByteArray =
        encodeBytes(fieldNumber, message)

    /**
     * Streaming decoder that reads protobuf fields from a byte array.
     */
    class Decoder(private val data: ByteArray) {
        var pos: Int = 0
            private set

        val isAtEnd: Boolean get() = pos >= data.size

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (pos < data.size) {
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                require(shift < 64) { "Varint too long" }
            }
            return result
        }

        fun readTag(): Pair<Int, Int> {
            if (isAtEnd) return 0 to 0
            val tag = readVarint().toInt()
            return (tag ushr 3) to (tag and 0x07)
        }

        fun readBytes(): ByteArray {
            val len = readVarint().toInt()
            require(pos + len <= data.size) { "Truncated length-delimited field" }
            val result = data.copyOfRange(pos, pos + len)
            pos += len
            return result
        }

        fun readString(): String = String(readBytes(), StandardCharsets.UTF_8)

        fun readBool(): Boolean = readVarint() != 0L

        fun skipField(wireType: Int) {
            when (wireType) {
                WIRE_VARINT -> readVarint()
                WIRE_LENGTH_DELIMITED -> {
                    val len = readVarint().toInt()
                    pos += len
                }
                else -> throw IllegalArgumentException("Unsupported wire type: $wireType")
            }
        }
    }
}
