package com.jbsan.ldapadvisor.data.ldap

import android.util.Base64
import com.jbsan.ldapadvisor.core.logging.AppLogger
import com.jbsan.ldapadvisor.data.database.dao.CustomCaDao
import com.jbsan.ldapadvisor.data.tls.CertificateParser
import com.jbsan.ldapadvisor.data.tls.ProfileSslSocketFactoryFactory
import com.jbsan.ldapadvisor.data.tls.SslSocketFactoryBundle
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.SecurityMode
import com.jbsan.ldapadvisor.domain.model.TrustMode
import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.LDAPConnectionOptions
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.cert.X509Certificate

class LdapClientFactory(
    private val sslFactoryFactory: ProfileSslSocketFactoryFactory = ProfileSslSocketFactoryFactory(),
    private val customCaDao: CustomCaDao? = null,
    private val logger: AppLogger? = null,
) {
    data class OpenedClient(
        val client: LdapClient,
        val tlsActive: Boolean,
        val sslBundle: SslSocketFactoryBundle?,
    )

    suspend fun open(
        profile: ConnectionProfile,
        customCaCertificates: List<X509Certificate> = emptyList(),
    ): Result<OpenedClient> = withContext(Dispatchers.IO) {
        try {
            logger?.debug(
                "LdapClientFactory",
                "Open ${profile.securityMode} ${profile.host}:${profile.port} " +
                    "trust=${profile.trustMode} connectTimeoutMs=${profile.connectTimeoutMs} " +
                    "readTimeoutMs=${profile.readTimeoutMs}",
            )
            val cas = customCaCertificates.ifEmpty {
                loadCustomCas(profile)
            }
            val needsTlsFactory = profile.securityMode == SecurityMode.LDAPS ||
                profile.securityMode == SecurityMode.START_TLS
            val sslBundle = if (needsTlsFactory) {
                logger?.debug("LdapClientFactory", "Building SSL factory trust=${profile.trustMode} cas=${cas.size}")
                sslFactoryFactory.create(
                    trustMode = profile.trustMode,
                    customCaCertificates = cas,
                    pinnedSha256Colon = profile.pinnedFingerprint,
                    hostname = profile.host,
                ).getOrElse { return@withContext Result.failure(it) }
            } else {
                null
            }

            val options = LDAPConnectionOptions().apply {
                connectTimeoutMillis = profile.connectTimeoutMs
                responseTimeoutMillis = profile.readTimeoutMs.toLong()
                setFollowReferrals(profile.followReferrals)
                // Helps detect half-open sockets; LDAP idle keep-alive is handled by SessionManager.
                setUseKeepAlive(true)
                setAbandonOnTimeout(true)
            }

            logger?.debug("LdapClientFactory", "TCP/TLS connect…")
            val connection = when (profile.securityMode) {
                SecurityMode.LDAPS -> {
                    LDAPConnection(sslBundle!!.socketFactory, options, profile.host, profile.port)
                }
                SecurityMode.LDAP -> {
                    LDAPConnection(options, profile.host, profile.port)
                }
                SecurityMode.START_TLS -> {
                    val plainOptions = LDAPConnectionOptions().apply {
                        connectTimeoutMillis = profile.connectTimeoutMs
                        responseTimeoutMillis = profile.readTimeoutMs.toLong()
                        setFollowReferrals(profile.followReferrals)
                        setUseKeepAlive(true)
                        setAbandonOnTimeout(true)
                    }
                    val conn = LDAPConnection(plainOptions, profile.host, profile.port)
                    try {
                        logger?.debug("LdapClientFactory", "StartTLS extended operation…")
                        val startTls = StartTLSExtendedRequest(sslBundle!!.socketFactory)
                        conn.processExtendedOperation(startTls)
                        conn
                    } catch (e: Exception) {
                        conn.close()
                        throw e
                    }
                }
            }

            val tlsActive = profile.securityMode == SecurityMode.LDAPS ||
                profile.securityMode == SecurityMode.START_TLS
            logger?.debug("LdapClientFactory", "Socket ready tlsActive=$tlsActive")
            val client = LdapClient(
                connection = connection,
                profile = profile,
                tlsActive = tlsActive,
                logger = logger,
            )
            Result.success(OpenedClient(client, tlsActive, sslBundle))
        } catch (e: Exception) {
            logger?.error("LdapClientFactory", "Failed to open LDAP connection: ${e.message}", e)
            Result.failure(LdapErrorMapper.map(e))
        }
    }

    private suspend fun loadCustomCas(profile: ConnectionProfile): List<X509Certificate> {
        if (profile.trustMode != TrustMode.CUSTOM_CA) return emptyList()
        val id = profile.customCaId ?: return emptyList()
        val entity = customCaDao?.getById(id) ?: return emptyList()
        val bytes = Base64.decode(entity.pemOrDerBase64, Base64.DEFAULT)
        return CertificateParser.parsePemOrDer(bytes).getOrNull()?.let { listOf(it.certificate) }.orEmpty()
    }
}
