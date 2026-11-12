package com.dskion.touch.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dskion.touch.ai.GeminiTtsClient
import com.dskion.touch.ai.GeminiVoices
import com.dskion.touch.data.AiChatRepository
import com.dskion.touch.data.ManagerPersona
import com.dskion.touch.data.ManagerRepository
import com.dskion.touch.voice.PcmPlayer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val managerRepository: ManagerRepository,
    private val aiChatRepository: AiChatRepository,
) : ViewModel() {

    val persona: StateFlow<ManagerPersona> = managerRepository.persona
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManagerPersona.FEMALE)

    val voiceChatEnabled: StateFlow<Boolean> = managerRepository.voiceChatEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val apiKey: StateFlow<String> = aiChatRepository.apiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val maleVoiceName: StateFlow<String> = managerRepository.maleVoiceName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GeminiVoices.DEFAULT_MALE_VOICE)

    val femaleVoiceName: StateFlow<String> = managerRepository.femaleVoiceName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GeminiVoices.DEFAULT_FEMALE_VOICE)

    fun setPersona(persona: ManagerPersona) {
        viewModelScope.launch { managerRepository.setPersona(persona) }
    }

    fun setVoiceChatEnabled(enabled: Boolean) {
        viewModelScope.launch { managerRepository.setVoiceChatEnabled(enabled) }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { aiChatRepository.setApiKey(key) }
    }

    fun setMaleVoiceName(voiceName: String) {
        viewModelScope.launch { managerRepository.setMaleVoiceName(voiceName) }
    }

    fun setFemaleVoiceName(voiceName: String) {
        viewModelScope.launch { managerRepository.setFemaleVoiceName(voiceName) }
    }

    /** Lets the user hear a voice option before committing to it - uses whatever API key is
     * currently saved, so it only works once a key has been entered (and saved). */
    fun previewVoice(voiceName: String, sampleText: String) {
        viewModelScope.launch {
            val key = apiKey.value
            if (key.isBlank()) return@launch
            val result = GeminiTtsClient.synthesize(key, sampleText, voiceName)
            result.getOrNull()?.let { audio -> PcmPlayer.play(audio.pcm, audio.sampleRateHz) }
        }
    }
}
