package com.mibandnfc.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.mibandnfc.ble.BandBleService
import com.mibandnfc.ble.BandService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {

    @Binds
    @Singleton
    abstract fun bindBandService(impl: BandBleService): BandService

    companion object {
        @Provides
        @Singleton
        fun provideBandBleService(
            @ApplicationContext context: Context
        ): BandBleService {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: throw IllegalStateException("Bluetooth not available")
            return BandBleService(context, adapter)
        }
    }
}
