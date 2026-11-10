package com.dskion.touch.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dskion.touch.data.CommandPhraseRepository
import com.dskion.touch.data.EditableCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CommandsViewModel(private val repository: CommandPhraseRepository) : ViewModel() {
    fun extraPhrases(command: EditableCommand): Flow<Set<String>> = repository.extraPhrases(command)

    fun addPhrase(command: EditableCommand, phrase: String) {
        viewModelScope.launch { repository.addPhrase(command, phrase) }
    }

    fun removePhrase(command: EditableCommand, phrase: String) {
        viewModelScope.launch { repository.removePhrase(command, phrase) }
    }
}
