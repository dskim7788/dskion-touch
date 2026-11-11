package com.dskion.touch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ManagerPersona(val displayName: String) {
    MALE("민준 매니저"),
    FEMALE("서연 매니저"),
}

private val Context.managerDataStore by preferencesDataStore(name = "manager_settings")
private val KEY_PERSONA = stringPreferencesKey("persona")
private val KEY_VOICE_CHAT_ENABLED = booleanPreferencesKey("voice_chat_enabled")

/** Which 매니저 persona speaks the praise/encouragement lines and answers "매니저" voice
 * questions, and whether that's on at all. Local-only, no account/backend. */
class ManagerRepository(private val context: Context) {

    val persona: Flow<ManagerPersona> = context.managerDataStore.data.map { prefs ->
        prefs[KEY_PERSONA]?.let { name -> runCatching { ManagerPersona.valueOf(name) }.getOrNull() } ?: ManagerPersona.FEMALE
    }

    val voiceChatEnabled: Flow<Boolean> = context.managerDataStore.data.map { prefs ->
        prefs[KEY_VOICE_CHAT_ENABLED] ?: true
    }

    suspend fun setPersona(persona: ManagerPersona) {
        context.managerDataStore.edit { prefs -> prefs[KEY_PERSONA] = persona.name }
    }

    suspend fun setVoiceChatEnabled(enabled: Boolean) {
        context.managerDataStore.edit { prefs -> prefs[KEY_VOICE_CHAT_ENABLED] = enabled }
    }
}
