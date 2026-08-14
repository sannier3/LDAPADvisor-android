package com.jbsan.ldapadvisor.data.kerberos

import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import org.apache.kerby.kerberos.kerb.KrbException
import org.apache.kerby.kerberos.kerb.client.KrbClient
import org.apache.kerby.kerberos.kerb.request.ApRequest
import org.apache.kerby.kerberos.kerb.type.ap.ApOption
import java.util.EnumSet

data class KerberosBindTokens(
    val principal: String,
    val servicePrincipal: String,
    /** InitialContextToken for SASL mechanism GSS-SPNEGO */
    val spnegoToken: ByteArray,
    /** InitialContextToken for SASL mechanism GSSAPI (Kerberos V5 mech) */
    val gssapiToken: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KerberosBindTokens) return false
        return principal == other.principal &&
            servicePrincipal == other.servicePrincipal &&
            spnegoToken.contentEquals(other.spnegoToken) &&
            gssapiToken.contentEquals(other.gssapiToken)
    }

    override fun hashCode(): Int {
        var result = principal.hashCode()
        result = 31 * result + servicePrincipal.hashCode()
        result = 31 * result + spnegoToken.contentHashCode()
        result = 31 * result + gssapiToken.contentHashCode()
        return result
    }
}

/**
 * Embedded Kerberos client (Apache Kerby) that obtains a TGT + LDAP service ticket
 * and builds SASL GSS tokens without relying on Android OS Kerberos or JDK GSS.
 */
class KerberosTicketService {
    fun acquireLdapBindTokens(
        profile: ConnectionProfile,
        password: CharArray,
    ): Result<KerberosBindTokens> {
        val realm = profile.kerberosRealm.ifBlank {
            profile.domain.trim().uppercase()
        }.uppercase()
        if (realm.isBlank()) {
            return Result.failure(
                AppError.Validation("Kerberos realm is required (or set the AD domain)"),
            )
        }
        val kdcHost = profile.kerberosKdcHost.ifBlank { profile.host }.trim()
        if (kdcHost.isBlank()) {
            return Result.failure(AppError.Validation("Kerberos KDC host is required"))
        }
        val kdcPort = profile.kerberosKdcPort.takeIf { it in 1..65535 } ?: 88
        val principal = normalizePrincipal(profile.bindIdentity, realm)
        if (principal.isBlank()) {
            return Result.failure(
                AppError.Validation("Kerberos principal is required (user@REALM)"),
            )
        }
        val servicePrincipal = profile.kerberosServicePrincipal.ifBlank {
            "ldap/${profile.host.trim().lowercase()}"
        }.trim()

        return try {
            val client = KrbClient()
            client.setKdcRealm(realm)
            client.setKdcHost(kdcHost)
            client.setKdcTcpPort(kdcPort)
            client.setKdcUdpPort(kdcPort)
            // Prefer TCP on mobile networks; UDP often blocked.
            client.setAllowTcp(true)
            client.setAllowUdp(true)
            client.setTimeout(profile.connectTimeoutMs.coerceAtLeast(1_000))
            client.init()

            val passwordString = String(password)
            val tgt = try {
                client.requestTgt(principal, passwordString)
            } finally {
                // Best-effort: String is immutable; caller still zeros the CharArray.
            }
            val sgt = client.requestSgt(tgt, servicePrincipal)
            val clientPrincipal = tgt.clientPrincipal
                ?: sgt.clientPrincipal
                ?: error("Missing client principal on ticket")
            // Match Kerby GSS initiator: include session/subkey options.
            val apReq = ApRequest(
                clientPrincipal,
                sgt,
                EnumSet.of(ApOption.USE_SESSION_KEY),
            ).apReq
            val apReqDer = apReq.encode()
            val gssapi = GssTokenCodec.wrapKerberosApReq(apReqDer)
            val spnego = GssTokenCodec.wrapSpnegoFromApReq(apReqDer)
            Result.success(
                KerberosBindTokens(
                    principal = principal,
                    servicePrincipal = servicePrincipal,
                    spnegoToken = spnego,
                    gssapiToken = gssapi,
                ),
            )
        } catch (e: KrbException) {
            Result.failure(
                AppError.KerberosFailure(
                    message = e.message?.takeIf { it.isNotBlank() }
                        ?: "Kerberos authentication failed",
                    technicalDetails = e.toString(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(
                AppError.KerberosFailure(
                    message = e.message?.takeIf { it.isNotBlank() }
                        ?: "Kerberos token generation failed",
                    technicalDetails = e.toString(),
                ),
            )
        }
    }

    companion object {
        fun normalizePrincipal(bindIdentity: String, realm: String): String {
            val raw = bindIdentity.trim()
            if (raw.isEmpty()) return ""
            return if (raw.contains('@')) {
                val parts = raw.split('@', limit = 2)
                "${parts[0]}@${parts.getOrElse(1) { realm }.uppercase()}"
            } else {
                "$raw@${realm.uppercase()}"
            }
        }
    }
}
