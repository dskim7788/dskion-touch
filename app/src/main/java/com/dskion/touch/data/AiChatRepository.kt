package com.dskion.touch.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aiChatDataStore by preferencesDataStore(name = "ai_chat_settings")
private val KEY_API_KEY = stringPreferencesKey("anthropic_api_key")

/** Optional Anthropic API key that turns the 매니저 chat from scripted replies into a real AI
 * conversation - same provider/model as 루틴 라이더의 "AI 루틴 관리" 기능. This app has no
 * account/backend to hold the key server-side, so the user enters their own key in Settings and
 * it's used straight from the device. Blank/unset keeps the feature fully offline (scripted
 * replies), exactly as before. */
class AiChatRepository(private val context: Context) {

    val apiKey: Flow<String> = context.aiChatDataStore.data.map { prefs -> prefs[KEY_API_KEY] ?: "" }

    suspend fun setApiKey(key: String) {
        context.aiChatDataStore.edit { prefs -> prefs[KEY_API_KEY] = key.trim() }
    }
}
