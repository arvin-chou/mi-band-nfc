package com.mibandnfc.di

import android.content.Context
import androidx.room.Room
import com.mibandnfc.data.db.AppDatabase
import com.mibandnfc.data.db.NfcCardDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "miband_nfc.db"
        ).build()

    @Provides
    @Singleton
    fun provideNfcCardDao(database: AppDatabase): NfcCardDao =
        database.nfcCardDao()
}
