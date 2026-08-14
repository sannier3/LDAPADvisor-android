package com.jbsan.ldapadvisor.core.util

import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate

object FingerprintUtils {

    private val COLON_HEX = Regex("(?i)^([0-9a-f]{2}:)*[0-9a-f]{2}$")
    private val PLAIN_HEX = Regex("(?i)^[0-9a-f]{64}$")

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Colon(bytes: ByteArray): String =
        HexUtils.toHex(sha256(bytes), separator = ":")

    fun certificateSha256Colon(certificate: Certificate): String =
        sha256Colon(certificate.encoded)

    fun x509Sha256Colon(certificate: X509Certificate): String =
        certificateSha256Colon(certificate)

    fun normalizeSha256Fingerprint(value: String): String? {
        val trimmed = value.trim()
        return when {
            COLON_HEX.matches(trimmed) && trimmed.replace(":", "").length == 64 ->
                trimmed.lowercase()
            PLAIN_HEX.matches(trimmed) ->
                HexUtils.toHex(HexUtils.fromHex(trimmed), separator = ":")
            else -> null
        }
    }

    fun isValidSha256Fingerprint(value: String): Boolean =
        normalizeSha256Fingerprint(value) != null
}
