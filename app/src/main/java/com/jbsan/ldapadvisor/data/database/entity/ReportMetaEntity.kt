package com.jbsan.ldapadvisor.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "report_meta")
data class ReportMetaEntity(
    @PrimaryKey val id: String,
    val diagnosticRunId: String?,
    val profileId: String?,
    val title: String,
    val format: String,
    val sanitized: Boolean,
    val createdAt: Long,
    val fileName: String?,
)
