package com.jbsan.ldapadvisor.data.kerberos

import com.jbsan.ldapadvisor.core.logging.AppLogger
import com.jbsan.ldapadvisor.data.dns.DnsResolver
import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.kerby.kerberos.kerb.KrbException
import org.apache.kerby.kerberos.kerb.client.KrbClient
import org.apache.kerby.kerberos.kerb.client.KrbConfig
import org.apache.kerby.kerberos.kerb.request.ApRequest
import org.apache.kerby.kerberos.kerb.type.ap.ApOption
import org.apache.kerby.kerberos.kerb.type.ticket.TgtTicket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.EnumSet
import java.util.LinkedHashSet

data class KerberosBindTokens(
    val principal: String,
    val servicePrincipal: String,
    /** InitialContextToken for SASL mechanism GSS-SPNEGO */
    val spnegoToken: ByteArray,
    /** InitialContextToken for SASL mechanism GSSAPI (Kerberos V5 mech) */
    val gssapiToken: ByteArray,
    /** Session key + authenticator clock for mutual AP-REP verification (may be null). */
    val mutualAuth: KerberosMutualAuthState? = null,
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
class KerberosTicketService(
    private val logger: AppLogger? = null,
    private val dnsResolver: DnsResolver = DnsResolver(),
) {
    suspend fun acquireLdapBindTokens(
        profile: ConnectionProfile,
        password: CharArray,
    ): Result<KerberosBindTokens> = withContext(Dispatchers.IO) {
        val realm = profile.kerberosRealm.ifBlank {
            profile.domain.trim().uppercase()
        }.uppercase()
        if (realm.isBlank()) {
            return@withContext Result.failure(
                AppError.Validation("Kerberos realm is required (or set the AD domain)"),
            )
        }
        val kdcHost = profile.kerberosKdcHost.ifBlank { profile.host }.trim()
        if (kdcHost.isBlank()) {
            return@withContext Result.failure(AppError.Validation("Kerberos KDC host is required"))
        }
        val kdcPort = profile.kerberosKdcPort.takeIf { it in 1..65535 } ?: 88
        val principal = normalizePrincipal(profile.bindIdentity, realm)
        if (principal.isBlank()) {
            return@withContext Result.failure(
                AppError.Validation("Kerberos principal is required (user@REALM)"),
            )
        }
        val timeoutMs = profile.connectTimeoutMs.coerceAtLeast(1_000)
        val spnCandidates = resolveServicePrincipalCandidates(profile)
        logger?.debug(
            TAG,
            "AS-REQ start principal=$principal realm=$realm kdc=$kdcHost:$kdcPort " +
                "spnCandidates=$spnCandidates timeoutMs=$timeoutMs",
        )

        val probe = probeTcp(kdcHost, kdcPort, timeoutMs)
        logger?.debug(TAG, "KDC TCP probe $kdcHost:$kdcPort → $probe")
        if (probe is TcpProbeResult.Failure) {
            return@withContext Result.failure(
                AppError.KerberosFailure(
                    message = "Cannot reach Kerberos KDC at $kdcHost:$kdcPort (${probe.reason}). " +
                        "LDAP may work on 389 while TCP/88 is blocked by firewall or Wi‑Fi isolation.",
                    technicalDetails = "tcpProbe=$probe",
                ),
            )
        }

        try {
            val config = buildAdCompatibleConfig()
            val client = KrbClient(config)
            client.setKdcRealm(realm)
            client.setKdcHost(kdcHost)
            client.setKdcTcpPort(kdcPort)
            client.setKdcUdpPort(kdcPort)
            client.setAllowTcp(true)
            client.setAllowUdp(false)
            client.setTimeout(timeoutMs)
            client.init()
            logger?.debug(
                TAG,
                "Kerby client ready etypes=${config.encryptionTypes} " +
                    "tkt=${config.defaultTktEnctypes} tgs=${config.defaultTgsEnctypes}",
            )

            val passwordString = String(password)
            val tgtStarted = System.currentTimeMillis()
            val tgt = try {
                client.requestTgt(principal, passwordString)
            } finally {
                // Best-effort: String is immutable; caller still zeros the CharArray.
            }
            logger?.debug(
                TAG,
                "TGT obtained clientPrincipal=${tgt.clientPrincipal} " +
                    "durationMs=${System.currentTimeMillis() - tgtStarted}",
            )

            val (servicePrincipal, sgt) = requestServiceTicket(client, tgt, spnCandidates)
            logger?.debug(TAG, "SGT obtained for $servicePrincipal")

            val clientPrincipal = tgt.clientPrincipal
                ?: sgt.clientPrincipal
                ?: error("Missing client principal on ticket")
            // Windows LDAP expects mutual authentication (APOptions mutual-required).
            // USE_SESSION_KEY alone commonly yields AcceptSecurityContext data 57 / 52e.
            val apRequest = ApRequest(
                clientPrincipal,
                sgt,
                EnumSet.of(ApOption.MUTUAL_REQUIRED),
            )
            val apReq = apRequest.apReq
            val authenticator = apReq.authenticator
                ?: error("AP-REQ missing authenticator")
            val expectedCtime = authenticator.ctime
                ?: error("AP-REQ authenticator missing ctime")
            val expectedCusec = authenticator.cusec
            val sessionKey = sgt.sessionKey
                ?: error("Service ticket missing session key")
            val mutualAuth = KerberosMutualAuthState(
                sessionKey = sessionKey,
                expectedCtime = expectedCtime,
                expectedCusec = expectedCusec,
            )
            val apReqDer = apReq.encode()
            val gssapi = GssTokenCodec.wrapKerberosApReq(apReqDer)
            val spnego = GssTokenCodec.wrapSpnegoFromApReq(apReqDer)
            logger?.debug(
                TAG,
                "GSS tokens built gssapiBytes=${gssapi.size} spnegoBytes=${spnego.size} " +
                    "apOptions=MUTUAL_REQUIRED",
            )
            Result.success(
                KerberosBindTokens(
                    principal = principal,
                    servicePrincipal = servicePrincipal,
                    spnegoToken = spnego,
                    gssapiToken = gssapi,
                    mutualAuth = mutualAuth,
                ),
            )
        } catch (e: KrbException) {
            logger?.error(TAG, "Kerberos KrbException: ${e.message}", e)
            Result.failure(
                AppError.KerberosFailure(
                    message = mapKrbExceptionMessage(e, kdcHost, kdcPort, spnCandidates),
                    technicalDetails = e.toString(),
                ),
            )
        } catch (e: Exception) {
            logger?.error(TAG, "Kerberos token generation failed: ${e.message}", e)
            Result.failure(
                AppError.KerberosFailure(
                    message = e.message?.takeIf { it.isNotBlank() }
                        ?: "Kerberos token generation failed",
                    technicalDetails = e.toString(),
                ),
            )
        }
    }

    private fun requestServiceTicket(
        client: KrbClient,
        tgt: TgtTicket,
        candidates: List<String>,
    ): Pair<String, org.apache.kerby.kerberos.kerb.type.ticket.SgtTicket> {
        var lastUnknown: KrbException? = null
        for (spn in candidates) {
            try {
                logger?.debug(TAG, "TGS-REQ for $spn")
                val sgt = client.requestSgt(tgt, spn)
                return spn to sgt
            } catch (e: KrbException) {
                val text = (e.message.orEmpty() + " " + (e.cause?.message.orEmpty()))
                if (text.contains("S_PRINCIPAL_UNKNOWN", ignoreCase = true)) {
                    logger?.debug(TAG, "SPN unknown: $spn — trying next candidate")
                    lastUnknown = e
                    continue
                }
                throw e
            }
        }
        throw lastUnknown ?: KrbException("No LDAP service principal candidates to try")
    }

    /**
     * Build SPN candidates. Prefer FQDNs that resolve to the LDAP host IP (AD rejects ldap/<ip>).
     *
     * When [ConnectionProfile.kerberosServicePrincipal] is set, skip DNS discovery entirely —
     * public resolvers often REFUSE private AD zones and can add several seconds of latency.
     */
    private suspend fun resolveServicePrincipalCandidates(profile: ConnectionProfile): List<String> {
        val ordered = LinkedHashSet<String>()
        fun addLdap(hostPart: String?) {
            val h = hostPart?.trim()?.trimEnd('.')?.lowercase().orEmpty()
            if (h.isNotEmpty()) ordered += "ldap/$h"
        }

        val host = profile.host.trim()
        val kdc = profile.kerberosKdcHost.ifBlank { host }.trim()
        val domain = profile.domain.trim().trimEnd('.').lowercase()
        val explicitSpn = profile.kerberosServicePrincipal.trim()

        if (explicitSpn.isNotEmpty()) {
            val n = explicitSpn.trimEnd('.')
            // UI often gets FQDN only; AD SPNs are ldap/<fqdn>.
            ordered += if (n.contains('/')) n else "ldap/$n"
            if (domain.isNotBlank()) addLdap(domain)
            if (looksLikeIpLiteral(host)) addLdap(host) else if (host.isNotBlank()) addLdap(host)
            if (looksLikeIpLiteral(kdc) && kdc != host) addLdap(kdc)
            logger?.debug(TAG, "SPN from profile (skip DNS discovery): $ordered")
            return ordered.toList()
        }

        val ldapIps = resolveHostIps(host)
        val preferredFqdns = LinkedHashSet<String>()
        val otherFqdns = LinkedHashSet<String>()

        suspend fun considerFqdn(name: String?) {
            val n = name?.trim()?.trimEnd('.')?.lowercase().orEmpty()
            if (n.isEmpty() || looksLikeIpLiteral(n)) return
            val ips = resolveHostIps(n)
            if (ldapIps.isNotEmpty() && ips.any { it in ldapIps }) {
                preferredFqdns += n
            } else {
                otherFqdns += n
            }
        }

        if (!looksLikeIpLiteral(host)) considerFqdn(host)
        if (!looksLikeIpLiteral(kdc)) considerFqdn(kdc)

        listOf(host, kdc).filter { looksLikeIpLiteral(it) }.distinct().forEach { ip ->
            dnsResolver.resolvePtr(ip).getOrNull().orEmpty().forEach { considerFqdn(it) }
        }

        if (domain.isNotBlank()) {
            // Bounded lookups: MiniDNS may hit public resolvers that REFUSE AD zones.
            listOf("_ldap._tcp.dc._msdcs.$domain", "_ldap._tcp.$domain").forEach { srv ->
                withTimeoutOrNull(DNS_DISCOVERY_TIMEOUT_MS) {
                    dnsResolver.resolveSrv(srv).getOrNull().orEmpty()
                }.orEmpty().forEach { considerFqdn(it.target) }
            }
            otherFqdns += domain
        }

        preferredFqdns.forEach { addLdap(it) }
        otherFqdns.forEach { addLdap(it) }
        if (looksLikeIpLiteral(host)) addLdap(host)
        if (looksLikeIpLiteral(kdc) && kdc != host) addLdap(kdc)

        logger?.debug(
            TAG,
            "SPN ranking ldapIps=$ldapIps preferred=$preferredFqdns other=$otherFqdns",
        )
        return ordered.toList().ifEmpty { listOf("ldap/${host.lowercase()}") }
    }

    private suspend fun resolveHostIps(nameOrIp: String): Set<String> {
        val n = nameOrIp.trim()
        if (n.isEmpty()) return emptySet()
        if (looksLikeIpLiteral(n)) return setOf(n)
        val out = linkedSetOf<String>()
        dnsResolver.resolveA(n).getOrNull()?.forEach { out += it }
        dnsResolver.resolveAAAA(n).getOrNull()?.forEach { out += it }
        return out
    }

    private fun buildAdCompatibleConfig(): KrbConfig {
        val etypes = AD_COMPAT_ENCTYPES
        val libdefaults = mapOf(
            "default_tkt_enctypes" to etypes,
            "default_tgs_enctypes" to etypes,
            "permitted_enctypes" to etypes,
            "allow_weak_crypto" to "true",
        )
        return KrbConfig().apply {
            addMapConfig(mapOf("libdefaults" to libdefaults))
        }
    }

    private fun mapKrbExceptionMessage(
        e: KrbException,
        kdcHost: String,
        kdcPort: Int,
        spnCandidates: List<String>,
    ): String {
        val raw = sequenceOf(e.message, e.cause?.message)
            .filterNotNull()
            .joinToString(" | ")
        val code = e.message.orEmpty() + " " + (e.cause?.message.orEmpty())
        return when {
            code.contains("ETYPE_NOSUPP", ignoreCase = true) ->
                "KDC rejected encryption types (KDC_ERR_ETYPE_NOSUPP) for $kdcHost:$kdcPort. " +
                    "On the AD user, enable “This account supports Kerberos AES 128/256 bit encryption” " +
                    "(and reset the password once), or temporarily allow RC4 on the domain. " +
                    "Client offers: $AD_COMPAT_ENCTYPES"
            code.contains("S_PRINCIPAL_UNKNOWN", ignoreCase = true) ->
                "KDC does not know the LDAP service principal (KDC_ERR_S_PRINCIPAL_UNKNOWN). " +
                    "Tried: ${spnCandidates.joinToString()}. " +
                    "Set Kerberos service principal to ldap/<dc-fqdn> " +
                    "(example: ldap/dc01.loc.example.com), not an IP address."
            raw.contains("establish the transport", ignoreCase = true) ->
                "Kerberos client could not talk to KDC $kdcHost:$kdcPort (transport). " +
                    "Confirm TCP/88 is open from this device. Detail: $raw"
            raw.isNotBlank() && !raw.equals("null", ignoreCase = true) -> raw
            else -> "Kerberos authentication failed ($code)"
        }
    }

    private fun probeTcp(host: String, port: Int, timeoutMs: Int): TcpProbeResult {
        return try {
            val resolved = InetAddress.getByName(host)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(resolved, port), timeoutMs)
            }
            TcpProbeResult.Success(resolved.hostAddress ?: host)
        } catch (e: Exception) {
            TcpProbeResult.Failure(
                reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName,
            )
        }
    }

    private sealed class TcpProbeResult {
        data class Success(val resolvedIp: String) : TcpProbeResult() {
            override fun toString(): String = "ok ip=$resolvedIp"
        }

        data class Failure(val reason: String) : TcpProbeResult() {
            override fun toString(): String = "fail reason=$reason"
        }
    }

    companion object {
        private const val TAG = "Kerberos"
        private const val DNS_DISCOVERY_TIMEOUT_MS = 1_500L
        private const val AD_COMPAT_ENCTYPES =
            "aes256-cts-hmac-sha1-96 aes128-cts-hmac-sha1-96 rc4-hmac arcfour-hmac-md5"

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

        fun looksLikeIpLiteral(host: String): Boolean {
            val h = host.trim().lowercase()
            if (h.isEmpty()) return false
            if (h.count { it == '.' } == 3 && h.all { it.isDigit() || it == '.' }) return true
            return h.contains(':')
        }
    }
}
