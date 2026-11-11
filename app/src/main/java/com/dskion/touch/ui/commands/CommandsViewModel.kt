package com.dskion.touch.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dskion.touch.data.CommandPhraseRepository
import com.dskion.touch.data.EditableCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CommandsViewModel(private val repository: CommandPhraseRepository) : ViewModel() {
    fun extraPhrases(command: EditableCommand): Flow<Set<String>> = repository.extraPhrases(command)

    fun hiddenDefaults(command: EditableCommand): Flow<Set<String>> = repository.hiddenDefaults(command)

    fun addPhrase(command: EditableCommand, phrase: String) {
        viewModelScope.launch { repository.addPhrase(command, phrase) }
    }

    fun removePhrase(command: EditableCommand, phrase: String) {
        viewModelScope.launch { repository.removePhrase(command, phrase) }
    }

    fun editPhrase(command: EditableCommand, oldPhrase: String, newPhrase: String) {
        viewModelScope.launch { repository.editPhrase(command, oldPhrase, newPhrase) }
    }

    fun editDefaultPhrase(command: EditableCommand, oldPhrase: String, newPhrase: String) {
        viewModelScope.launch { repository.editDefaultPhrase(command, oldPhrase, newPhrase) }
    }

    fun removeDefaultPhrase(command: EditableCommand, phrase: String) {
        viewModelScope.launch { repository.removeDefaultPhrase(command, phrase) }
    }
}
