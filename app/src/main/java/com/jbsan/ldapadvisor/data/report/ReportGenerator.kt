package com.jbsan.ldapadvisor.data.report

import com.jbsan.ldapadvisor.core.util.HtmlEscaper
import com.jbsan.ldapadvisor.domain.model.DiagnosticRun
import com.jbsan.ldapadvisor.domain.model.Finding
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReportSanitizer {
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val ipv4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val ipv6 = Regex("\\b(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\\b")
    private val dn = Regex("(?i)\\b(?:cn|ou|dc)=[^,=\\s]+(?:,(?:cn|ou|dc)=[^,=\\s]+)+")

    fun sanitizeText(input: String, domainHints: List<String> = emptyList()): String {
        var out = input
        out = email.replace(out, "[REDACTED_EMAIL]")
        out = dn.replace(out, "[REDACTED_DN]")
        out = ipv4.replace(out, "[REDACTED_IP]")
        out = ipv6.replace(out, "[REDACTED_IP]")
        domainHints.filter { it.isNotBlank() }.sortedByDescending { it.length }.forEach { hint ->
            // Redact FQDNs under the domain first, then the bare domain.
            out = out.replace(Regex("(?i)\\b[\\w-]+(?:\\.[\\w-]+)*\\.${Regex.escape(hint)}\\b"), "[REDACTED_HOST]")
            out = out.replace(hint, "[REDACTED_DOMAIN]", ignoreCase = true)
        }
        // Never allow password-looking assignments through.
        out = Regex("(?i)(password|unicodepwd|secret)\\s*[:=]\\s*\\S+").replace(out, "$1=[REDACTED]")
        return out
    }

    fun sanitizeRun(run: DiagnosticRun, domainHints: List<String> = emptyList()): DiagnosticRun {
        fun s(value: String?) = value?.let { sanitizeText(it, domainHints) }
        return run.copy(
            tests = run.tests.map { test ->
                test.copy(
                    target = s(test.target),
                    summary = sanitizeText(test.summary, domainHints),
                    technicalDetails = s(test.technicalDetails),
                    probableCause = s(test.probableCause),
                    recommendations = test.recommendations.map { sanitizeText(it, domainHints) },
                    evidence = test.evidence.map { sanitizeText(it, domainHints) },
                )
            },
            findings = run.findings.map { finding ->
                finding.copy(
                    description = sanitizeText(finding.description, domainHints),
                    evidence = finding.evidence.map { sanitizeText(it, domainHints) },
                    probableCauses = finding.probableCauses.map { sanitizeText(it, domainHints) },
                    recommendations = finding.recommendations.map { sanitizeText(it, domainHints) },
                )
            },
        )
    }
}

class ReportGenerator(
    private val sanitizer: ReportSanitizer = ReportSanitizer(),
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    fun generateHtml(
        run: DiagnosticRun,
        profileName: String?,
        domain: String?,
        target: String?,
        sanitized: Boolean,
    ): String {
        val data = if (sanitized) {
            sanitizer.sanitizeRun(run, listOfNotNull(domain, target))
        } else {
            run
        }
        val e = HtmlEscaper::escape
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html><head><meta charset=\"utf-8\"/><title>LDAPADvisor Report</title>")
            appendLine("<style>body{font-family:sans-serif;margin:24px} table{border-collapse:collapse;width:100%} td,th{border:1px solid #ccc;padding:6px;text-align:left} .crit{color:#a00}</style>")
            appendLine("</head><body>")
            appendLine("<h1>${e("LDAPADvisor")}</h1>")
            appendLine("<p>Date: ${e(java.time.Instant.ofEpochMilli(data.startedAt).toString())}</p>")
            appendLine("<p>Profile: ${e(profileName ?: data.profileId ?: "")}</p>")
            appendLine("<p>Domain: ${e(domain ?: "")}</p>")
            appendLine("<p>Target: ${e(target ?: "")}</p>")
            appendLine("<p>Diagnostic score: ${e(data.summary.score?.toString() ?: "n/a")} (LDAPADvisor diagnostic score)</p>")
            appendLine("<h2>Summary</h2>")
            appendLine("<ul>")
            appendLine("<li>Success: ${data.summary.successCount}</li>")
            appendLine("<li>Warning: ${data.summary.warningCount}</li>")
            appendLine("<li>Error: ${data.summary.errorCount}</li>")
            appendLine("<li>Unsupported: ${data.summary.unsupportedCount}</li>")
            appendLine("</ul>")
            appendLine("<h2>Findings</h2>")
            if (data.findings.isEmpty()) {
                appendLine("<p>No findings.</p>")
            } else {
                appendLine("<ul>")
                data.findings.forEach { f ->
                    appendLine("<li class=\"crit\"><strong>${e(f.severity.name)}</strong> ${e(f.title)} — ${e(f.description)}</li>")
                }
                appendLine("</ul>")
            }
            appendLine("<h2>Tests</h2>")
            appendLine("<table><tr><th>Status</th><th>Title</th><th>Target</th><th>Duration</th><th>Summary</th></tr>")
            data.tests.forEach { t ->
                appendLine(
                    "<tr><td>${e(t.status.name)}</td><td>${e(t.title)}</td><td>${e(t.target ?: "")}</td>" +
                        "<td>${e(t.durationMs?.toString() ?: "")}</td><td>${e(t.summary)}</td></tr>",
                )
            }
            appendLine("</table>")
            appendLine("<h2>Recommendations</h2><ul>")
            data.findings.flatMap { it.recommendations }.distinct().forEach {
                appendLine("<li>${e(it)}</li>")
            }
            appendLine("</ul>")
            appendLine("</body></html>")
        }
    }

    fun generateJson(
        run: DiagnosticRun,
        profileName: String?,
        domain: String?,
        target: String?,
        sanitized: Boolean,
    ): String {
        val data = if (sanitized) sanitizer.sanitizeRun(run, listOfNotNull(domain, target)) else run
        val payload = ReportJsonV1(
            schemaVersion = 1,
            generatedAtEpochMs = System.currentTimeMillis(),
            profileName = profileName,
            domain = if (sanitized) domain?.let { sanitizer.sanitizeText(it) } else domain,
            target = if (sanitized) target?.let { sanitizer.sanitizeText(it) } else target,
            sanitized = sanitized,
            run = data,
        )
        return json.encodeToString(payload)
    }

    fun generateTxt(
        run: DiagnosticRun,
        profileName: String?,
        domain: String?,
        target: String?,
        sanitized: Boolean,
    ): String {
        val data = if (sanitized) sanitizer.sanitizeRun(run, listOfNotNull(domain, target)) else run
        return buildString {
            appendLine("LDAPADvisor Report")
            appendLine("schemaVersion: 1")
            appendLine("Date: ${java.time.Instant.ofEpochMilli(data.startedAt)}")
            appendLine("Profile: ${profileName ?: data.profileId}")
            appendLine("Domain: ${domain.orEmpty()}")
            appendLine("Target: ${target.orEmpty()}")
            appendLine("Score: ${data.summary.score ?: "n/a"}")
            appendLine()
            appendLine("Findings:")
            if (data.findings.isEmpty()) appendLine("  (none)")
            data.findings.forEach { f ->
                appendLine("- [${f.severity}] ${f.title}")
                appendLine("  ${f.description}")
            }
            appendLine()
            appendLine("Tests:")
            data.tests.forEach { t ->
                appendLine("- ${t.status} ${t.title} (${t.target ?: "-"}): ${t.summary}")
            }
        }
    }
}

@Serializable
data class ReportJsonV1(
    val schemaVersion: Int,
    val generatedAtEpochMs: Long,
    val profileName: String? = null,
    val domain: String? = null,
    val target: String? = null,
    val sanitized: Boolean = true,
    val run: DiagnosticRun,
)
