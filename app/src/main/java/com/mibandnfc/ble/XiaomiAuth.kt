package com.mibandnfc.ble

import android.util.Log
import com.mibandnfc.ble.proto.NfcProto
import com.mibandnfc.ble.proto.ParsedPacket
import com.mibandnfc.ble.proto.WireFormat
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Xiaomi Smart Band 8 BLE authentication over the FE95 channel protocol.
 *
 * Auth uses protobuf packets on characteristic 0051 (same as all other commands).
 * Packet: command_type=1 (AUTH), sub_type varies by phase.
 *
 * Flow (Basic auth / v6 for already-paired device):
 *   1. Client → Band:  Packet(cmd=1, sub=REQUEST_RANDOM)  + apiLevel/platform payload
 *   2. Band → Client:  Packet(cmd=1, sub=RANDOM_RESPONSE) + status + 16-byte random
 *   3. Client → Band:  Packet(cmd=1, sub=AUTHORIZE)       + AES_ECB(authKey, bandRandom)
 *   4. Band → Client:  Packet(cmd=1, sub=AUTH_SUCCESS)     + status
 *
 * Sub-type numbers are configurable since exact values vary between firmware versions
 * and decompiled sources (Gadgetbridge vs Mi Health).
 */
class XiaomiAuth(private val authKey: ByteArray) {

    companion object {
        private const val TAG = "XiaomiAuth"

        // Sub-types from Gadgetbridge XiaomiProto analysis (configurable).
        var SUB_REQUEST_RANDOM = 14
        var SUB_RANDOM_RESPONSE = 15
        var SUB_AUTHORIZE = 16
        var SUB_AUTH_SUCCESS = 17

        // Auth request payload fields
        private const val API_LEVEL = 1
        private const val PLATFORM_ANDROID = 2

        // Response status
        const val STATUS_OK = 1
    }

    enum class Phase { IDLE, AWAITING_RANDOM, AWAITING_RESULT }

    var isAuthenticated: Boolean = false
        private set

    var phase: Phase = Phase.IDLE
        private set

    init {
        require(authKey.size == 16) { "Auth key must be 16 bytes, got ${authKey.size}" }
    }

    /**
     * Build the initial auth request packet (protobuf).
     * Caller must wrap in channel frame and send to 0051.
     */
    fun buildAuthRequest(): ByteArray {
        phase = Phase.AWAITING_RANDOM
        isAuthenticated = false

        val authPayload = buildRequestRandomPayload()
        val packet = NfcProto.buildAuthPacket(SUB_REQUEST_RANDOM, authPayload)

        Log.d(TAG, "Auth request: sub=$SUB_REQUEST_RANDOM, " +
            "payload=${authPayload.toHex()}, packet=${packet.toHex()}")
        return packet
    }

    /**
     * Handle a parsed auth response from the band.
     *
     * @return the next protobuf packet to send, or null if auth is complete/failed.
     * @throws IllegalStateException on auth rejection.
     */
    fun handleAuthResponse(parsed: ParsedPacket): ByteArray? {
        Log.d(TAG, "Auth response: cmd=${parsed.commandType}, sub=${parsed.subType}, " +
            "phase=$phase, fields=${parsed.payloads.keys}")

        if (parsed.commandType != NfcProto.COMMAND_TYPE_AUTH) {
            Log.w(TAG, "Not an auth response (cmd=${parsed.commandType}), ignoring")
            return null
        }

        return when (parsed.subType) {
            SUB_RANDOM_RESPONSE -> onRandomResponse(parsed)
            SUB_AUTH_SUCCESS -> onAuthSuccess(parsed)
            else -> {
                Log.w(TAG, "Unexpected auth sub=${parsed.subType} in phase=$phase, " +
                    "payloads=${parsed.payloads.mapValues { "${it.value.size}B" }}")
                checkForRejection(parsed)
                null
            }
        }
    }

    fun reset() {
        phase = Phase.IDLE
        isAuthenticated = false
    }

    // --- Phase handlers ---

    private fun onRandomResponse(parsed: ParsedPacket): ByteArray {
        if (phase != Phase.AWAITING_RANDOM) {
            Log.w(TAG, "Random response arrived in phase=$phase (expected AWAITING_RANDOM)")
        }

        val authPayload = parsed.authPayload
        if (authPayload == null) {
            logAllPayloads(parsed, "random response has no auth payload (field ${NfcProto.AUTH_PAYLOAD_FIELD})")
            throw IllegalStateException("No auth payload in random response")
        }

        Log.d(TAG, "Random response payload (${authPayload.size}B): ${authPayload.toHex()}")
        val bandRandom = extractBandRandom(authPayload)
        Log.d(TAG, "Band random: ${bandRandom.toHex()}")

        val cipher = aesEcbEncrypt(authKey, bandRandom)
        Log.d(TAG, "AES proof: ${cipher.toHex()}")

        val proofPayload = buildAuthorizePayload(cipher)
        val packet = NfcProto.buildAuthPacket(SUB_AUTHORIZE, proofPayload)

        phase = Phase.AWAITING_RESULT
        Log.d(TAG, "Authorize packet: sub=$SUB_AUTHORIZE, ${packet.size}B")
        return packet
    }

    private fun onAuthSuccess(parsed: ParsedPacket): ByteArray? {
        val payload = parsed.authPayload
        if (payload != null) {
            val status = tryReadStatus(payload)
            Log.d(TAG, "Auth success payload: status=$status, ${payload.toHex()}")
            if (status != null && status != STATUS_OK) {
                phase = Phase.IDLE
                throw IllegalStateException("Auth succeeded with bad status=$status")
            }
        }

        isAuthenticated = true
        phase = Phase.IDLE
        Log.d(TAG, "Authentication successful!")
        return null
    }

    private fun checkForRejection(parsed: ParsedPacket) {
        val payload = parsed.authPayload ?: return
        val status = tryReadStatus(payload)
        if (status != null && status != STATUS_OK) {
            phase = Phase.IDLE
            throw IllegalStateException("Auth rejected: sub=${parsed.subType}, status=$status")
        }
    }

    // --- Payload encoding ---

    private fun buildRequestRandomPayload(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(WireFormat.encodeInt32(1, API_LEVEL))
        out.write(WireFormat.encodeInt32(2, PLATFORM_ANDROID))
        return out.toByteArray()
    }

    private fun buildAuthorizePayload(cipher: ByteArray): ByteArray =
        WireFormat.encodeBytes(1, cipher)

    // --- Payload decoding ---

    /**
     * Extract the 16-byte band random from the auth response payload.
     * Tries protobuf decode first (field 2=status, field 3=random),
     * then falls back to raw binary heuristics.
     */
    private fun extractBandRandom(authPayload: ByteArray): ByteArray {
        try {
            val dec = WireFormat.Decoder(authPayload)
            var status = -1
            var random: ByteArray? = null

            while (!dec.isAtEnd) {
                val (field, wireType) = dec.readTag()
                if (field == 0) break
                when (field) {
                    2 -> status = dec.readVarint().toInt()
                    3 -> random = dec.readBytes()
                    else -> {
                        Log.d(TAG, "extractBandRandom: skip field=$field wt=$wireType")
                        dec.skipField(wireType)
                    }
                }
            }

            Log.d(TAG, "Protobuf random: status=$status, randomLen=${random?.size}")

            if (status != STATUS_OK && status != -1) {
                throw IllegalStateException("Band rejected random request: status=$status")
            }
            if (random != null && random.size >= 16) {
                return random.copyOf(16)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Protobuf decode failed, trying raw: ${e.message}")
        }

        // Fallback: raw binary [prefix][status][random:16]
        if (authPayload.size >= 18) {
            val prefix = authPayload[0].toInt() and 0xFF
            val rawStatus = authPayload[1].toInt() and 0xFF
            Log.d(TAG, "Raw fallback: prefix=0x${prefix.toString(16)}, status=$rawStatus")
            if (rawStatus == STATUS_OK) {
                return authPayload.copyOfRange(2, 18)
            }
        }

        // Last resort: treat entire 16-byte payload as random
        if (authPayload.size == 16) {
            Log.w(TAG, "Using entire ${authPayload.size}-byte payload as random")
            return authPayload
        }

        throw IllegalStateException(
            "Cannot extract random from ${authPayload.size}B: ${authPayload.toHex()}"
        )
    }

    private fun tryReadStatus(payload: ByteArray): Int? {
        try {
            val dec = WireFormat.Decoder(payload)
            while (!dec.isAtEnd) {
                val (field, wireType) = dec.readTag()
                if (field == 0) break
                if (field == 2 && wireType == WireFormat.WIRE_VARINT) {
                    return dec.readVarint().toInt()
                }
                dec.skipField(wireType)
            }
        } catch (_: Exception) { }

        if (payload.size >= 2) {
            return payload[1].toInt() and 0xFF
        }
        return null
    }

    // --- Crypto ---

    private fun aesEcbEncrypt(key: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(input)
    }

    // --- Debug helpers ---

    private fun logAllPayloads(parsed: ParsedPacket, context: String) {
        Log.e(TAG, context)
        for ((field, data) in parsed.payloads) {
            Log.d(TAG, "  field $field: ${data.size}B = ${data.toHex()}")
        }
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
