package com.jbsan.ldapadvisor.data.diagnostics

import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.data.tls.TlsDiagnosticService
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.DiagnosticRun
import com.jbsan.ldapadvisor.domain.model.DiagnosticRunSummary
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import com.jbsan.ldapadvisor.domain.model.SecurityMode
import com.jbsan.ldapadvisor.domain.service.AdvisorEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

sealed class DiagnosticProgress {
    data class Started(val runId: String, val startedAt: Long) : DiagnosticProgress()
    data class TestCompleted(val runId: String, val completed: Int, val totalHint: Int, val result: DiagnosticTestResult) : DiagnosticProgress()
    data class Finished(val run: DiagnosticRun) : DiagnosticProgress()
}

class DiagnosticEngine(
    private val tcpDiagnosticService: TcpDiagnosticService = TcpDiagnosticService(),
    private val dnsDiagnosticService: DnsDiagnosticService = DnsDiagnosticService(),
    private val ldapDiagnosticService: LdapDiagnosticService = LdapDiagnosticService(),
    private val tlsDiagnosticService: TlsDiagnosticService = TlsDiagnosticService(),
    private val adFsmoService: AdFsmoService = AdFsmoService(),
    private val adTrustService: AdTrustService = AdTrustService(),
    private val passwordPolicyReader: PasswordPolicyReader = PasswordPolicyReader(),
    private val advisorEngine: AdvisorEngine = AdvisorEngine(),
    private val sessionManager: SessionManager? = null,
) {
    fun runFullDiagnostic(
        profile: ConnectionProfile,
        concurrency: Int = 4,
        tcpTimeoutMs: Int = 3_000,
    ): Flow<DiagnosticProgress> = flow {
        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        emit(DiagnosticProgress.Started(runId, startedAt))
        val tests = mutableListOf<DiagnosticTestResult>()
        val totalHint = 40

        suspend fun emitTest(result: DiagnosticTestResult) {
            currentCoroutineContext().ensureActive()
            tests += result
            emit(DiagnosticProgress.TestCompleted(runId, tests.size, totalHint, result))
        }

        try {
            dnsDiagnosticService.runForDomain(profile.domain, profile.host).forEach { emitTest(it) }

            tcpDiagnosticService.probeHost(
                host = profile.host,
                timeoutMs = tcpTimeoutMs,
                concurrency = concurrency,
            ).forEach { emitTest(it) }

            if (profile.securityMode == SecurityMode.LDAPS || profile.port == 636 || profile.port == 3269) {
                emitTest(tlsDiagnosticService.asDiagnosticResult(profile.host, profile.port))
            } else if (profile.securityMode == SecurityMode.START_TLS) {
                emitTest(
                    tlsDiagnosticService.asDiagnosticResult(profile.host, profile.port).copy(
                        id = "tls-starttls-note",
                        title = "TLS (StartTLS path)",
                        summary = "Separate LDAPS probe optional; StartTLS negotiated on LDAP port during connect",
                        status = DiagnosticStatus.INFO,
                    ),
                )
            }

            val client = sessionManager?.currentSession()?.client
            ldapDiagnosticService.run(profile, client).forEach { emitTest(it) }

            val root = client?.readRootDse()?.getOrNull()
            if (root != null && root.toCapabilities().isActiveDirectory) {
                passwordPolicyReader.functionalLevelResults(
                    domainLevel = root.domainFunctionality,
                    forestLevel = root.forestFunctionality,
                    dcLevel = root.domainControllerFunctionality,
                    gcReady = root.isGlobalCatalogReady,
                ).forEach { emitTest(it) }

                val domainNc = root.defaultNamingContext
                val configNc = root.configurationNamingContext
                if (!domainNc.isNullOrBlank() && client != null) {
                    passwordPolicyReader.read(client, domainNc).getOrNull()?.let {
                        emitTest(passwordPolicyReader.asDiagnosticResult(it))
                    }
                    if (!configNc.isNullOrBlank()) {
                        adFsmoService.readRoles(client, configNc, domainNc, root.schemaNamingContext)
                            .getOrNull()
                            ?.let { adFsmoService.asDiagnosticResults(it).forEach { r -> emitTest(r) } }
                    }
                    adTrustService.readTrusts(client, domainNc).getOrNull()?.forEach { emitTest(it) }
                }

                // Embedded Kerberos (Apache Kerby) + SASL GSS-SPNEGO/GSSAPI bind.
                val kerberosConfigured =
                    profile.authMethod == com.jbsan.ldapadvisor.domain.model.AuthMethod.KERBEROS
                emitTest(
                    DiagnosticTestResult(
                        id = "kerberos-gssapi",
                        category = "Kerberos",
                        title = "Embedded Kerberos / SASL GSS bind",
                        status = DiagnosticStatus.INFO,
                        startedAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis(),
                        summary = if (kerberosConfigured) {
                            "Profile uses embedded Kerberos (Kerby). Tickets are obtained from the KDC, then LDAP binds via GSS-SPNEGO/GSSAPI."
                        } else {
                            "Kerberos bind is available: set Auth method to KERBEROS on the connection profile (embedded Kerby client)."
                        },
                        evidence = listOf(
                            "stack=Apache Kerby 2.1.0",
                            "osNativeKerberos=false",
                            "unboundidJaasGssapi=not_used",
                            "configured=$kerberosConfigured",
                        ),
                        recommendations = listOf(
                            "Ensure UDP/TCP 88 to the KDC is reachable (VPN if needed)",
                            "Use the DC FQDN as LDAP host so the default SPN ldap/<host> matches AD",
                            "Prefer AES enctypes; RC4 may be disabled in modern AD",
                        ),
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emitTest(
                DiagnosticTestResult(
                    id = "diagnostic-engine-error",
                    category = "Engine",
                    title = "Diagnostic engine",
                    status = DiagnosticStatus.ERROR,
                    startedAt = System.currentTimeMillis(),
                    completedAt = System.currentTimeMillis(),
                    summary = e.message ?: "Unexpected diagnostic failure",
                ),
            )
        }

        val summary = summarize(tests)
        val findings = advisorEngine.evaluate(profile, tests)
        val run = DiagnosticRun(
            id = runId,
            profileId = profile.id,
            startedAt = startedAt,
            completedAt = System.currentTimeMillis(),
            tests = tests.toList(),
            summary = summary,
            findings = findings,
        )
        emit(DiagnosticProgress.Finished(run))
    }

    fun summarize(tests: List<DiagnosticTestResult>): DiagnosticRunSummary {
        var success = 0
        var warning = 0
        var error = 0
        var info = 0
        var skipped = 0
        var unsupported = 0
        tests.forEach {
            when (it.status) {
                DiagnosticStatus.SUCCESS -> success++
                DiagnosticStatus.WARNING -> warning++
                DiagnosticStatus.ERROR -> error++
                DiagnosticStatus.INFO -> info++
                DiagnosticStatus.SKIPPED -> skipped++
                DiagnosticStatus.UNSUPPORTED -> unsupported++
                DiagnosticStatus.RUNNING -> Unit
            }
        }
        val scored = tests.filter {
            it.status == DiagnosticStatus.SUCCESS ||
                it.status == DiagnosticStatus.WARNING ||
                it.status == DiagnosticStatus.ERROR
        }
        val score = if (scored.isEmpty()) {
            null
        } else {
            val points = scored.sumOf {
                when (it.status) {
                    DiagnosticStatus.SUCCESS -> 100
                    DiagnosticStatus.WARNING -> 60
                    DiagnosticStatus.ERROR -> 0
                    else -> 0
                }
            }
            points / scored.size
        }
        return DiagnosticRunSummary(
            successCount = success,
            warningCount = warning,
            errorCount = error,
            infoCount = info,
            skippedCount = skipped,
            unsupportedCount = unsupported,
            score = score,
        )
    }
}
