package com.dskion.touch.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dskion.touch.data.ModeRepository
import com.dskion.touch.ui.home.HomeViewModel

class AppViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    private val modeRepository by lazy { ModeRepository(application) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        HomeViewModel::class.java -> HomeViewModel(modeRepository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
