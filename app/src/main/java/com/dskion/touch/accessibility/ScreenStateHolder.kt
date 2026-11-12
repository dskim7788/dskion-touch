package com.dskion.touch.accessibility

import com.dskion.touch.core.model.ScreenSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-process bridge between [TouchAccessibilityService] (system-managed lifecycle) and the
 * Compose UI. Both run in the same process, so a plain StateFlow holder is enough — no
 * bound-service/IPC layer needed.
 */
object ScreenStateHolder {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    private val _latestSnapshot = MutableStateFlow<ScreenSnapshot?>(null)
    val latestSnapshot: StateFlow<ScreenSnapshot?> = _latestSnapshot

    private val _voiceStatus = MutableStateFlow<String?>(null)
    val voiceStatus: StateFlow<String?> = _voiceStatus

    /** True from the moment a voice command starts (countdown/listening) until it finishes
     * acting on the result - drives the floating overlay button's color so it's usable without
     * looking at the Console tab. */
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    /** On/off state of the Home tab's "매니저와 대화하기" continuous chat loop - toggled once,
     * stays on (listen → reply → listen again) until toggled back off, instead of one press per
     * utterance. */
    private val _managerChatActive = MutableStateFlow(false)
    val managerChatActive: StateFlow<Boolean> = _managerChatActive

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun updateSnapshot(snapshot: ScreenSnapshot) {
        _latestSnapshot.value = snapshot
    }

    fun setVoiceStatus(status: String?) {
        _voiceStatus.value = status
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun setManagerChatActive(active: Boolean) {
        _managerChatActive.value = active
    }
}
