package com.dskion.touch.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dskion.touch.data.ManagerPersona
import com.dskion.touch.data.ManagerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val managerRepository: ManagerRepository) : ViewModel() {

    val persona: StateFlow<ManagerPersona> = managerRepository.persona
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManagerPersona.FEMALE)

    val voiceChatEnabled: StateFlow<Boolean> = managerRepository.voiceChatEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setPersona(persona: ManagerPersona) {
        viewModelScope.launch { managerRepository.setPersona(persona) }
    }

    fun setVoiceChatEnabled(enabled: Boolean) {
        viewModelScope.launch { managerRepository.setVoiceChatEnabled(enabled) }
    }
}
