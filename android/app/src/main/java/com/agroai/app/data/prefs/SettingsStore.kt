package com.agroai.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val onboarded: Boolean = false,
    /** Crops the farmer grows; the app offers and downloads experts for these. */
    val crops: Set<String> = emptySet(),
    val wifiOnly: Boolean = true,
    val lastCrop: String? = null,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val crops = stringSetPreferencesKey("crops")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val lastCrop = stringPreferencesKey("last_crop")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            onboarded = p[Keys.onboarded] ?: false,
            crops = p[Keys.crops] ?: emptySet(),
            wifiOnly = p[Keys.wifiOnly] ?: true,
            lastCrop = p[Keys.lastCrop],
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboarded(value: Boolean) = context.dataStore.edit { it[Keys.onboarded] = value }

    suspend fun setCrops(crops: Set<String>) = context.dataStore.edit { it[Keys.crops] = crops }

    suspend fun addCrop(crop: String) = context.dataStore.edit { it[Keys.crops] = (it[Keys.crops] ?: emptySet()) + crop }

    suspend fun removeCrop(crop: String) = context.dataStore.edit { it[Keys.crops] = (it[Keys.crops] ?: emptySet()) - crop }

    suspend fun setWifiOnly(value: Boolean) = context.dataStore.edit { it[Keys.wifiOnly] = value }

    suspend fun setLastCrop(crop: String) = context.dataStore.edit { it[Keys.lastCrop] = crop }
}
