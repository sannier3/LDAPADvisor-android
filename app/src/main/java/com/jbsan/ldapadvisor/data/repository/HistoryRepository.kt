package com.jbsan.ldapadvisor.data.repository

import com.jbsan.ldapadvisor.data.database.dao.DiagnosticRunDao
import com.jbsan.ldapadvisor.data.database.dao.ReportMetaDao
import com.jbsan.ldapadvisor.data.database.entity.DiagnosticRunEntity
import com.jbsan.ldapadvisor.data.database.entity.ReportMetaEntity
import com.jbsan.ldapadvisor.domain.model.DiagnosticRun
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HistoryRepository(
    private val diagnosticRunDao: DiagnosticRunDao,
    private val reportMetaDao: ReportMetaDao,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun observeDiagnosticRuns(): Flow<List<DiagnosticRunEntity>> = diagnosticRunDao.observeAll()

    fun observeReportMeta(): Flow<List<ReportMetaEntity>> = reportMetaDao.observeAll()

    suspend fun saveDiagnosticRun(run: DiagnosticRun) {
        diagnosticRunDao.upsert(
            DiagnosticRunEntity(
                id = run.id,
                profileId = run.profileId,
                startedAt = run.startedAt,
                completedAt = run.completedAt,
                summaryJson = json.encodeToString(run.summary),
                testsJson = json.encodeToString(run.tests),
                findingsJson = json.encodeToString(run.findings),
            ),
        )
    }

    suspend fun getLatestDiagnosticRun(): DiagnosticRun? {
        val entity = diagnosticRunDao.observeAll().first().firstOrNull() ?: return null
        return DiagnosticRun(
            id = entity.id,
            profileId = entity.profileId,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            summary = json.decodeFromString(entity.summaryJson),
            tests = json.decodeFromString(entity.testsJson),
            findings = json.decodeFromString(entity.findingsJson),
        )
    }

    suspend fun getDiagnosticRun(id: String): DiagnosticRun? {
        val entity = diagnosticRunDao.getById(id) ?: return null
        return DiagnosticRun(
            id = entity.id,
            profileId = entity.profileId,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            summary = json.decodeFromString(entity.summaryJson),
            tests = json.decodeFromString(entity.testsJson),
            findings = json.decodeFromString(entity.findingsJson),
        )
    }

    suspend fun deleteDiagnosticRun(id: String) = diagnosticRunDao.deleteById(id)

    suspend fun clearDiagnosticHistory() = diagnosticRunDao.clearAll()

    suspend fun saveReportMeta(entity: ReportMetaEntity) = reportMetaDao.upsert(entity)

    suspend fun deleteReportMeta(id: String) = reportMetaDao.deleteById(id)

    suspend fun clearReports() = reportMetaDao.clearAll()

    suspend fun pruneOlderThan(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24L * 60L * 60L * 1000L
        diagnosticRunDao.deleteOlderThan(cutoff)
        reportMetaDao.deleteOlderThan(cutoff)
    }
}
