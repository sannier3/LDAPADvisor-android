package com.jbsan.ldapadvisor.domain.model

/**
 * Mapped application errors for network, TLS, and LDAP failures.
 */
sealed class AppError : Exception() {
    abstract override val message: String
    abstract val technicalDetails: String?

    data class UnknownHost(
        override val message: String = "Host could not be resolved",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class DnsTimeout(
        override val message: String = "DNS resolution timed out",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class SocketTimeout(
        override val message: String = "Connection timed out",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class ConnectionRefused(
        override val message: String = "Connection refused",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class ConnectionReset(
        override val message: String = "Connection reset",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class TlsHandshakeFailure(
        override val message: String = "TLS handshake failed",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class CertificateExpired(
        override val message: String = "Server certificate has expired",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class CertificateNotYetValid(
        override val message: String = "Server certificate is not yet valid",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class CertificateUntrusted(
        override val message: String = "Server certificate is not trusted",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class HostnameMismatch(
        override val message: String = "Hostname does not match certificate",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapInvalidCredentials(
        override val message: String = "Invalid credentials",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapUnavailable(
        override val message: String = "LDAP service unavailable",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapStrongAuthRequired(
        override val message: String = "Strong authentication required",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapInsufficientAccess(
        override val message: String = "Insufficient access rights",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapConstraintViolation(
        override val message: String = "Constraint violation",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapObjectNotFound(
        override val message: String = "Object not found",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapUnwillingToPerform(
        override val message: String = "Server unwilling to perform the operation",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapProtocolError(
        override val message: String = "LDAP protocol error",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapSizeLimitExceeded(
        override val message: String = "Size limit exceeded",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class LdapTimeLimitExceeded(
        override val message: String = "Time limit exceeded",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class ReadOnlyViolation(
        override val message: String = "Operation blocked by read-only mode",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class PlaintextBindRequiresConfirmation(
        override val message: String = "Simple bind over plaintext LDAP requires explicit confirmation",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class SecureChannelRequired(
        override val message: String =
            "A secure channel is required (LDAPS, StartTLS, or Kerberos SASL bind)",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class KerberosFailure(
        override val message: String = "Kerberos authentication failed",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class InsecureTrustRequiresConfirmation(
        override val message: String =
            "INSECURE_NO_VERIFY trust skips certificate validation; confirm explicitly to send credentials",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class NotConnected(
        override val message: String = "Not connected to a directory",
        override val technicalDetails: String? = null,
    ) : AppError()

    data class Validation(
        override val message: String,
        override val technicalDetails: String? = null,
    ) : AppError()

    data class Generic(
        override val message: String,
        override val technicalDetails: String? = null,
        override val cause: Throwable? = null,
    ) : AppError()
}
