package com.mibandnfc.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mibandnfc.data.db.entity.NfcCardEntity
import com.mibandnfc.data.db.entity.SwitchRuleEntity

@Database(
    entities = [NfcCardEntity::class, SwitchRuleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nfcCardDao(): NfcCardDao
}
