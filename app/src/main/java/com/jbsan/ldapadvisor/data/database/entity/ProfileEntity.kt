package com.jbsan.ldapadvisor.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val directoryType: String,
    val domain: String,
    val host: String,
    val port: Int,
    val securityMode: String,
    val authMethod: String = "SIMPLE",
    val bindIdentity: String,
    val baseDn: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val followReferrals: Boolean,
    val trustMode: String,
    val customCaId: String?,
    val pinnedFingerprint: String?,
    val rememberPassword: Boolean,
    val readOnly: Boolean,
    val kerberosRealm: String = "",
    val kerberosKdcHost: String = "",
    val kerberosKdcPort: Int = 88,
    val kerberosServicePrincipal: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val lastSuccessfulConnectionAt: Long?,
)
