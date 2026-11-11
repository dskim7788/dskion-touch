package com.dskion.touch.ui.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dskion.touch.accessibility.ScreenStateHolder
import com.dskion.touch.core.model.ScreenSnapshot
import com.dskion.touch.data.ModeRepository
import com.dskion.touch.data.MonitoredAppsRepository
import com.dskion.touch.data.OperatingMode
import com.dskion.touch.util.isAccessibilityServiceEnabled
import com.dskion.touch.util.isBatteryOptimizationIgnored
import com.dskion.touch.util.isMicGranted
import com.dskion.touch.util.isOverlayGranted
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val micGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryIgnored: Boolean = false,
)

class HomeViewModel(
    private val modeRepository: ModeRepository,
    private val monitoredAppsRepository: MonitoredAppsRepository,
) : ViewModel() {

    var permissionState by mutableStateOf(PermissionState())
        private set

    val mode: StateFlow<OperatingMode> = modeRepository.mode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OperatingMode.NORMAL)

    val monitoredPackages: StateFlow<Set<String>> = monitoredAppsRepository.monitoredPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonitoredAppsRepository.DEFAULT_PACKAGES)

    val overlayEnabled: StateFlow<Boolean> = modeRepository.overlayEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val serviceRunning: StateFlow<Boolean> = ScreenStateHolder.serviceRunning
    val latestSnapshot: StateFlow<ScreenSnapshot?> = ScreenStateHolder.latestSnapshot
    val voiceStatus: StateFlow<String?> = ScreenStateHolder.voiceStatus

    /** Permission grants can only change while the user is in system Settings, so this is
     * called from the screen's onResume rather than observed reactively. */
    fun refreshPermissions(context: Context) {
        permissionState = PermissionState(
            micGranted = isMicGranted(context),
            accessibilityGranted = isAccessibilityServiceEnabled(context),
            overlayGranted = isOverlayGranted(context),
            batteryIgnored = isBatteryOptimizationIgnored(context),
        )
    }

    fun setMode(mode: OperatingMode) {
        viewModelScope.launch { modeRepository.setMode(mode) }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { modeRepository.setOverlayEnabled(enabled) }
    }

    fun addMonitoredApp(packageName: String) {
        viewModelScope.launch { monitoredAppsRepository.addPackage(packageName) }
    }

    fun removeMonitoredApp(packageName: String) {
        viewModelScope.launch { monitoredAppsRepository.removePackage(packageName) }
    }
}
