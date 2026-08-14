package com.jbsan.ldapadvisor.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_certs")
data class TrustedCertEntity(
    @PrimaryKey val id: String,
    val profileId: String?,
    val subject: String,
    val issuer: String,
    val sha256Fingerprint: String,
    val notBeforeEpochMs: Long,
    val notAfterEpochMs: Long,
    val pemBase64: String,
    val createdAt: Long,
)
