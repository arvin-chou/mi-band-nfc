package com.mibandnfc.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mibandnfc.data.db.entity.NfcCardEntity
import com.mibandnfc.data.db.entity.SwitchRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NfcCardDao {

    @Query("SELECT * FROM nfc_cards ORDER BY addedAt ASC")
    fun getAll(): Flow<List<NfcCardEntity>>

    @Query("SELECT * FROM nfc_cards WHERE aid = :aid LIMIT 1")
    suspend fun getByAid(aid: String): NfcCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: NfcCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cards: List<NfcCardEntity>)

    @Query("DELETE FROM nfc_cards WHERE aid = :aid")
    suspend fun delete(aid: String)

    @Query("DELETE FROM nfc_cards")
    suspend fun deleteAll()

    @Query("SELECT * FROM switch_rules ORDER BY hour, minute ASC")
    fun getSwitchRules(): Flow<List<SwitchRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: SwitchRuleEntity): Long

    @Update
    suspend fun updateRule(rule: SwitchRuleEntity)

    @Query("DELETE FROM switch_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)
}
