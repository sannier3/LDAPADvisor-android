package com.jbsan.ldapadvisor.data.repository

import android.util.Base64
import com.jbsan.ldapadvisor.data.database.dao.CustomCaDao
import com.jbsan.ldapadvisor.data.database.entity.CustomCaEntity
import com.jbsan.ldapadvisor.data.tls.CertificateParser
import com.jbsan.ldapadvisor.domain.model.AppError
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CustomCaRepository(
    private val customCaDao: CustomCaDao,
) {
    fun observeAll(): Flow<List<CustomCaEntity>> = customCaDao.observeAll()

    suspend fun getById(id: String): CustomCaEntity? = customCaDao.getById(id)

    suspend fun importFromBytes(alias: String, bytes: ByteArray): Result<CustomCaEntity> {
        val trimmedAlias = alias.trim()
        if (trimmedAlias.isBlank()) {
            return Result.failure(AppError.Validation("CA alias is required"))
        }
        if (bytesLooksLikePrivateKey(bytes)) {
            return Result.failure(AppError.Validation("File appears to contain a private key; only CA certificates are accepted"))
        }
        val parsed = CertificateParser.parsePemOrDer(bytes).getOrElse { return Result.failure(it) }
        val entity = CustomCaEntity(
            id = UUID.randomUUID().toString(),
            alias = trimmedAlias,
            subject = parsed.subject,
            issuer = parsed.issuer,
            serialNumber = parsed.serialNumber,
            notBeforeEpochMs = parsed.notBeforeEpochMs,
            notAfterEpochMs = parsed.notAfterEpochMs,
            sha256Fingerprint = parsed.sha256Fingerprint,
            pemOrDerBase64 = Base64.encodeToString(parsed.certificate.encoded, Base64.NO_WRAP),
            createdAt = System.currentTimeMillis(),
        )
        customCaDao.upsert(entity)
        return Result.success(entity)
    }

    suspend fun delete(id: String) {
        customCaDao.deleteById(id)
    }

    companion object {
        fun bytesLooksLikePrivateKey(bytes: ByteArray): Boolean {
            val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return false
            return text.contains("PRIVATE KEY", ignoreCase = true)
        }
    }
}
