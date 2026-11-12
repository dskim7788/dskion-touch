package com.dskion.touch.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dskion.touch.data.CommandPhraseRepository
import com.dskion.touch.data.ManagerRepository
import com.dskion.touch.data.ModeRepository
import com.dskion.touch.data.MonitoredAppsRepository
import com.dskion.touch.ui.commands.CommandsViewModel
import com.dskion.touch.ui.home.HomeViewModel
import com.dskion.touch.ui.settings.SettingsViewModel

class AppViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    private val modeRepository by lazy { ModeRepository(application) }
    private val commandPhraseRepository by lazy { CommandPhraseRepository(application) }
    private val monitoredAppsRepository by lazy { MonitoredAppsRepository(application) }
    private val managerRepository by lazy { ManagerRepository(application) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        HomeViewModel::class.java -> HomeViewModel(modeRepository, monitoredAppsRepository, managerRepository) as T
        CommandsViewModel::class.java -> CommandsViewModel(commandPhraseRepository) as T
        SettingsViewModel::class.java -> SettingsViewModel(managerRepository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
