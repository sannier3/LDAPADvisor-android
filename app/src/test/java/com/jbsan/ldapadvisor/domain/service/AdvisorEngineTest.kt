package com.jbsan.ldapadvisor.domain.service

import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import com.jbsan.ldapadvisor.domain.model.DirectoryType
import com.jbsan.ldapadvisor.domain.model.SecurityMode
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorEngineTest {
    private val engine = AdvisorEngine()

    private fun profile(security: SecurityMode = SecurityMode.LDAPS) = ConnectionProfile(
        id = "p1",
        name = "corp",
        directoryType = DirectoryType.ACTIVE_DIRECTORY,
        domain = "corp.example.com",
        host = "dc01.corp.example.com",
        port = security.defaultPort(),
        securityMode = security,
        bindIdentity = "admin@corp.example.com",
        baseDn = "DC=corp,DC=example,DC=com",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun test(
        id: String,
        status: DiagnosticStatus,
        summary: String = "",
        evidence: List<String> = emptyList(),
        technicalDetails: String? = null,
    ) = DiagnosticTestResult(
        id = id,
        category = "t",
        title = id,
        status = status,
        startedAt = 1L,
        completedAt = 2L,
        summary = summary,
        evidence = evidence,
        technicalDetails = technicalDetails,
    )

    @Test
    fun detectsMissingDcSrvAndPlaintextBind() {
        val findings = engine.evaluate(
            profile(SecurityMode.LDAP),
            listOf(
                test("dns-srv-ldap-msdcs", DiagnosticStatus.ERROR, "No SRV records"),
                test("dns-srv-ldap", DiagnosticStatus.ERROR, "No SRV records"),
                test("tcp-389", DiagnosticStatus.ERROR, "Timeout"),
                test("tcp-636", DiagnosticStatus.ERROR, "Timeout"),
            ),
        )
        val ids = findings.map { it.id }.toSet()
        assertTrue(ids.contains("adv-no-dc-srv"))
        assertTrue(ids.contains("adv-ldap-unreachable"))
        assertTrue(ids.contains("adv-ldaps-unreachable"))
        assertTrue(ids.contains("adv-simple-bind-no-tls"))
    }

    @Test
    fun detectsUserSignals() {
        val findings = engine.evaluate(
            profile(),
            emptyList(),
            AdvisorSignals(
                userDisabled = true,
                userLocked = true,
                passwordExpired = true,
                passwordNeverExpires = true,
                computerDnsAMissing = true,
                computerPtrMissing = true,
                evidence = listOf("CN=jdoe,OU=Users,DC=corp,DC=example,DC=com"),
            ),
        )
        val ids = findings.map { it.id }.toSet()
        assertTrue(ids.contains("adv-user-disabled"))
        assertTrue(ids.contains("adv-user-locked"))
        assertTrue(ids.contains("adv-password-expired"))
        assertTrue(ids.contains("adv-password-never-expires"))
        assertTrue(ids.contains("adv-computer-dns-a-missing"))
        assertTrue(ids.contains("adv-computer-ptr-missing"))
    }

    @Test
    fun detectsIncompleteTlsChain() {
        val findings = engine.evaluate(
            profile(),
            listOf(
                test(
                    id = "tls-handshake",
                    status = DiagnosticStatus.ERROR,
                    summary = "TLS failure",
                    technicalDetails = "PKIX path building failed: unable to find valid certification path",
                    evidence = listOf("incomplete chain from server"),
                ),
            ),
        )
        val ids = findings.map { it.id }.toSet()
        assertTrue(ids.contains("adv-ldaps-incomplete-chain"))
        assertTrue(ids.contains("adv-ldaps-untrusted"))
    }
}
