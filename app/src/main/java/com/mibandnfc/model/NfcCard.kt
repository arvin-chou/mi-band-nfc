package com.mibandnfc.model

data class NfcCard(
    val aid: String,
    val type: CardType,
    val name: String,
    val isDefault: Boolean = false,
    val cardFace: String? = null,
)
