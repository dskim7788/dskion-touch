package com.dskion.touch.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.commandPhrasesDataStore by preferencesDataStore(name = "command_phrases")

/** Voice commands whose trigger phrases the user can extend with their own synonyms, on top of
 * the hardcoded defaults in BaeminRules / NewDispatchCommand / AcceptDispatchCommand /
 * RespondDispatchCommand. [id] is the DataStore key suffix - never reuse one for a different
 * command, or users' saved phrases would silently jump to the wrong command. */
enum class EditableCommand(val id: String, val defaultPhrases: List<String>) {
    START_WORK("baemin_start_work", listOf("운행 시작", "운행시작", "시작")),
    NEW_DISPATCH_ON("baemin_new_dispatch_on", listOf("신규배차 켜줘", "배차 켜")),
    NEW_DISPATCH_OFF("baemin_new_dispatch_off", listOf("신규배차 꺼줘", "배차 꺼")),
    ACCEPT_DISPATCH("baemin_accept_dispatch", listOf("배달확인")),
    RESPOND_ACCEPT("baemin_respond_accept", listOf("수락", "배차수락")),
    RESPOND_REJECT("baemin_respond_reject", listOf("거절")),
}

/** User-added extra trigger phrases per [EditableCommand], layered on top of its hardcoded
 * defaults - never replacing them, so a bad edit can't break the command entirely. */
class CommandPhraseRepository(private val context: Context) {
    private fun key(command: EditableCommand) = stringSetPreferencesKey("extra_${command.id}")

    fun extraPhrases(command: EditableCommand): Flow<Set<String>> =
        context.commandPhrasesDataStore.data.map { prefs -> prefs[key(command)] ?: emptySet() }

    suspend fun extraPhrasesSnapshot(command: EditableCommand): Set<String> = extraPhrases(command).first()

    suspend fun addPhrase(command: EditableCommand, phrase: String) {
        val trimmed = phrase.trim()
        if (trimmed.isEmpty()) return
        context.commandPhrasesDataStore.edit { prefs ->
            prefs[key(command)] = (prefs[key(command)] ?: emptySet()) + trimmed
        }
    }

    suspend fun removePhrase(command: EditableCommand, phrase: String) {
        context.commandPhrasesDataStore.edit { prefs ->
            prefs[key(command)] = (prefs[key(command)] ?: emptySet()) - phrase
        }
    }

    suspend fun editPhrase(command: EditableCommand, oldPhrase: String, newPhrase: String) {
        val trimmed = newPhrase.trim()
        if (trimmed.isEmpty() || trimmed == oldPhrase) return
        context.commandPhrasesDataStore.edit { prefs ->
            prefs[key(command)] = (prefs[key(command)] ?: emptySet()) - oldPhrase + trimmed
        }
    }
}
