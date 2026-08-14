package com.jbsan.ldapadvisor.data.tls

import com.jbsan.ldapadvisor.core.util.FingerprintUtils
import com.jbsan.ldapadvisor.core.util.HexUtils
import com.jbsan.ldapadvisor.domain.model.AppError
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object CertificateParser {
    data class ParsedCertificate(
        val certificate: X509Certificate,
        val subject: String,
        val issuer: String,
        val serialNumber: String,
        val notBeforeEpochMs: Long,
        val notAfterEpochMs: Long,
        val sha256Fingerprint: String,
        val subjectAlternativeNames: List<String>,
        val publicKeyAlgorithm: String,
        val signatureAlgorithm: String,
        val publicKeySize: Int?,
    )

    fun parsePemOrDer(bytes: ByteArray): Result<ParsedCertificate> = try {
        val factory = CertificateFactory.getInstance("X.509")
        val cert = factory.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        Result.success(toParsed(cert))
    } catch (e: Exception) {
        Result.failure(AppError.Validation("Unable to parse certificate: ${e.message}"))
    }

    fun toParsed(cert: X509Certificate): ParsedCertificate {
        val sans = mutableListOf<String>()
        try {
            cert.subjectAlternativeNames?.forEach { entry ->
                if (entry.size >= 2) {
                    sans += entry[1].toString()
                }
            }
        } catch (_: Exception) {
            // ignore malformed SAN for display purposes
        }
        val keySize = try {
            when (val key = cert.publicKey) {
                is java.security.interfaces.RSAPublicKey -> key.modulus.bitLength()
                is java.security.interfaces.ECPublicKey -> key.params.order.bitLength()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        return ParsedCertificate(
            certificate = cert,
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            serialNumber = HexUtils.toHex(cert.serialNumber.toByteArray(), separator = ":"),
            notBeforeEpochMs = cert.notBefore.time,
            notAfterEpochMs = cert.notAfter.time,
            sha256Fingerprint = FingerprintUtils.x509Sha256Colon(cert),
            subjectAlternativeNames = sans,
            publicKeyAlgorithm = cert.publicKey.algorithm,
            signatureAlgorithm = cert.sigAlgName,
            publicKeySize = keySize,
        )
    }
}
