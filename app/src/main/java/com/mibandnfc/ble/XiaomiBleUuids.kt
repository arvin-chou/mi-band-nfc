package com.mibandnfc.ble

import java.util.UUID

/**
 * BLE UUIDs for Xiaomi Smart Band 8, verified against real GATT scan.
 *
 * Primary service is FE95 (Xiaomi BLE). All auth and NFC commands go through
 * a single multiplexed characteristic 0051 (write + notify).
 */
object XiaomiBleUuids {

    // --- Services ---
    val SERVICE_XIAOMI = UUID.fromString("0000FE95-0000-1000-8000-00805F9B34FB")
    val SERVICE_FDAB = UUID.fromString("0000FDAB-0000-1000-8000-00805F9B34FB")
    val SERVICE_DEVICE_INFO = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    val SERVICE_BATTERY = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")

    // --- FE95 Characteristics ---
    val CHAR_DEVICE_INFO = UUID.fromString("00000050-0000-1000-8000-00805F9B34FB")
    val CHAR_MAIN_CHANNEL = UUID.fromString("00000051-0000-1000-8000-00805F9B34FB")
    val CHAR_ACTIVITY = UUID.fromString("00000052-0000-1000-8000-00805F9B34FB")

    // --- Standard BLE ---
    val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    val CHAR_BATTERY_LEVEL = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    val CHAR_FIRMWARE_REV = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")
    val CHAR_HARDWARE_REV = UUID.fromString("00002A27-0000-1000-8000-00805F9B34FB")
}
