package com.mibandnfc.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mibandnfc.ble.BandService
import com.mibandnfc.data.repository.CardRepository
import com.mibandnfc.model.CardType
import com.mibandnfc.model.NfcCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val cardRepo: CardRepository,
    private val bandService: BandService,
) : ViewModel() {

    val cards: StateFlow<List<NfcCard>> = bandService.cards

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _showDeleteConfirm = MutableStateFlow<NfcCard?>(null)
    val showDeleteConfirm: StateFlow<NfcCard?> = _showDeleteConfirm.asStateFlow()

    fun showAdd() {
        _showAddDialog.value = true
    }

    fun hideAdd() {
        _showAddDialog.value = false
    }

    fun setDefault(card: NfcCard) {
        viewModelScope.launch {
            cardRepo.setDefault(card.aid, card.type)
        }
    }

    fun requestDelete(card: NfcCard) {
        _showDeleteConfirm.value = card
    }

    fun cancelDelete() {
        _showDeleteConfirm.value = null
    }

    fun confirmDelete() {
        val card = _showDeleteConfirm.value ?: return
        _showDeleteConfirm.value = null
        viewModelScope.launch {
            cardRepo.deleteCard(card.aid, card.type)
        }
    }

    fun addCard(aid: String, type: CardType, name: String) {
        viewModelScope.launch {
            cardRepo.addCard(aid, type, name)
            _showAddDialog.value = false
        }
    }
}
