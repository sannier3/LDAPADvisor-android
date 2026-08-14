package com.jbsan.ldapadvisor.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.logging.AppLogger
import com.jbsan.ldapadvisor.core.logging.LogExport
import com.jbsan.ldapadvisor.data.repository.SettingsRepository
import com.jbsan.ldapadvisor.domain.model.AppSettings
import com.jbsan.ldapadvisor.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val logger: AppLogger,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setReadOnlyDefault(v: Boolean) = viewModelScope.launch { settingsRepository.setReadOnlyByDefault(v) }
    fun setConnectTimeout(v: Int) = viewModelScope.launch { settingsRepository.setDefaultConnectTimeoutMs(v) }
    fun setReadTimeout(v: Int) = viewModelScope.launch { settingsRepository.setDefaultReadTimeoutMs(v) }
    fun setConcurrency(v: Int) = viewModelScope.launch { settingsRepository.setDiagnosticConcurrency(v) }
    fun setSanitize(v: Boolean) = viewModelScope.launch { settingsRepository.setReportSanitizationDefault(v) }
    fun setRetention(v: Int) = viewModelScope.launch { settingsRepository.setHistoryRetentionDays(v) }
    fun setSaveSearchHistory(v: Boolean) = viewModelScope.launch { settingsRepository.setSaveSearchHistory(v) }
    fun setDebugLogging(v: Boolean) = viewModelScope.launch {
        settingsRepository.setDebugLoggingEnabled(v)
        logger.debugEnabled = v
    }

    fun exportLogs(context: Context) = viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val file = LogExport.writeSnapshot(context, logger)
                withContext(Dispatchers.Main) {
                    LogExport.share(context, file)
                }
            }
            _exportMessage.value = "exported"
        }.onFailure {
            _exportMessage.value = it.message
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }
}
