package com.mibandnfc.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private object Keys {
    val BAND_MAC = stringPreferencesKey("band_mac")
    val AUTH_KEY = stringPreferencesKey("auth_key")
    val AUTO_SWITCH_ENABLED = booleanPreferencesKey("auto_switch_enabled")
    val IS_SUPPORTER = booleanPreferencesKey("is_supporter")
}

@Singleton
class AppPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store = context.dataStore

    val bandMac: Flow<String> = store.data.map { prefs ->
        prefs[Keys.BAND_MAC] ?: ""
    }

    val authKey: Flow<String> = store.data.map { prefs ->
        prefs[Keys.AUTH_KEY] ?: ""
    }

    val autoSwitchEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.AUTO_SWITCH_ENABLED] ?: false
    }

    val isSupporter: Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.IS_SUPPORTER] ?: false
    }

    suspend fun setBandMac(mac: String) {
        store.edit { prefs ->
            prefs[Keys.BAND_MAC] = mac
        }
    }

    suspend fun setAuthKey(key: String) {
        store.edit { prefs ->
            prefs[Keys.AUTH_KEY] = key
        }
    }

    suspend fun setAutoSwitchEnabled(enabled: Boolean) {
        store.edit { prefs ->
            prefs[Keys.AUTO_SWITCH_ENABLED] = enabled
        }
    }

    suspend fun setSupporter(supporter: Boolean) {
        store.edit { prefs ->
            prefs[Keys.IS_SUPPORTER] = supporter
        }
    }
}
