package com.dskion.touch.ui.console

import androidx.lifecycle.ViewModel
import com.dskion.touch.accessibility.ScreenStateHolder
import com.dskion.touch.core.model.ScreenSnapshot
import kotlinx.coroutines.flow.StateFlow

class ConsoleViewModel : ViewModel() {
    val serviceRunning: StateFlow<Boolean> = ScreenStateHolder.serviceRunning
    val snapshot: StateFlow<ScreenSnapshot?> = ScreenStateHolder.latestSnapshot
    val voiceStatus: StateFlow<String?> = ScreenStateHolder.voiceStatus
}
