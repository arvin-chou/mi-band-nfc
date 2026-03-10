package com.mibandnfc.ble.proto

import com.mibandnfc.model.CardType
import com.mibandnfc.model.NfcCard
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Parsed protobuf packet from the Xiaomi channel protocol.
 *
 * All length-delimited fields are stored by field number in [payloads].
 * Known payload accessors: [nfcPayload] (field 7), [authPayload] (field 3).
 */
data class ParsedPacket(
    val commandType: Int,
    val subType: Int,
    val payloads: Map<Int, ByteArray> = emptyMap(),
) {
    val nfcPayload: ByteArray? get() = payloads[NfcProto.NFC_PAYLOAD_FIELD]
    val authPayload: ByteArray? get() = payloads[NfcProto.AUTH_PAYLOAD_FIELD]

    fun getPayload(field: Int): ByteArray? = payloads[field]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedPacket) return false
        if (commandType != other.commandType || subType != other.subType) return false
        if (payloads.size != other.payloads.size) return false
        return payloads.all { (k, v) -> other.payloads[k]?.contentEquals(v) == true }
    }

    override fun hashCode(): Int {
        var result = commandType
        result = 31 * result + subType
        payloads.forEach { (k, v) -> result = 31 * result + k + v.contentHashCode() }
        return result
    }

    override fun toString(): String =
        "ParsedPacket(cmd=$commandType, sub=$subType, fields=${payloads.keys})"
}

object NfcProto {

    const val COMMAND_TYPE_AUTH = 1
    const val COMMAND_TYPE_NFC = 5

    const val AUTH_PAYLOAD_FIELD = 3
    const val NFC_PAYLOAD_FIELD = 7

    // AidInfo operations
    const val AID_OP_ADD = 1
    const val AID_OP_DELETE = 2

    // CardConfig operations
    const val CONFIG_OP_ACTIVATE = 1
    const val CONFIG_OP_INACTIVATE = 2
    const val CONFIG_OP_READ = 3

    // --- Packet builders ---

    /**
     * Build a top-level protobuf packet.
     *
     * Field 1 = commandType (varint), field 2 = subType (varint),
     * [payloadField] = payload (length-delimited, default 7 for NFC).
     */
    fun buildPacket(
        commandType: Int,
        subType: Int,
        payload: ByteArray? = null,
        payloadField: Int = NFC_PAYLOAD_FIELD,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(WireFormat.encodeInt32(1, commandType))
        out.write(WireFormat.encodeInt32(2, subType))
        if (payload != null) {
            out.write(WireFormat.encodeMessage(payloadField, payload))
        }
        return out.toByteArray()
    }

    /**
     * Convenience: build an auth packet (command_type=1).
     */
    fun buildAuthPacket(subType: Int, authPayload: ByteArray? = null): ByteArray =
        buildPacket(COMMAND_TYPE_AUTH, subType, authPayload, AUTH_PAYLOAD_FIELD)

    /**
     * Build a CardInfo protobuf message.
     * Fields: type(1), aid(2), name(3), isDefault(7).
     */
    fun buildCardInfo(type: CardType, aid: String, name: String, isDefault: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(WireFormat.encodeInt32(1, type.protoValue))
        out.write(WireFormat.encodeString(2, aid))
        out.write(WireFormat.encodeString(3, name))
        out.write(WireFormat.encodeBool(7, isDefault))
        return out.toByteArray()
    }

    /**
     * Build NFC payload with SmartSwitch at field 19.
     * SmartSwitch: enabled(1, bool), aids(2, repeated string).
     */
    fun buildSmartSwitch(enabled: Boolean, aids: List<String>): ByteArray {
        val switchMsg = ByteArrayOutputStream()
        switchMsg.write(WireFormat.encodeBool(1, enabled))
        for (aid in aids) {
            switchMsg.write(WireFormat.encodeString(2, aid))
        }
        return WireFormat.encodeMessage(19, switchMsg.toByteArray())
    }

    /**
     * Build AidInfo binary format: [operation:1][bleType:1][aidLen:1][aid:N].
     */
    fun buildAidInfo(operation: Int, type: CardType, aid: String): ByteArray {
        val aidBytes = aid.toByteArray(StandardCharsets.UTF_8)
        return byteArrayOf(
            operation.toByte(),
            type.bleValue.toByte(),
            aidBytes.size.toByte(),
        ) + aidBytes
    }

    /**
     * Build CardConfig binary format: [operation:1][bleType:1][aidLen:1][aid:N].
     */
    fun buildCardConfig(operation: Int, type: CardType, aid: String): ByteArray {
        val aidBytes = aid.toByteArray(StandardCharsets.UTF_8)
        return byteArrayOf(
            operation.toByte(),
            type.bleValue.toByte(),
            aidBytes.size.toByte(),
        ) + aidBytes
    }

    // --- Packet parsers ---

    /**
     * Parse a protobuf packet. Captures command_type (field 1), sub_type (field 2),
     * and all length-delimited fields into [ParsedPacket.payloads].
     */
    fun parsePacket(data: ByteArray): ParsedPacket {
        val decoder = WireFormat.Decoder(data)
        var commandType = 0
        var subType = 0
        val payloads = mutableMapOf<Int, ByteArray>()

        while (!decoder.isAtEnd) {
            val (field, wireType) = decoder.readTag()
            if (field == 0) break
            when {
                field == 1 && wireType == WireFormat.WIRE_VARINT -> {
                    commandType = decoder.readVarint().toInt()
                }
                field == 2 && wireType == WireFormat.WIRE_VARINT -> {
                    subType = decoder.readVarint().toInt()
                }
                wireType == WireFormat.WIRE_LENGTH_DELIMITED -> {
                    payloads[field] = decoder.readBytes()
                }
                else -> decoder.skipField(wireType)
            }
        }
        return ParsedPacket(commandType, subType, payloads)
    }

    /**
     * Parse card_list from NFC payload (field 5 contains repeated card descriptors).
     * Card descriptor: type(1), aid(2), name(3), card_face(4), status(6), is_default(7).
     */
    fun parseCardList(nfcPayload: ByteArray): List<NfcCard> {
        val cards = mutableListOf<NfcCard>()
        val outer = WireFormat.Decoder(nfcPayload)

        while (!outer.isAtEnd) {
            val (field, wireType) = outer.readTag()
            if (field == 0) break
            if (field == 5) {
                val cardBytes = outer.readBytes()
                cards.add(parseCardDescriptor(cardBytes))
            } else {
                outer.skipField(wireType)
            }
        }
        return cards
    }

    private fun parseCardDescriptor(data: ByteArray): NfcCard {
        val dec = WireFormat.Decoder(data)
        var typeVal = -1
        var aid = ""
        var name = ""
        var cardFace: String? = null
        var isDefault = false

        while (!dec.isAtEnd) {
            val (field, wireType) = dec.readTag()
            if (field == 0) break
            when (field) {
                1 -> typeVal = dec.readVarint().toInt()
                2 -> aid = dec.readString()
                3 -> name = dec.readString()
                4 -> cardFace = dec.readString()
                6 -> dec.readVarint() // status — reserved
                7 -> isDefault = dec.readBool()
                else -> dec.skipField(wireType)
            }
        }
        return NfcCard(
            aid = aid,
            type = CardType.fromProto(typeVal),
            name = name,
            isDefault = isDefault,
            cardFace = cardFace,
        )
    }

    /**
     * Parse SmartSwitch from NFC payload (field 19).
     * SmartSwitch: enabled(1), aids(2, repeated).
     */
    fun parseSmartSwitch(nfcPayload: ByteArray): Pair<Boolean, List<String>> {
        val outer = WireFormat.Decoder(nfcPayload)
        var switchData: ByteArray? = null

        while (!outer.isAtEnd) {
            val (field, wireType) = outer.readTag()
            if (field == 0) break
            if (field == 19) {
                switchData = outer.readBytes()
            } else {
                outer.skipField(wireType)
            }
        }

        if (switchData == null) return false to emptyList()

        val dec = WireFormat.Decoder(switchData)
        var enabled = false
        val aids = mutableListOf<String>()

        while (!dec.isAtEnd) {
            val (field, wireType) = dec.readTag()
            if (field == 0) break
            when (field) {
                1 -> enabled = dec.readBool()
                2 -> aids.add(dec.readString())
                else -> dec.skipField(wireType)
            }
        }
        return enabled to aids
    }
}
