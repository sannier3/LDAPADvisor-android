package com.jbsan.ldapadvisor.data.repository

import com.jbsan.ldapadvisor.data.datastore.SettingsDataStore
import com.jbsan.ldapadvisor.domain.model.AppSettings
import com.jbsan.ldapadvisor.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val settingsDataStore: SettingsDataStore,
) {
    val settings: Flow<AppSettings> = settingsDataStore.settings

    suspend fun setThemeMode(mode: ThemeMode) {
        settingsDataStore.update { it.copy(themeMode = mode) }
    }

    suspend fun setReadOnlyByDefault(value: Boolean) {
        settingsDataStore.update { it.copy(readOnlyByDefault = value) }
    }

    suspend fun setDefaultConnectTimeoutMs(value: Int) {
        settingsDataStore.update { it.copy(defaultConnectTimeoutMs = value.coerceIn(500, 120_000)) }
    }

    suspend fun setDefaultReadTimeoutMs(value: Int) {
        settingsDataStore.update { it.copy(defaultReadTimeoutMs = value.coerceIn(500, 300_000)) }
    }

    suspend fun setDiagnosticConcurrency(value: Int) {
        settingsDataStore.update { it.copy(diagnosticConcurrency = value.coerceIn(1, 16)) }
    }

    suspend fun setReportSanitizationDefault(value: Boolean) {
        settingsDataStore.update { it.copy(reportSanitizationDefault = value) }
    }

    suspend fun setHistoryRetentionDays(value: Int) {
        settingsDataStore.update { it.copy(historyRetentionDays = value.coerceIn(1, 365)) }
    }

    suspend fun setSaveSearchHistory(value: Boolean) {
        settingsDataStore.update { it.copy(saveSearchHistory = value) }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        settingsDataStore.update(transform)
    }
}
