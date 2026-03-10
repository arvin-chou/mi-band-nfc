package com.mibandnfc.ble

import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * BLE chunked transfer for the Xiaomi FE95 channel protocol.
 *
 * Frame format (verified against real Smart Band 8 with MTU 247):
 *
 *   First chunk:        0x00 | handle:2LE | totalLen:2LE | data...
 *   Continuation chunk: seq:1 | handle:2LE | data...
 *
 * The "data" being chunked is a channel message:
 *   encrypted_flag:1 | protobuf_payload...
 *
 * totalLen covers encrypted_flag + protobuf (everything after the 5-byte chunk header).
 * Handle is 0x0000 for the main command channel (char 0051).
 */
object ChunkedTransfer {

    private const val TAG = "ChunkedTransfer"
    private const val HEADER_SIZE = 3      // [flags/seq:1][handle:2LE]
    private const val FIRST_CHUNK_EXTRA = 2 // [totalLen:2LE]

    // --- Channel message helpers ---

    /**
     * Wrap a protobuf payload in a channel message by prepending the encrypted flag.
     * This is the "data" that gets chunked.
     */
    fun buildChannelPayload(encrypted: Boolean, protobuf: ByteArray): ByteArray =
        byteArrayOf(if (encrypted) 0x01 else 0x00) + protobuf

    /**
     * Parse a reassembled channel message into (encrypted, protobuf).
     */
    fun parseChannelPayload(data: ByteArray): Pair<Boolean, ByteArray> {
        require(data.isNotEmpty()) { "Empty channel payload" }
        val encrypted = data[0].toInt() != 0
        val protobuf = if (data.size > 1) data.copyOfRange(1, data.size) else byteArrayOf()
        return encrypted to protobuf
    }

    // --- Chunking ---

    /**
     * Split a message into BLE-sized chunks.
     *
     * @param message Complete message bytes (typically: encrypted_flag + protobuf).
     * @param handle BLE attribute handle (default 0 for main channel).
     * @param mtu Negotiated MTU (default 244).
     * @return Ordered list of chunks ready to write to the characteristic.
     */
    fun split(message: ByteArray, handle: Int = 0, mtu: Int = 244): List<ByteArray> {
        val maxFirstPayload = mtu - HEADER_SIZE - FIRST_CHUNK_EXTRA
        val maxContPayload = mtu - HEADER_SIZE

        if (message.size <= maxFirstPayload) {
            return listOf(buildFirstChunk(handle, message.size, message))
        }

        val chunks = mutableListOf<ByteArray>()
        val firstData = message.copyOfRange(0, maxFirstPayload)
        chunks.add(buildFirstChunk(handle, message.size, firstData))

        var offset = maxFirstPayload
        var seq = 1
        while (offset < message.size) {
            val end = minOf(offset + maxContPayload, message.size)
            val payload = message.copyOfRange(offset, end)
            chunks.add(buildContinuationChunk(handle, seq, payload))
            offset = end
            seq++
        }

        Log.d(TAG, "Split ${message.size}B into ${chunks.size} chunks (mtu=$mtu, handle=$handle)")
        return chunks
    }

    private fun buildFirstChunk(handle: Int, totalLen: Int, data: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + FIRST_CHUNK_EXTRA + data.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x00.toByte())
        buf.putShort(handle.toShort())
        buf.putShort(totalLen.toShort())
        buf.put(data)
        return buf.array()
    }

    private fun buildContinuationChunk(handle: Int, seq: Int, data: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + data.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.put(seq.toByte())
        buf.putShort(handle.toShort())
        buf.put(data)
        return buf.array()
    }

    // --- Reassembly ---

    /**
     * Stateful reassembler for incoming chunked notifications.
     * Feed each notification payload; returns the complete message when all chunks arrive.
     */
    class Reassembler {
        private var totalLength = -1
        private var handle = 0
        private var expectedSeq = 1
        private val buffer = ByteArrayOutputStream()

        fun feed(chunk: ByteArray): ByteArray? {
            if (chunk.size < HEADER_SIZE) {
                Log.w(TAG, "Chunk too small: ${chunk.size}B")
                return null
            }

            val buf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            val flags = buf.get().toInt() and 0xFF
            val chunkHandle = buf.short.toInt() and 0xFFFF

            return if (flags == 0) {
                handleFirstChunk(buf, chunkHandle)
            } else {
                handleContinuationChunk(buf, flags, chunkHandle)
            }
        }

        private fun handleFirstChunk(buf: ByteBuffer, chunkHandle: Int): ByteArray? {
            if (buf.remaining() < FIRST_CHUNK_EXTRA) {
                Log.w(TAG, "First chunk missing length field")
                return null
            }
            buffer.reset()
            totalLength = buf.short.toInt() and 0xFFFF
            handle = chunkHandle
            expectedSeq = 1

            val data = ByteArray(buf.remaining())
            buf.get(data)
            buffer.write(data)

            Log.d(TAG, "First chunk: total=$totalLength, got=${data.size}, handle=$handle")
            return checkComplete()
        }

        private fun handleContinuationChunk(buf: ByteBuffer, seq: Int, chunkHandle: Int): ByteArray? {
            if (chunkHandle != handle) {
                Log.w(TAG, "Handle mismatch: expected=$handle, got=$chunkHandle")
            }
            if (seq != expectedSeq) {
                Log.w(TAG, "Seq mismatch: expected=$expectedSeq, got=$seq")
            }
            expectedSeq = seq + 1

            val data = ByteArray(buf.remaining())
            buf.get(data)
            buffer.write(data)

            Log.d(TAG, "Continuation seq=$seq, buffered=${buffer.size()}/$totalLength")
            return checkComplete()
        }

        private fun checkComplete(): ByteArray? {
            if (totalLength < 0) return null
            if (buffer.size() >= totalLength) {
                val result = buffer.toByteArray().copyOf(totalLength)
                Log.d(TAG, "Reassembly complete: $totalLength bytes")
                reset()
                return result
            }
            return null
        }

        fun reset() {
            totalLength = -1
            handle = 0
            expectedSeq = 1
            buffer.reset()
        }
    }
}
