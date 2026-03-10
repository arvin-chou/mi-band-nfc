package com.mibandnfc.ble

import com.mibandnfc.model.BandState
import com.mibandnfc.model.CardType
import com.mibandnfc.model.NfcCard
import kotlinx.coroutines.flow.StateFlow

interface BandService {
    val state: StateFlow<BandState>
    val cards: StateFlow<List<NfcCard>>
    suspend fun connect(mac: String, authKey: ByteArray)
    suspend fun disconnect()
    suspend fun refreshCards()
    suspend fun setDefaultCard(aid: String, type: CardType)
    suspend fun addCard(aid: String, type: CardType, name: String)
    suspend fun deleteCard(aid: String, type: CardType)
    suspend fun getAutoSwitch(): Pair<Boolean, List<String>>
    suspend fun setAutoSwitch(enabled: Boolean, aids: List<String>)
}
