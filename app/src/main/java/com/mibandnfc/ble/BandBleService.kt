package com.mibandnfc.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.mibandnfc.ble.proto.NfcProto
import com.mibandnfc.model.BandState
import com.mibandnfc.model.CardType
import com.mibandnfc.model.NfcCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BLE service for Xiaomi Smart Band 8.
 *
 * All commands (auth, NFC, etc.) flow through a single multiplexed channel
 * on characteristic 0051 under the FE95 service. The channel protocol wraps
 * protobuf payloads with an encrypted_flag byte, then chunks across MTU.
 *
 * Connection flow:
 *   1. GATT connect → request MTU 512
 *   2. Discover services → find FE95/0051
 *   3. Enable notifications on 0051
 *   4. Auth handshake (protobuf cmd_type=1) on 0051
 *   5. Read device info (0050) + firmware (2A26)
 *   6. Ready for NFC commands (protobuf cmd_type=5) on 0051
 */
@SuppressLint("MissingPermission")
class BandBleService(
    private val context: Context,
    private val adapter: BluetoothAdapter,
) : BandService {

    companion object {
        private const val TAG = "BandBleService"
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val COMMAND_TIMEOUT_MS = 5_000L
        private const val WRITE_TIMEOUT_MS = 3_000L
        private const val TARGET_MTU = 512

        private const val SUB_TYPE_QUERY_CARDS = 2
        private const val SUB_TYPE_GET_AUTO_SWITCH = 21
        private const val SUB_TYPE_SET_AUTO_SWITCH = 22
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val _state = MutableStateFlow<BandState>(BandState.Disconnected)
    override val state: StateFlow<BandState> = _state.asStateFlow()

    private val _cards = MutableStateFlow<List<NfcCard>>(emptyList())
    override val cards: StateFlow<List<NfcCard>> = _cards.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var auth: XiaomiAuth? = null
    private var negotiatedMtu: Int = 23
    private var firmware: String = ""
    private val reassembler = ChunkedTransfer.Reassembler()

    private var mainChannel: BluetoothGattCharacteristic? = null

    private var connectionDeferred: CompletableDeferred<Unit>? = null
    private var pendingResponse: CompletableDeferred<ByteArray>? = null
    private var writeCompletion: CompletableDeferred<Int>? = null
    private var descriptorWriteCompletion: CompletableDeferred<Int>? = null
    private var readCompletion: CompletableDeferred<ByteArray?>? = null

    // --- BandService implementation ---

    override suspend fun connect(mac: String, authKey: ByteArray) {
        if (_state.value is BandState.Connected) {
            Log.d(TAG, "Already connected")
            return
        }

        _state.value = BandState.Connecting
        auth = XiaomiAuth(authKey)

        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                connectGatt(mac)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _state.value = BandState.Error("Connection failed: ${e.message}")
            cleanupGatt()
            throw e
        }
    }

    override suspend fun disconnect() {
        Log.d(TAG, "Disconnecting")
        cleanupGatt()
        _state.value = BandState.Disconnected
        _cards.value = emptyList()
    }

    override suspend fun refreshCards() {
        ensureConnected()
        val packet = NfcProto.buildPacket(NfcProto.COMMAND_TYPE_NFC, SUB_TYPE_QUERY_CARDS)
        val response = sendChannelCommand(packet)
        val parsed = NfcProto.parsePacket(response)
        if (parsed.nfcPayload != null) {
            val list = NfcProto.parseCardList(parsed.nfcPayload!!)
            _cards.value = list
            Log.d(TAG, "Refreshed ${list.size} cards")
        } else {
            Log.w(TAG, "No NFC payload in card list response: $parsed")
        }
    }

    override suspend fun setDefaultCard(aid: String, type: CardType) {
        ensureConnected()
        val config = NfcProto.buildCardConfig(NfcProto.CONFIG_OP_ACTIVATE, type, aid)
        val packet = NfcProto.buildPacket(NfcProto.COMMAND_TYPE_NFC, 6, config)
        sendChannelCommand(packet)
        Log.d(TAG, "Set default card: aid=$aid, type=$type")
        refreshCards()
    }

    override suspend fun addCard(aid: String, type: CardType, name: String) {
        ensureConnected()
        val aidInfo = NfcProto.buildAidInfo(NfcProto.AID_OP_ADD, type, aid)
        val packet = NfcProto.buildPacket(NfcProto.COMMAND_TYPE_NFC, 4, aidInfo)
        sendChannelCommand(packet)
        Log.d(TAG, "Added card: aid=$aid, type=$type, name=$name")
        refreshCards()
    }

    override suspend fun deleteCard(aid: String, type: CardType) {
        ensureConnected()
        val aidInfo = NfcProto.buildAidInfo(NfcProto.AID_OP_DELETE, type, aid)
        val packet = NfcProto.buildPacket(NfcProto.COMMAND_TYPE_NFC, 4, aidInfo)
        sendChannelCommand(packet)
        Log.d(TAG, "Deleted card: aid=$aid, type=$type")
        refreshCards()
    }

    override suspend fun getAutoSwitch(): Pair<Boolean, List<String>> {
        ensureConnected()
        val packet = NfcProto.buildPacket(NfcProto.COMMAND_TYPE_NFC, SUB_TYPE_GET_AUTO_SWITCH)
        val response = sendChannelCommand(packet)
        val parsed = NfcProto.parsePacket(response)
        return if (parsed.nfcPayload != null) {
            NfcProto.parseSmartSwitch(parsed.nfcPayload!!)
        } else {
            false to emptyList()
        }
    }

    override suspend fun setAutoSwitch(enabled: Boolean, aids: List<String>) {
        ensureConnected()
        val switchPayload = NfcProto.buildSmartSwitch(enabled, aids)
        val packet = NfcProto.buildPacket(NfcProto.COMMAND_TYPE_NFC, SUB_TYPE_SET_AUTO_SWITCH, switchPayload)
        sendChannelCommand(packet)
        Log.d(TAG, "Set auto-switch: enabled=$enabled, aids=$aids")
    }

    // --- Internal: GATT connection ---

    private suspend fun connectGatt(mac: String) = suspendCancellableCoroutine { cont ->
        val device: BluetoothDevice = adapter.getRemoteDevice(mac)
        connectionDeferred = CompletableDeferred()

        Log.d(TAG, "Connecting to ${device.name ?: mac}")
        val g = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        gatt = g

        cont.invokeOnCancellation {
            g.disconnect()
            g.close()
        }

        scope.launch {
            try {
                connectionDeferred?.await()
                cont.resume(Unit)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }

    // --- Internal: channel command send/receive ---

    /**
     * Send a protobuf command through the channel protocol and wait for the response.
     * Wraps in channel frame (encrypted_flag + protobuf), chunks, writes each chunk
     * with write-callback acknowledgment, then waits for the response notification.
     */
    private suspend fun sendChannelCommand(protobuf: ByteArray): ByteArray = writeMutex.withLock {
        val g = gatt ?: throw IllegalStateException("Not connected")
        val char = mainChannel ?: throw IllegalStateException("Main channel not found")

        val deferred = CompletableDeferred<ByteArray>()
        pendingResponse = deferred

        val channelMsg = ChunkedTransfer.buildChannelPayload(encrypted = false, protobuf)
        val chunks = ChunkedTransfer.split(channelMsg, handle = 0, mtu = negotiatedMtu)

        Log.d(TAG, "Sending ${protobuf.size}B protobuf in ${chunks.size} chunk(s) (mtu=$negotiatedMtu)")

        for ((i, chunk) in chunks.withIndex()) {
            Log.d(TAG, "Writing chunk ${i + 1}/${chunks.size}: ${chunk.size}B")
            writeAndAwait(g, char, chunk)
        }

        return withTimeout(COMMAND_TIMEOUT_MS) { deferred.await() }
    }

    /**
     * Write a value and wait for the onCharacteristicWrite callback.
     * Uses API 33+ overload when available, falls back to deprecated API.
     */
    private suspend fun writeAndAwait(
        g: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        val deferred = CompletableDeferred<Int>()
        writeCompletion = deferred

        val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(
                char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BTSTATUS_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = value
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(char)
        }

        if (!initiated) {
            writeCompletion = null
            throw IllegalStateException("writeCharacteristic initiation failed")
        }

        val status = withTimeout(WRITE_TIMEOUT_MS) { deferred.await() }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            throw IllegalStateException("Write callback failed: status=$status")
        }
    }

    /**
     * Enable notifications on a characteristic and wait for the descriptor write callback.
     */
    private suspend fun enableNotificationsAndAwait(
        g: BluetoothGatt,
        char: BluetoothGattCharacteristic,
    ) {
        g.setCharacteristicNotification(char, true)

        val desc = char.getDescriptor(XiaomiBleUuids.CCCD)
            ?: throw IllegalStateException("CCCD not found on ${char.uuid}")

        val deferred = CompletableDeferred<Int>()
        descriptorWriteCompletion = deferred

        val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(
                desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            ) == BTSTATUS_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(desc)
        }

        if (!initiated) {
            descriptorWriteCompletion = null
            throw IllegalStateException("writeDescriptor initiation failed for ${char.uuid}")
        }

        val status = withTimeout(WRITE_TIMEOUT_MS) { deferred.await() }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            throw IllegalStateException("Descriptor write failed: status=$status")
        }
        Log.d(TAG, "Notifications enabled on ${char.uuid}")
    }

    /**
     * Read a characteristic and wait for the result.
     */
    private suspend fun readCharacteristicAwait(
        g: BluetoothGatt,
        char: BluetoothGattCharacteristic,
    ): ByteArray? {
        val deferred = CompletableDeferred<ByteArray?>()
        readCompletion = deferred

        @Suppress("DEPRECATION")
        if (!g.readCharacteristic(char)) {
            readCompletion = null
            Log.w(TAG, "readCharacteristic initiation failed for ${char.uuid}")
            return null
        }

        return try {
            withTimeout(WRITE_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            Log.w(TAG, "Read timeout for ${char.uuid}: ${e.message}")
            null
        }
    }

    // --- Internal: authentication ---

    /**
     * Perform the full auth handshake over the channel protocol.
     */
    private suspend fun performAuth() {
        _state.value = BandState.Authenticating
        val localAuth = auth ?: throw IllegalStateException("Auth not initialized")

        Log.d(TAG, "Starting auth handshake...")

        val authReq = localAuth.buildAuthRequest()
        val randomResp = sendChannelCommand(authReq)
        val parsedRandom = NfcProto.parsePacket(randomResp)
        Log.d(TAG, "Auth step 1 response: $parsedRandom")

        val authorizePacket = localAuth.handleAuthResponse(parsedRandom)
        if (authorizePacket != null) {
            val successResp = sendChannelCommand(authorizePacket)
            val parsedSuccess = NfcProto.parsePacket(successResp)
            Log.d(TAG, "Auth step 2 response: $parsedSuccess")
            localAuth.handleAuthResponse(parsedSuccess)
        }

        if (!localAuth.isAuthenticated) {
            throw IllegalStateException("Auth handshake completed without success")
        }
    }

    // --- Internal: device info ---

    private suspend fun readDeviceInfo(): String {
        val g = gatt ?: return ""

        val deviceInfoChar = g.getService(XiaomiBleUuids.SERVICE_XIAOMI)
            ?.getCharacteristic(XiaomiBleUuids.CHAR_DEVICE_INFO)
        if (deviceInfoChar != null) {
            val info = readCharacteristicAwait(g, deviceInfoChar)
            if (info != null) {
                Log.d(TAG, "Device info (0050): ${info.toHex()}, ${info.size}B")
            }
        }

        val fwService = g.getService(XiaomiBleUuids.SERVICE_DEVICE_INFO)
        if (fwService != null) {
            val fwChar = fwService.getCharacteristic(XiaomiBleUuids.CHAR_FIRMWARE_REV)
            if (fwChar != null) {
                val fwBytes = readCharacteristicAwait(g, fwChar)
                if (fwBytes != null) {
                    val fwStr = String(fwBytes, Charsets.UTF_8).trim()
                    Log.d(TAG, "Firmware: $fwStr")
                    return fwStr
                }
            }
            val hwChar = fwService.getCharacteristic(XiaomiBleUuids.CHAR_HARDWARE_REV)
            if (hwChar != null) {
                val hwBytes = readCharacteristicAwait(g, hwChar)
                if (hwBytes != null) {
                    Log.d(TAG, "Hardware: ${String(hwBytes, Charsets.UTF_8).trim()}")
                }
            }
        }

        return ""
    }

    // --- Internal: notification dispatch ---

    private fun onMainChannelData(rawData: ByteArray) {
        Log.d(TAG, "Main channel data: ${rawData.size}B, first=${
            rawData.take(8).joinToString("") { "%02x".format(it) }
        }")

        val complete = reassembler.feed(rawData) ?: return

        val (encrypted, protobuf) = ChunkedTransfer.parseChannelPayload(complete)
        Log.d(TAG, "Complete message: encrypted=$encrypted, protobuf=${protobuf.size}B")

        if (encrypted) {
            Log.w(TAG, "Received encrypted message — decryption not implemented, " +
                "passing raw protobuf to handler")
        }

        pendingResponse?.complete(protobuf)
        pendingResponse = null
    }

    // --- Helpers ---

    private fun ensureConnected() {
        check(_state.value is BandState.Connected) { "Band not connected" }
    }

    private fun cleanupGatt() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        mainChannel = null
        reassembler.reset()
        auth?.reset()
        pendingResponse?.cancel()
        pendingResponse = null
        writeCompletion?.cancel()
        writeCompletion = null
        descriptorWriteCompletion?.cancel()
        descriptorWriteCompletion = null
        readCompletion?.cancel()
        readCompletion = null
    }

    fun destroy() {
        cleanupGatt()
        scope.cancel()
    }

    // --- GATT Callback ---

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected, requesting MTU $TARGET_MTU")
                    gatt.requestMtu(TARGET_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected (status=$status)")
                    _state.value = BandState.Disconnected
                    connectionDeferred?.completeExceptionally(
                        IllegalStateException("Disconnected (status=$status)")
                    )
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            Log.d(TAG, "MTU: $negotiatedMtu (status=$status)")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                connectionDeferred?.completeExceptionally(
                    IllegalStateException("Service discovery failed: $status")
                )
                return
            }

            logDiscoveredServices(gatt)

            val xiaomiService = gatt.getService(XiaomiBleUuids.SERVICE_XIAOMI)
            if (xiaomiService == null) {
                Log.e(TAG, "FE95 service not found!")
                connectionDeferred?.completeExceptionally(
                    IllegalStateException("Xiaomi FE95 service not found")
                )
                return
            }

            mainChannel = xiaomiService.getCharacteristic(XiaomiBleUuids.CHAR_MAIN_CHANNEL)
            if (mainChannel == null) {
                Log.e(TAG, "Main channel 0051 not found!")
                connectionDeferred?.completeExceptionally(
                    IllegalStateException("Main channel characteristic 0051 not found")
                )
                return
            }

            val props = mainChannel!!.properties
            Log.d(TAG, "Main channel 0051 props: write=${props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0}, " +
                "wnr=${props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0}, " +
                "notify=${props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0}")

            scope.launch {
                try {
                    enableNotificationsAndAwait(gatt, mainChannel!!)
                    performAuth()

                    firmware = try { readDeviceInfo() } catch (e: Exception) {
                        Log.w(TAG, "Device info read failed: ${e.message}")
                        ""
                    }

                    val device = gatt.device
                    _state.value = BandState.Connected(
                        name = device.name ?: "Mi Band",
                        mac = device.address,
                        firmware = firmware,
                    )
                    Log.d(TAG, "Fully connected: ${device.name} (${device.address}), fw=$firmware")
                    connectionDeferred?.complete(Unit)

                    scope.launch { try { refreshCards() } catch (e: Exception) {
                        Log.w(TAG, "Initial card refresh failed: ${e.message}")
                    } }
                } catch (e: Exception) {
                    Log.e(TAG, "Post-discovery setup failed", e)
                    _state.value = BandState.Error("Setup failed: ${e.message}")
                    connectionDeferred?.completeExceptionally(e)
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            Log.d(TAG, "onDescriptorWrite: char=${descriptor.characteristic.uuid}, status=$status")
            descriptorWriteCompletion?.complete(status)
            descriptorWriteCompletion = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.d(TAG, "onCharacteristicWrite: uuid=${characteristic.uuid}, status=$status")
            writeCompletion?.complete(status)
            writeCompletion = null
        }

        // API 33+: value provided directly
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            Log.d(TAG, "onCharChanged(33+): uuid=${characteristic.uuid}, ${value.size}B")
            if (characteristic.uuid == XiaomiBleUuids.CHAR_MAIN_CHANNEL) {
                onMainChannelData(value)
            }
        }

        // Pre-API 33 fallback
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            Log.d(TAG, "onCharChanged(legacy): uuid=${characteristic.uuid}, ${value.size}B")
            if (characteristic.uuid == XiaomiBleUuids.CHAR_MAIN_CHANNEL) {
                onMainChannelData(value)
            }
        }

        // API 33+: value provided directly
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            Log.d(TAG, "onCharRead(33+): uuid=${characteristic.uuid}, status=$status, ${value.size}B")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readCompletion?.complete(value)
            } else {
                readCompletion?.complete(null)
            }
            readCompletion = null
        }

        // Pre-API 33 fallback
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.d(TAG, "onCharRead(legacy): uuid=${characteristic.uuid}, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readCompletion?.complete(characteristic.value)
            } else {
                readCompletion?.complete(null)
            }
            readCompletion = null
        }
    }

    // --- Debug helpers ---

    private fun logDiscoveredServices(gatt: BluetoothGatt) {
        for (service in gatt.services) {
            Log.d(TAG, "Service: ${service.uuid}")
            for (char in service.characteristics) {
                val p = char.properties
                val flags = buildList {
                    if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("R")
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("W")
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WNR")
                    if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("N")
                    if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("I")
                }.joinToString(",")
                Log.d(TAG, "  Char: ${char.uuid} [$flags]")
            }
        }
    }
}

/**
 * BluetoothStatusCodes.SUCCESS equivalent for all API levels.
 * On API 33+ this equals BluetoothStatusCodes.SUCCESS (0).
 */
private const val BTSTATUS_SUCCESS = 0
