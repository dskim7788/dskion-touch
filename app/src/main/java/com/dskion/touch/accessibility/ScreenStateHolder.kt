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

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun updateSnapshot(snapshot: ScreenSnapshot) {
        _latestSnapshot.value = snapshot
    }
}
