package com.jbsan.ldapadvisor.data.diagnostics

import com.jbsan.ldapadvisor.core.ad.TrustDecoders
import com.jbsan.ldapadvisor.data.ldap.LdapClient
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdTrustService {
    suspend fun readTrusts(client: LdapClient, domainNc: String): Result<List<DiagnosticTestResult>> =
        withContext(Dispatchers.IO) {
            try {
                val entries = client.search(
                    LdapSearchRequest(
                        baseDn = "CN=System,$domainNc",
                        filter = "(objectClass=trustedDomain)",
                        scope = SearchScopeMode.ONE,
                        attributes = arrayOf("trustPartner", "trustDirection", "trustType", "trustAttributes", "cn"),
                    ),
                ).getOrElse { return@withContext Result.failure(it) }
                val now = System.currentTimeMillis()
                val results = entries.map { entry ->
                    val partner = entry.firstString("trustPartner") ?: entry.firstString("cn") ?: entry.dn
                    val direction = entry.firstString("trustDirection")?.toIntOrNull() ?: -1
                    val type = entry.firstString("trustType")?.toIntOrNull() ?: -1
                    val attrs = entry.firstString("trustAttributes")?.toIntOrNull() ?: 0
                    val decoded = TrustDecoders.decode(direction, type, attrs)
                    DiagnosticTestResult(
                        id = "trust-${partner.lowercase()}",
                        category = "AD",
                        title = "Trust: $partner",
                        status = DiagnosticStatus.INFO,
                        startedAt = now,
                        completedAt = now,
                        target = entry.dn,
                        summary = "${decoded.direction} / ${decoded.type}",
                        evidence = decoded.attributes.toList(),
                        technicalDetails = "LDAP trustedDomain object only; not a full trust health proof",
                    )
                }
                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
