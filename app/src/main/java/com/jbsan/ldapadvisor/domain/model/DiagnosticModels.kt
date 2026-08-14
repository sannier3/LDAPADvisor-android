package com.jbsan.ldapadvisor.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DiagnosticStatus {
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
    SKIPPED,
    UNSUPPORTED,
    RUNNING,
}

@Serializable
data class DiagnosticTestResult(
    val id: String,
    val category: String,
    val title: String,
    val status: DiagnosticStatus,
    val startedAt: Long,
    val completedAt: Long? = null,
    val durationMs: Long? = null,
    val target: String? = null,
    val summary: String = "",
    val technicalDetails: String? = null,
    val probableCause: String? = null,
    val recommendations: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
)

@Serializable
enum class FindingSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

@Serializable
data class Finding(
    val id: String,
    val severity: FindingSeverity,
    val category: String,
    val title: String,
    val description: String,
    val evidence: List<String> = emptyList(),
    val probableCauses: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val relatedTests: List<String> = emptyList(),
)

@Serializable
data class DiagnosticRunSummary(
    val successCount: Int = 0,
    val warningCount: Int = 0,
    val errorCount: Int = 0,
    val infoCount: Int = 0,
    val skippedCount: Int = 0,
    val unsupportedCount: Int = 0,
    val score: Int? = null,
)

@Serializable
data class DiagnosticRun(
    val id: String,
    val profileId: String?,
    val startedAt: Long,
    val completedAt: Long? = null,
    val tests: List<DiagnosticTestResult> = emptyList(),
    val summary: DiagnosticRunSummary = DiagnosticRunSummary(),
    val findings: List<Finding> = emptyList(),
)
