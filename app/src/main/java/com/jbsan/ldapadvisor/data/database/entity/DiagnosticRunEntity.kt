package com.jbsan.ldapadvisor.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_runs")
data class DiagnosticRunEntity(
    @PrimaryKey val id: String,
    val profileId: String?,
    val startedAt: Long,
    val completedAt: Long?,
    val summaryJson: String,
    val testsJson: String,
    val findingsJson: String,
)
