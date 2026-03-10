package com.mibandnfc.data.repository

import com.mibandnfc.ble.BandService
import com.mibandnfc.data.db.NfcCardDao
import com.mibandnfc.data.db.entity.SwitchRuleEntity
import com.mibandnfc.data.db.entity.toDomain
import com.mibandnfc.data.db.entity.toEntity
import com.mibandnfc.model.CardType
import com.mibandnfc.model.NfcCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepository @Inject constructor(
    private val bandService: BandService,
    private val dao: NfcCardDao
) {
    val localCards: Flow<List<NfcCard>> = dao.getAll().map { list ->
        list.map { it.toDomain() }
    }
    val bandCards: StateFlow<List<NfcCard>> = bandService.cards
    val switchRules: Flow<List<SwitchRuleEntity>> = dao.getSwitchRules()

    suspend fun syncFromBand() {
        bandService.refreshCards()
        val cards = bandService.cards.value
        dao.upsertAll(cards.map { it.toEntity() })
    }

    suspend fun setDefault(aid: String, type: CardType) {
        bandService.setDefaultCard(aid, type)
    }

    suspend fun addCard(aid: String, type: CardType, name: String) {
        bandService.addCard(aid, type, name)
    }

    suspend fun deleteCard(aid: String, type: CardType) {
        bandService.deleteCard(aid, type)
        dao.delete(aid)
    }

    suspend fun addSwitchRule(rule: SwitchRuleEntity): Long = dao.insertRule(rule)

    suspend fun removeSwitchRule(id: Long) = dao.deleteRule(id)
}
