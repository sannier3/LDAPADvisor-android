package com.jbsan.ldapadvisor.domain.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val readOnlyByDefault: Boolean = true,
    val defaultConnectTimeoutMs: Int = 5_000,
    val defaultReadTimeoutMs: Int = 10_000,
    val diagnosticConcurrency: Int = 4,
    val diagnosticTcpTimeoutMs: Int = 3_000,
    val reportSanitizationDefault: Boolean = true,
    val historyRetentionDays: Int = 30,
    val saveSearchHistory: Boolean = true,
)
