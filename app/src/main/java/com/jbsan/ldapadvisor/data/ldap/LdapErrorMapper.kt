package com.jbsan.ldapadvisor.data.ldap

import com.jbsan.ldapadvisor.domain.model.AppError
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.ResultCode
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

object LdapErrorMapper {
    fun map(throwable: Throwable): AppError {
        var t: Throwable? = throwable
        while (t != null) {
            when (t) {
                is AppError -> return t
                is UnknownHostException -> return AppError.UnknownHost(technicalDetails = t.message)
                is SocketTimeoutException -> return AppError.SocketTimeout(technicalDetails = t.message)
                is ConnectException -> return AppError.ConnectionRefused(technicalDetails = t.message)
                is SSLPeerUnverifiedException -> return AppError.HostnameMismatch(technicalDetails = t.message)
                is SSLHandshakeException -> return mapSsl(t)
                is SSLException -> return mapSsl(t)
                is CertificateException -> return mapCert(t)
                is LDAPException -> return mapLdap(t)
            }
            t = t.cause
        }
        return AppError.Generic(
            message = throwable.message ?: "Unexpected error",
            technicalDetails = throwable::class.java.name,
            cause = throwable,
        )
    }

    private fun mapSsl(t: Throwable): AppError {
        val msg = (t.message ?: "").lowercase()
        return when {
            "expired" in msg -> AppError.CertificateExpired(technicalDetails = t.message)
            "not yet valid" in msg || "notvalidyet" in msg.replace(" ", "") ->
                AppError.CertificateNotYetValid(technicalDetails = t.message)
            "hostname" in msg || "mismatch" in msg ->
                AppError.HostnameMismatch(technicalDetails = t.message)
            "trust" in msg || "path" in msg || "PKIX" in (t.message ?: "") ->
                AppError.CertificateUntrusted(technicalDetails = t.message)
            else -> AppError.TlsHandshakeFailure(technicalDetails = t.message)
        }
    }

    private fun mapCert(t: CertificateException): AppError {
        val msg = (t.message ?: "").lowercase()
        return when {
            "expired" in msg -> AppError.CertificateExpired(technicalDetails = t.message)
            "not yet valid" in msg -> AppError.CertificateNotYetValid(technicalDetails = t.message)
            "pin" in msg || "mismatch" in msg -> AppError.CertificateUntrusted(technicalDetails = t.message)
            else -> AppError.CertificateUntrusted(technicalDetails = t.message)
        }
    }

    private fun mapLdap(ex: LDAPException): AppError {
        val details = "${ex.resultCode} ${ex.diagnosticMessage ?: ex.message}"
        return when (ex.resultCode) {
            ResultCode.INVALID_CREDENTIALS -> AppError.LdapInvalidCredentials(technicalDetails = details)
            ResultCode.UNAVAILABLE, ResultCode.BUSY, ResultCode.SERVER_DOWN ->
                AppError.LdapUnavailable(technicalDetails = details)
            ResultCode.STRONG_AUTH_REQUIRED, ResultCode.CONFIDENTIALITY_REQUIRED,
            ResultCode.AUTH_METHOD_NOT_SUPPORTED,
            -> AppError.LdapStrongAuthRequired(technicalDetails = details)
            ResultCode.INSUFFICIENT_ACCESS_RIGHTS -> AppError.LdapInsufficientAccess(technicalDetails = details)
            ResultCode.CONSTRAINT_VIOLATION -> AppError.LdapConstraintViolation(
                message = "Constraint violation${ex.diagnosticMessage?.let { ": $it" }.orEmpty()}",
                technicalDetails = details,
            )
            ResultCode.NO_SUCH_OBJECT -> AppError.LdapObjectNotFound(technicalDetails = details)
            ResultCode.UNWILLING_TO_PERFORM -> AppError.LdapUnwillingToPerform(
                message = "Server unwilling to perform the operation${ex.diagnosticMessage?.let { ": $it" }.orEmpty()}",
                technicalDetails = details,
            )
            ResultCode.PROTOCOL_ERROR -> AppError.LdapProtocolError(technicalDetails = details)
            ResultCode.SIZE_LIMIT_EXCEEDED -> AppError.LdapSizeLimitExceeded(technicalDetails = details)
            ResultCode.TIME_LIMIT_EXCEEDED -> AppError.LdapTimeLimitExceeded(technicalDetails = details)
            ResultCode.CONNECT_ERROR -> AppError.ConnectionRefused(technicalDetails = details)
            ResultCode.TIMEOUT -> AppError.SocketTimeout(technicalDetails = details)
            else -> AppError.Generic(message = "LDAP error: ${ex.resultCode}", technicalDetails = details, cause = ex)
        }
    }
}
