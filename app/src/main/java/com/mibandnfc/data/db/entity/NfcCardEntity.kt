package com.mibandnfc.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mibandnfc.model.CardType
import com.mibandnfc.model.NfcCard

@Entity(tableName = "nfc_cards")
data class NfcCardEntity(
    @PrimaryKey val aid: String,
    val type: Int,
    val name: String,
    val isDefault: Boolean = false,
    val cardFace: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

fun NfcCardEntity.toDomain(): NfcCard = NfcCard(
    aid = aid,
    type = CardType.fromProto(type),
    name = name,
    isDefault = isDefault,
    cardFace = cardFace
)

fun NfcCard.toEntity(): NfcCardEntity = NfcCardEntity(
    aid = aid,
    type = type.protoValue,
    name = name,
    isDefault = isDefault,
    cardFace = cardFace
)
