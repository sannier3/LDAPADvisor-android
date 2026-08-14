package com.jbsan.ldapadvisor.domain.model

/**
 * Connection profile without secrets. Passwords live only in [com.jbsan.ldapadvisor.core.security.SecretStore].
 */
data class ConnectionProfile(
    val id: String,
    val name: String,
    val directoryType: DirectoryType = DirectoryType.AUTO,
    val domain: String = "",
    val host: String,
    val port: Int,
    val securityMode: SecurityMode = SecurityMode.LDAPS,
    val authMethod: AuthMethod = AuthMethod.SIMPLE,
    val bindIdentity: String = "",
    val baseDn: String = "",
    val connectTimeoutMs: Int = 5_000,
    val readTimeoutMs: Int = 10_000,
    val followReferrals: Boolean = false,
    val trustMode: TrustMode = TrustMode.SYSTEM,
    val customCaId: String? = null,
    val pinnedFingerprint: String? = null,
    val rememberPassword: Boolean = false,
    val readOnly: Boolean = true,
    /** Kerberos realm, e.g. CORP.EXAMPLE.COM */
    val kerberosRealm: String = "",
    /** KDC host (often a DC FQDN). Defaults to [host] when blank. */
    val kerberosKdcHost: String = "",
    val kerberosKdcPort: Int = 88,
    /** Service principal for LDAP, e.g. ldap/dc.corp.example.com — defaults to ldap/[host]. */
    val kerberosServicePrincipal: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val lastSuccessfulConnectionAt: Long? = null,
)
