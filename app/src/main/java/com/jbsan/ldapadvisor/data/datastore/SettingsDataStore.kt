package com.jbsan.ldapadvisor.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jbsan.ldapadvisor.domain.model.AppSettings
import com.jbsan.ldapadvisor.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ldapadvisor_settings",
)

class SettingsDataStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        prefs.toAppSettings()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toAppSettings())
            prefs[Keys.THEME] = next.themeMode.name
            prefs[Keys.READ_ONLY_BY_DEFAULT] = next.readOnlyByDefault
            prefs[Keys.CONNECT_TIMEOUT] = next.defaultConnectTimeoutMs
            prefs[Keys.READ_TIMEOUT] = next.defaultReadTimeoutMs
            prefs[Keys.DIAG_CONCURRENCY] = next.diagnosticConcurrency
            prefs[Keys.DIAG_TCP_TIMEOUT] = next.diagnosticTcpTimeoutMs
            prefs[Keys.REPORT_SANITIZE] = next.reportSanitizationDefault
            prefs[Keys.HISTORY_RETENTION] = next.historyRetentionDays
            prefs[Keys.SAVE_SEARCH_HISTORY] = next.saveSearchHistory
            prefs[Keys.DEBUG_LOGGING] = next.debugLoggingEnabled
        }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        themeMode = this[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        readOnlyByDefault = this[Keys.READ_ONLY_BY_DEFAULT] ?: true,
        defaultConnectTimeoutMs = this[Keys.CONNECT_TIMEOUT] ?: 5_000,
        defaultReadTimeoutMs = this[Keys.READ_TIMEOUT] ?: 10_000,
        diagnosticConcurrency = this[Keys.DIAG_CONCURRENCY] ?: 4,
        diagnosticTcpTimeoutMs = this[Keys.DIAG_TCP_TIMEOUT] ?: 3_000,
        reportSanitizationDefault = this[Keys.REPORT_SANITIZE] ?: true,
        historyRetentionDays = this[Keys.HISTORY_RETENTION] ?: 30,
        saveSearchHistory = this[Keys.SAVE_SEARCH_HISTORY] ?: true,
        debugLoggingEnabled = this[Keys.DEBUG_LOGGING] ?: false,
    )

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val READ_ONLY_BY_DEFAULT = booleanPreferencesKey("read_only_by_default")
        val CONNECT_TIMEOUT = intPreferencesKey("default_connect_timeout_ms")
        val READ_TIMEOUT = intPreferencesKey("default_read_timeout_ms")
        val DIAG_CONCURRENCY = intPreferencesKey("diagnostic_concurrency")
        val DIAG_TCP_TIMEOUT = intPreferencesKey("diagnostic_tcp_timeout_ms")
        val REPORT_SANITIZE = booleanPreferencesKey("report_sanitization_default")
        val HISTORY_RETENTION = intPreferencesKey("history_retention_days")
        val SAVE_SEARCH_HISTORY = booleanPreferencesKey("save_search_history")
        val DEBUG_LOGGING = booleanPreferencesKey("debug_logging_enabled")
    }
}
