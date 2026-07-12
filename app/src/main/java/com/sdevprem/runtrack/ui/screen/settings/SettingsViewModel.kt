package com.sdevprem.runtrack.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdevprem.runtrack.ai.news.config.NewsProgramSettings
import com.sdevprem.runtrack.data.repository.NewsProgramSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val newsSettingsRepository: NewsProgramSettingsRepository
) : ViewModel() {
    val newsSettings: StateFlow<NewsProgramSettings> = newsSettingsRepository.settings

    fun setProgramEnabled(value: Boolean) = launchUpdate { newsSettingsRepository.setEnabled(value) }
    fun setAllowVoiceStart(value: Boolean) = launchUpdate { newsSettingsRepository.setAllowVoiceStart(value) }
    fun setFulltextAuthorized(value: Boolean) = launchUpdate { newsSettingsRepository.setFullTextAuthorized(value) }
    fun setAutoStartOnAppOpen(value: Boolean) = launchUpdate { newsSettingsRepository.setAutoStartOnAppOpen(value) }
    fun setDefaultKeyword(value: String) = launchUpdate { newsSettingsRepository.setDefaultKeyword(value) }
    fun setDefaultLanguage(value: String) = launchUpdate { newsSettingsRepository.setDefaultLanguage(value) }
    fun setNoContentRetryMinutes(value: Int) = launchUpdate { newsSettingsRepository.setNoContentRetryMinutes(value) }
    fun setFeedUrlTemplate(value: String) = launchUpdate { newsSettingsRepository.setFeedUrlTemplate(value) }
    fun setContentUrlTemplate(value: String) = launchUpdate { newsSettingsRepository.setContentUrlTemplate(value) }
    fun setApiKeyHeaderName(value: String) = launchUpdate { newsSettingsRepository.setApiKeyHeaderName(value) }
    fun setApiKeyQueryName(value: String) = launchUpdate { newsSettingsRepository.setApiKeyQueryName(value) }
    fun setApiKeyValue(value: String) = launchUpdate { newsSettingsRepository.setApiKeyValue(value) }
    fun applyNewsApiPreset() = launchUpdate { newsSettingsRepository.applyNewsApiPreset() }
    fun applyLocalMockPreset() = launchUpdate { newsSettingsRepository.applyLocalMockPreset() }

    private fun launchUpdate(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
        }
    }
}
