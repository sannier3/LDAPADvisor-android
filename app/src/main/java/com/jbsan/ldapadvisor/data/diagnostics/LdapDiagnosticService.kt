package com.jbsan.ldapadvisor.data.diagnostics

import com.jbsan.ldapadvisor.data.ldap.LdapClient
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LdapDiagnosticService(
    private val sessionManager: SessionManager? = null,
) {
    suspend fun run(
        profile: ConnectionProfile,
        client: LdapClient?,
    ): List<DiagnosticTestResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticTestResult>()
        val startedConnect = System.currentTimeMillis()
        if (client == null || !client.isConnected()) {
            results += DiagnosticTestResult(
                id = "ldap-connection",
                category = "LDAP",
                title = "LDAP connection",
                status = DiagnosticStatus.ERROR,
                startedAt = startedConnect,
                completedAt = System.currentTimeMillis(),
                target = "${profile.host}:${profile.port}",
                summary = "Not connected",
                recommendations = listOf("Connect using a valid profile before LDAP protocol checks."),
            )
            return@withContext results
        }
        results += DiagnosticTestResult(
            id = "ldap-connection",
            category = "LDAP",
            title = "LDAP connection",
            status = DiagnosticStatus.SUCCESS,
            startedAt = startedConnect,
            completedAt = System.currentTimeMillis(),
            target = "${profile.host}:${profile.port}",
            summary = "LDAP connection established",
        )

        val bindStarted = System.currentTimeMillis()
        results += DiagnosticTestResult(
            id = "ldap-bind",
            category = "LDAP",
            title = "LDAP bind",
            status = DiagnosticStatus.SUCCESS,
            startedAt = bindStarted,
            completedAt = System.currentTimeMillis(),
            summary = "Bound as ${client.boundIdentity() ?: "(anonymous)"}",
        )

        val rootStarted = System.currentTimeMillis()
        val root = client.readRootDse()
        results += root.fold(
            onSuccess = {
                DiagnosticTestResult(
                    id = "ldap-rootdse",
                    category = "LDAP",
                    title = "RootDSE",
                    status = DiagnosticStatus.SUCCESS,
                    startedAt = rootStarted,
                    completedAt = System.currentTimeMillis(),
                    summary = "defaultNamingContext=${it.defaultNamingContext ?: "(none)"}",
                    evidence = listOfNotNull(
                        it.defaultNamingContext,
                        it.dnsHostName,
                        it.configurationNamingContext,
                    ),
                )
            },
            onFailure = {
                DiagnosticTestResult(
                    id = "ldap-rootdse",
                    category = "LDAP",
                    title = "RootDSE",
                    status = DiagnosticStatus.ERROR,
                    startedAt = rootStarted,
                    completedAt = System.currentTimeMillis(),
                    summary = it.message ?: "RootDSE failed",
                    technicalDetails = it.message,
                )
            },
        )

        val baseDn = profile.baseDn.ifBlank {
            root.getOrNull()?.defaultNamingContext.orEmpty()
        }
        if (baseDn.isNotBlank()) {
            val searchStarted = System.currentTimeMillis()
            val search = client.search(
                LdapSearchRequest(
                    baseDn = baseDn,
                    filter = "(objectClass=*)",
                    scope = SearchScopeMode.BASE,
                    attributes = arrayOf("objectClass"),
                    sizeLimit = 1,
                ),
            )
            results += search.fold(
                onSuccess = {
                    DiagnosticTestResult(
                        id = "ldap-base-search",
                        category = "LDAP",
                        title = "Base DN search",
                        status = DiagnosticStatus.SUCCESS,
                        startedAt = searchStarted,
                        completedAt = System.currentTimeMillis(),
                        target = baseDn,
                        summary = "Base entry readable",
                    )
                },
                onFailure = {
                    DiagnosticTestResult(
                        id = "ldap-base-search",
                        category = "LDAP",
                        title = "Base DN search",
                        status = DiagnosticStatus.ERROR,
                        startedAt = searchStarted,
                        completedAt = System.currentTimeMillis(),
                        target = baseDn,
                        summary = it.message ?: "Search failed",
                    )
                },
            )
        }

        val caps = root.getOrNull()?.toCapabilities()
        if (caps != null) {
            results += DiagnosticTestResult(
                id = "ldap-paged",
                category = "LDAP",
                title = "Paged results support",
                status = if (caps.supportsPagedResults) DiagnosticStatus.SUCCESS else DiagnosticStatus.INFO,
                startedAt = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis(),
                summary = if (caps.supportsPagedResults) {
                    "Server advertises paged results control"
                } else {
                    "Paged results control not advertised"
                },
            )
        }
        results
    }
}
