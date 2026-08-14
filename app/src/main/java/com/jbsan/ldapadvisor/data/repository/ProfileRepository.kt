package com.jbsan.ldapadvisor.data.repository

import com.jbsan.ldapadvisor.core.security.SecretStore
import com.jbsan.ldapadvisor.core.util.DnUtils
import com.jbsan.ldapadvisor.core.util.FingerprintUtils
import com.jbsan.ldapadvisor.data.database.ProfileMapper
import com.jbsan.ldapadvisor.data.database.dao.ProfileDao
import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.TrustMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val secretStore: SecretStore,
) {
    fun observeProfiles(): Flow<List<ConnectionProfile>> =
        profileDao.observeAll().map { list -> list.map(ProfileMapper::toDomain) }

    suspend fun getAll(): List<ConnectionProfile> =
        profileDao.getAll().map(ProfileMapper::toDomain)

    suspend fun getById(id: String): ConnectionProfile? =
        profileDao.getById(id)?.let(ProfileMapper::toDomain)

    suspend fun save(profile: ConnectionProfile): Result<ConnectionProfile> {
        validate(profile).onFailure { return Result.failure(it) }
        val now = System.currentTimeMillis()
        val toSave = if (profile.createdAt == 0L) {
            profile.copy(createdAt = now, updatedAt = now)
        } else {
            profile.copy(updatedAt = now)
        }
        profileDao.upsert(ProfileMapper.toEntity(toSave))
        if (!toSave.rememberPassword) {
            secretStore.deletePassword(toSave.id)
        }
        return Result.success(toSave)
    }

    suspend fun delete(id: String) {
        profileDao.deleteById(id)
        secretStore.deletePassword(id)
    }

    suspend fun duplicate(id: String): Result<ConnectionProfile> {
        val source = getById(id) ?: return Result.failure(
            IllegalArgumentException("Profile not found: $id"),
        )
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (copy)",
            rememberPassword = false,
            lastSuccessfulConnectionAt = null,
            createdAt = now,
            updatedAt = now,
        )
        return save(copy)
    }

    suspend fun markLastSuccessfulConnection(id: String, epochMs: Long = System.currentTimeMillis()) {
        profileDao.updateLastSuccessfulConnection(id, epochMs)
    }

    fun validate(profile: ConnectionProfile): Result<Unit> {
        if (profile.name.isBlank()) {
            return Result.failure(AppError.Validation("Profile name is required"))
        }
        if (profile.host.isBlank()) {
            return Result.failure(AppError.Validation("Host is required"))
        }
        if (profile.port !in 1..65535) {
            return Result.failure(AppError.Validation("Port must be between 1 and 65535"))
        }
        if (profile.connectTimeoutMs !in 500..120_000) {
            return Result.failure(AppError.Validation("Connect timeout out of range"))
        }
        if (profile.readTimeoutMs !in 500..300_000) {
            return Result.failure(AppError.Validation("Read timeout out of range"))
        }
        if (!DnUtils.isPlausibleDn(profile.baseDn)) {
            return Result.failure(AppError.Validation("Base DN syntax is invalid"))
        }
        if (profile.trustMode == TrustMode.PINNED) {
            val fp = profile.pinnedFingerprint
            if (fp.isNullOrBlank() || !FingerprintUtils.isValidSha256Fingerprint(fp)) {
                return Result.failure(AppError.Validation("Pinned SHA-256 fingerprint is invalid"))
            }
        }
        if (profile.trustMode == TrustMode.CUSTOM_CA && profile.customCaId.isNullOrBlank()) {
            return Result.failure(AppError.Validation("Custom CA is required for CUSTOM_CA trust mode"))
        }
        return Result.success(Unit)
    }
}
