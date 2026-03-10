package com.mibandnfc.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mibandnfc.ble.BandService
import com.mibandnfc.data.db.entity.SwitchRuleEntity
import com.mibandnfc.data.prefs.AppPrefs
import com.mibandnfc.data.repository.CardRepository
import com.mibandnfc.model.CardType
import com.mibandnfc.model.NfcCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPrefs,
    private val bandService: BandService,
    private val cardRepo: CardRepository,
) : ViewModel() {

    val bandMac: StateFlow<String> = prefs.bandMac
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val authKey: StateFlow<String> = prefs.authKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val autoSwitchEnabled: StateFlow<Boolean> = prefs.autoSwitchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isSupporter: StateFlow<Boolean> = prefs.isSupporter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val switchRules: StateFlow<List<SwitchRuleEntity>> = cardRepo.switchRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cards: StateFlow<List<NfcCard>> = bandService.cards

    private val _showAddRuleDialog = MutableStateFlow(false)
    val showAddRuleDialog: StateFlow<Boolean> = _showAddRuleDialog.asStateFlow()

    fun saveBandMac(mac: String) {
        viewModelScope.launch { prefs.setBandMac(mac) }
    }

    fun saveAuthKey(key: String) {
        viewModelScope.launch { prefs.setAuthKey(key) }
    }

    fun toggleAutoSwitch(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoSwitchEnabled(enabled) }
    }

    fun showAddRule() {
        _showAddRuleDialog.value = true
    }

    fun hideAddRule() {
        _showAddRuleDialog.value = false
    }

    fun addRule(
        aid: String,
        cardType: CardType,
        hour: Int,
        minute: Int,
        daysOfWeek: Int,
        label: String,
    ) {
        viewModelScope.launch {
            cardRepo.addSwitchRule(
                SwitchRuleEntity(
                    aid = aid,
                    cardType = cardType.protoValue,
                    hour = hour,
                    minute = minute,
                    daysOfWeek = daysOfWeek,
                    label = label,
                )
            )
            _showAddRuleDialog.value = false
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch { cardRepo.removeSwitchRule(id) }
    }

    fun setSupporter(supporter: Boolean) {
        viewModelScope.launch { prefs.setSupporter(supporter) }
    }
}
