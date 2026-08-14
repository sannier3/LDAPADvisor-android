package com.jbsan.ldapadvisor.data.report

import com.jbsan.ldapadvisor.domain.model.DiagnosticRun
import com.jbsan.ldapadvisor.domain.model.DiagnosticRunSummary
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import com.jbsan.ldapadvisor.domain.model.Finding
import com.jbsan.ldapadvisor.domain.model.FindingSeverity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSanitizerTest {
    @Test
    fun masksSensitiveValuesAndKeepsPasswordsOut() {
        val sanitizer = ReportSanitizer()
        val run = DiagnosticRun(
            id = "r1",
            profileId = "p1",
            startedAt = 1L,
            completedAt = 2L,
            summary = DiagnosticRunSummary(successCount = 1, score = 100),
            tests = listOf(
                DiagnosticTestResult(
                    id = "t1",
                    category = "DNS",
                    title = "A",
                    status = DiagnosticStatus.SUCCESS,
                    startedAt = 1L,
                    completedAt = 2L,
                    target = "dc01.corp.example.com",
                    summary = "192.0.2.10 user@corp.example.com CN=jdoe,OU=Users,DC=corp,DC=example,DC=com password=Secret",
                ),
            ),
            findings = listOf(
                Finding(
                    id = "f1",
                    severity = FindingSeverity.LOW,
                    category = "x",
                    title = "t",
                    description = "Host dc01.corp.example.com",
                ),
            ),
        )
        val sanitized = sanitizer.sanitizeRun(run, listOf("corp.example.com"))
        val blob = buildString {
            append(sanitized.tests.first().summary)
            append(' ')
            append(sanitized.tests.first().target.orEmpty())
            append(' ')
            append(sanitized.findings.first().description)
        }
        assertFalse(blob.contains("192.0.2.10"))
        assertFalse(blob.contains("user@corp.example.com"))
        assertFalse(blob.contains("Secret"))
        assertFalse(blob.contains("corp.example.com"))
        assertTrue(blob.contains("[REDACTED"))
    }

    @Test
    fun htmlEscapesLdapValues() {
        val generator = ReportGenerator()
        val run = DiagnosticRun(
            id = "r1",
            profileId = "p1",
            startedAt = 1L,
            tests = listOf(
                DiagnosticTestResult(
                    id = "t1",
                    category = "LDAP",
                    title = "<script>",
                    status = DiagnosticStatus.INFO,
                    startedAt = 1L,
                    summary = "cn=<b>x</b>",
                ),
            ),
        )
        val html = generator.generateHtml(run, "p", "corp.example.com", "dc01.corp.example.com", sanitized = false)
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("schemaVersion").not())
        val json = generator.generateJson(run, "p", "corp.example.com", "dc01", false)
        assertTrue(json.contains("\"schemaVersion\": 1"))
    }
}
