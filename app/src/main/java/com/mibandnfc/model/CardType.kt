package com.mibandnfc.model

enum class CardType(val protoValue: Int, val bleValue: Int, val label: String) {
    DOOR(0, 2, "門禁卡"),
    BUS(1, 1, "交通卡"),
    UNIONPAY(2, 4, "銀聯"),
    MASTERCARD(4, 3, "Mastercard"),
    VISA(5, 8, "Visa"),
    UNKNOWN(-1, 0, "未知");

    companion object {
        fun fromProto(v: Int): CardType = entries.find { it.protoValue == v } ?: UNKNOWN
        fun fromBle(v: Int): CardType = entries.find { it.bleValue == v } ?: UNKNOWN
    }
}
