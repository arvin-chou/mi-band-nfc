package com.mibandnfc.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "switch_rules")
data class SwitchRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aid: String,
    val cardType: Int,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Int = 0x7F,
    val enabled: Boolean = true,
    val label: String = ""
)
