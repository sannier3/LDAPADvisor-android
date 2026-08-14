package com.jbsan.ldapadvisor.data.diagnostics

import com.jbsan.ldapadvisor.data.ldap.LdapClient
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FsmoRole(
    val role: String,
    val ntdsSettingsDn: String?,
    val serverDn: String?,
    val dnsHostName: String?,
)

class AdFsmoService {
    suspend fun readRoles(
        client: LdapClient,
        configurationNc: String,
        domainNc: String,
        schemaNc: String?,
    ): Result<List<FsmoRole>> = withContext(Dispatchers.IO) {
        try {
            val roles = mutableListOf<FsmoRole>()

            suspend fun owner(base: String, roleName: String) {
                val entry = client.search(
                    LdapSearchRequest(
                        baseDn = base,
                        filter = "(objectClass=*)",
                        scope = SearchScopeMode.BASE,
                        attributes = arrayOf("fSMORoleOwner"),
                    ),
                ).getOrNull()?.firstOrNull()
                val ntds = entry?.firstString("fSMORoleOwner")
                val resolved = ntds?.let { resolveNtdsToDns(client, it) }
                roles += FsmoRole(roleName, ntds, resolved?.first, resolved?.second)
            }

            if (!schemaNc.isNullOrBlank()) owner(schemaNc, "Schema Master")
            owner("CN=Partitions,$configurationNc", "Domain Naming Master")
            owner(domainNc, "PDC Emulator")
            owner("CN=RID Manager$,CN=System,$domainNc", "RID Master")
            owner("CN=Infrastructure,$domainNc", "Infrastructure Master")
            Result.success(roles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resolveNtdsToDns(client: LdapClient, ntdsDn: String): Pair<String?, String?> {
        val serverDn = ntdsDn.substringAfter(',', missingDelimiterValue = "").ifBlank { null }
        if (serverDn.isNullOrBlank()) return null to null
        val server = client.search(
            LdapSearchRequest(
                baseDn = serverDn,
                filter = "(objectClass=*)",
                scope = SearchScopeMode.BASE,
                attributes = arrayOf("dNSHostName", "cn"),
            ),
        ).getOrNull()?.firstOrNull()
        return serverDn to server?.firstString("dNSHostName")
    }

    fun asDiagnosticResults(roles: List<FsmoRole>): List<DiagnosticTestResult> {
        val now = System.currentTimeMillis()
        return roles.map { role ->
            DiagnosticTestResult(
                id = "fsmo-${role.role.lowercase().replace(' ', '-')}",
                category = "AD",
                title = "FSMO: ${role.role}",
                status = if (role.dnsHostName != null || role.ntdsSettingsDn != null) {
                    DiagnosticStatus.SUCCESS
                } else {
                    DiagnosticStatus.WARNING
                },
                startedAt = now,
                completedAt = now,
                summary = role.dnsHostName ?: role.ntdsSettingsDn ?: "Role cannot be determined",
                evidence = listOfNotNull(role.ntdsSettingsDn, role.serverDn, role.dnsHostName),
            )
        }
    }
}
