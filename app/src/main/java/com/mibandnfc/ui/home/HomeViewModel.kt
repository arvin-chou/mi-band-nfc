package com.mibandnfc.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mibandnfc.ble.BandService
import com.mibandnfc.data.prefs.AppPrefs
import com.mibandnfc.data.repository.CardRepository
import com.mibandnfc.model.BandState
import com.mibandnfc.model.NfcCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bandService: BandService,
    private val cardRepo: CardRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    val appPrefs: AppPrefs = prefs

    val bandState: StateFlow<BandState> = bandService.state

    val cards: StateFlow<List<NfcCard>> = bandService.cards

    val isConnecting: StateFlow<Boolean> = bandState
        .map { it is BandState.Scanning || it is BandState.Connecting || it is BandState.Authenticating }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun connect() {
        viewModelScope.launch {
            val mac = prefs.bandMac.first()
            val keyHex = prefs.authKey.first()
            if (mac.isNotBlank() && keyHex.length == 32) {
                val keyBytes = keyHex.chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
                bandService.connect(mac, keyBytes)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bandService.disconnect()
        }
    }

    fun setDefault(card: NfcCard) {
        viewModelScope.launch {
            cardRepo.setDefault(card.aid, card.type)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                cardRepo.syncFromBand()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
