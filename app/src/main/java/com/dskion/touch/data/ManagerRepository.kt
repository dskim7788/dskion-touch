package com.dskion.touch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dskion.touch.ai.GeminiVoices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ManagerPersona(val displayName: String) {
    MALE("민준 매니저"),
    FEMALE("서연 매니저"),
}

/** AI = Gemini TTS로 생성한 자연스러운 목소리(네트워크 필요, 무료 한도 있음).
 * DEVICE = 기기 기본 TTS만 사용 - 네트워크도 안 쓰고 한도 걱정도 없지만 덜
 * 자연스럽다. 사용자가 Gemini 무료 한도를 아끼고 싶을 때 직접 끌 수 있게
 * 한다 - 자동으로 알아서 줄이는 대신 명시적으로 고르게 하는 쪽을 택했다. */
enum class ManagerVoiceEngine {
    AI,
    DEVICE,
}

private val Context.managerDataStore by preferencesDataStore(name = "manager_settings")
private val KEY_PERSONA = stringPreferencesKey("persona")
private val KEY_VOICE_CHAT_ENABLED = booleanPreferencesKey("voice_chat_enabled")
private val KEY_MALE_VOICE = stringPreferencesKey("gemini_voice_male")
private val KEY_FEMALE_VOICE = stringPreferencesKey("gemini_voice_female")
private val KEY_VOICE_ENGINE = stringPreferencesKey("voice_engine")

/** Which 매니저 persona speaks the praise/encouragement lines and answers "매니저" voice
 * questions, whether that's on at all, which Gemini TTS voice each persona uses when an AI
 * key is configured, and whether AI voice is used at all right now. Local-only, no account/
 * backend. */
class ManagerRepository(private val context: Context) {

    val persona: Flow<ManagerPersona> = context.managerDataStore.data.map { prefs ->
        prefs[KEY_PERSONA]?.let { name -> runCatching { ManagerPersona.valueOf(name) }.getOrNull() } ?: ManagerPersona.FEMALE
    }

    val voiceChatEnabled: Flow<Boolean> = context.managerDataStore.data.map { prefs ->
        prefs[KEY_VOICE_CHAT_ENABLED] ?: true
    }

    val voiceEngine: Flow<ManagerVoiceEngine> = context.managerDataStore.data.map { prefs ->
        prefs[KEY_VOICE_ENGINE]?.let { name -> runCatching { ManagerVoiceEngine.valueOf(name) }.getOrNull() } ?: ManagerVoiceEngine.AI
    }

    val maleVoiceName: Flow<String> = context.managerDataStore.data.map { prefs ->
        prefs[KEY_MALE_VOICE] ?: GeminiVoices.DEFAULT_MALE_VOICE
    }

    val femaleVoiceName: Flow<String> = context.managerDataStore.data.map { prefs ->
        prefs[KEY_FEMALE_VOICE] ?: GeminiVoices.DEFAULT_FEMALE_VOICE
    }

    suspend fun setPersona(persona: ManagerPersona) {
        context.managerDataStore.edit { prefs -> prefs[KEY_PERSONA] = persona.name }
    }

    suspend fun setVoiceChatEnabled(enabled: Boolean) {
        context.managerDataStore.edit { prefs -> prefs[KEY_VOICE_CHAT_ENABLED] = enabled }
    }

    suspend fun setVoiceEngine(engine: ManagerVoiceEngine) {
        context.managerDataStore.edit { prefs -> prefs[KEY_VOICE_ENGINE] = engine.name }
    }

    suspend fun setMaleVoiceName(voiceName: String) {
        context.managerDataStore.edit { prefs -> prefs[KEY_MALE_VOICE] = voiceName }
    }

    suspend fun setFemaleVoiceName(voiceName: String) {
        context.managerDataStore.edit { prefs -> prefs[KEY_FEMALE_VOICE] = voiceName }
    }
}
