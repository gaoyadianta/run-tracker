package com.sdevprem.runtrack.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdevprem.runtrack.ai.manager.AIRunningCompanionManager
import com.sdevprem.runtrack.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    userRepository: UserRepository,
    aiRunningCompanionManager: AIRunningCompanionManager
) : ViewModel() {
    init {
        aiRunningCompanionManager.tryAutoStartNewsOnAppOpen()
    }

    val doesUserExist = userRepository.doesUserExist
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            null
        )
}
