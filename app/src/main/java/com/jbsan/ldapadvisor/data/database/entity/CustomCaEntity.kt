package com.jbsan.ldapadvisor.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_cas")
data class CustomCaEntity(
    @PrimaryKey val id: String,
    val alias: String,
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val notBeforeEpochMs: Long,
    val notAfterEpochMs: Long,
    val sha256Fingerprint: String,
    val pemOrDerBase64: String,
    val createdAt: Long,
)
