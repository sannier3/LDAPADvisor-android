package com.jbsan.ldapadvisor.data.diagnostics

import com.jbsan.ldapadvisor.core.ad.FileTimeConverter
import com.jbsan.ldapadvisor.core.ad.FunctionalLevelDecoder
import com.jbsan.ldapadvisor.data.ldap.LdapClient
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DomainPasswordPolicy(
    val minPwdAge: String?,
    val maxPwdAge: String?,
    val minPwdLength: Int?,
    val pwdHistoryLength: Int?,
    val pwdProperties: Int?,
    val lockoutThreshold: Int?,
    val lockoutDuration: String?,
    val lockOutObservationWindow: String?,
)

class PasswordPolicyReader {
    suspend fun read(client: LdapClient, domainNc: String): Result<DomainPasswordPolicy> =
        withContext(Dispatchers.IO) {
            try {
                val entry = client.search(
                    LdapSearchRequest(
                        baseDn = domainNc,
                        filter = "(objectClass=*)",
                        scope = SearchScopeMode.BASE,
                        attributes = arrayOf(
                            "minPwdAge", "maxPwdAge", "minPwdLength", "pwdHistoryLength",
                            "pwdProperties", "lockoutThreshold", "lockoutDuration", "lockOutObservationWindow",
                        ),
                    ),
                ).getOrElse { return@withContext Result.failure(it) }.firstOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("Domain object not found"))

                fun durationLabel(raw: String?): String? {
                    val parsed = FileTimeConverter.parse(raw)
                    return when (parsed) {
                        is FileTimeConverter.FileTimeValue.DurationValue -> parsed.duration.toString()
                        is FileTimeConverter.FileTimeValue.Never -> "Never"
                        is FileTimeConverter.FileTimeValue.Zero -> "0"
                        else -> raw
                    }
                }

                Result.success(
                    DomainPasswordPolicy(
                        minPwdAge = durationLabel(entry.firstString("minPwdAge")),
                        maxPwdAge = durationLabel(entry.firstString("maxPwdAge")),
                        minPwdLength = entry.firstString("minPwdLength")?.toIntOrNull(),
                        pwdHistoryLength = entry.firstString("pwdHistoryLength")?.toIntOrNull(),
                        pwdProperties = entry.firstString("pwdProperties")?.toIntOrNull(),
                        lockoutThreshold = entry.firstString("lockoutThreshold")?.toIntOrNull(),
                        lockoutDuration = durationLabel(entry.firstString("lockoutDuration")),
                        lockOutObservationWindow = durationLabel(entry.firstString("lockOutObservationWindow")),
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun asDiagnosticResult(policy: DomainPasswordPolicy): DiagnosticTestResult {
        val now = System.currentTimeMillis()
        return DiagnosticTestResult(
            id = "ad-password-policy",
            category = "AD",
            title = "Domain password policy",
            status = DiagnosticStatus.INFO,
            startedAt = now,
            completedAt = now,
            summary = "minLen=${policy.minPwdLength} history=${policy.pwdHistoryLength} lockoutThreshold=${policy.lockoutThreshold}",
            evidence = listOfNotNull(
                policy.minPwdAge?.let { "minPwdAge=$it" },
                policy.maxPwdAge?.let { "maxPwdAge=$it" },
                policy.lockoutDuration?.let { "lockoutDuration=$it" },
            ),
        )
    }

    fun functionalLevelResults(
        domainLevel: String?,
        forestLevel: String?,
        dcLevel: String?,
        gcReady: String?,
    ): List<DiagnosticTestResult> {
        val now = System.currentTimeMillis()
        return listOf(
            DiagnosticTestResult(
                id = "ad-domain-functional-level",
                category = "AD",
                title = "Domain functional level",
                status = DiagnosticStatus.INFO,
                startedAt = now,
                completedAt = now,
                summary = FunctionalLevelDecoder.label(domainLevel),
            ),
            DiagnosticTestResult(
                id = "ad-forest-functional-level",
                category = "AD",
                title = "Forest functional level",
                status = DiagnosticStatus.INFO,
                startedAt = now,
                completedAt = now,
                summary = FunctionalLevelDecoder.label(forestLevel),
            ),
            DiagnosticTestResult(
                id = "ad-dc-functional-level",
                category = "AD",
                title = "DC functional level",
                status = DiagnosticStatus.INFO,
                startedAt = now,
                completedAt = now,
                summary = FunctionalLevelDecoder.label(dcLevel),
            ),
            DiagnosticTestResult(
                id = "ad-gc-ready",
                category = "AD",
                title = "Global Catalog ready",
                status = when {
                    gcReady.equals("TRUE", true) -> DiagnosticStatus.SUCCESS
                    gcReady.equals("FALSE", true) -> DiagnosticStatus.WARNING
                    else -> DiagnosticStatus.INFO
                },
                startedAt = now,
                completedAt = now,
                summary = gcReady ?: "Not advertised",
            ),
        )
    }
}
