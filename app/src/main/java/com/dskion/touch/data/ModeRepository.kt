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
private val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")

/** Local-only settings (no account, no backend) - which of the two modes is active, and whether
 * the floating mic button (only useful for 일반 모드's manual tap-to-talk - 배달 모드 is hands-free
 * and never shows it, mode selection included) is shown at all. */
class ModeRepository(private val context: Context) {

    val mode: Flow<OperatingMode> = context.dataStore.data.map { prefs ->
        if (prefs[KEY_DELIVERY_MODE] == true) OperatingMode.DELIVERY else OperatingMode.NORMAL
    }

    val overlayEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_OVERLAY_ENABLED] ?: true }

    suspend fun setMode(mode: OperatingMode) {
        context.dataStore.edit { prefs -> prefs[KEY_DELIVERY_MODE] = mode == OperatingMode.DELIVERY }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_OVERLAY_ENABLED] = enabled }
    }
}
