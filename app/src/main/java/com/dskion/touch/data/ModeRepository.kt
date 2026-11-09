package com.dskion.touch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class OperatingMode { NORMAL, DELIVERY }

private val Context.dataStore by preferencesDataStore(name = "touch_settings")
private val KEY_DELIVERY_MODE = booleanPreferencesKey("delivery_mode")

/** Local-only settings (no account, no backend) - just which of the two modes is active. */
class ModeRepository(private val context: Context) {

    val mode: Flow<OperatingMode> = context.dataStore.data.map { prefs ->
        if (prefs[KEY_DELIVERY_MODE] == true) OperatingMode.DELIVERY else OperatingMode.NORMAL
    }

    suspend fun setMode(mode: OperatingMode) {
        context.dataStore.edit { prefs -> prefs[KEY_DELIVERY_MODE] = mode == OperatingMode.DELIVERY }
    }
}
