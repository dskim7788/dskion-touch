package com.dskion.touch.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dskion.touch.data.CommandPhraseRepository
import com.dskion.touch.data.ModeRepository
import com.dskion.touch.data.MonitoredAppsRepository
import com.dskion.touch.ui.commands.CommandsViewModel
import com.dskion.touch.ui.home.HomeViewModel

class AppViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    private val modeRepository by lazy { ModeRepository(application) }
    private val commandPhraseRepository by lazy { CommandPhraseRepository(application) }
    private val monitoredAppsRepository by lazy { MonitoredAppsRepository(application) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        HomeViewModel::class.java -> HomeViewModel(modeRepository, monitoredAppsRepository) as T
        CommandsViewModel::class.java -> CommandsViewModel(commandPhraseRepository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
