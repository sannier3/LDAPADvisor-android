package com.jbsan.ldapadvisor.domain.service

import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import com.jbsan.ldapadvisor.domain.model.Finding
import com.jbsan.ldapadvisor.domain.model.FindingSeverity
import com.jbsan.ldapadvisor.domain.model.SecurityMode
import com.jbsan.ldapadvisor.domain.model.TrustMode

/**
 * Deterministic local advisor rules. No cloud AI.
 */
class AdvisorEngine {

    fun evaluate(
        profile: ConnectionProfile,
        tests: List<DiagnosticTestResult>,
        extraSignals: AdvisorSignals = AdvisorSignals(),
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        val byId = tests.associateBy { it.id }

        fun failed(idPrefix: String): Boolean =
            tests.any { it.id.startsWith(idPrefix) && it.status == DiagnosticStatus.ERROR }

        fun missingSrv(id: String): Boolean =
            byId[id]?.let { it.status == DiagnosticStatus.ERROR || it.evidence.isEmpty() } == true

        if (missingSrv("dns-srv-ldap-msdcs") && missingSrv("dns-srv-ldap")) {
            findings += finding(
                id = "adv-no-dc-srv",
                severity = FindingSeverity.HIGH,
                category = "DNS",
                title = "No AD DC SRV records found",
                description = "Neither _ldap._tcp.dc._msdcs nor _ldap._tcp SRV records were found for the configured domain.",
                evidence = listOfNotNull(byId["dns-srv-ldap-msdcs"]?.summary, byId["dns-srv-ldap"]?.summary),
                causes = listOf("Wrong domain DNS name", "Device not using internal DNS", "AD DNS zone incomplete"),
                recommendations = listOf(
                    "Confirm the domain FQDN",
                    "Ensure the Android device uses corporate DNS",
                    "Check SRV records with an internal resolver",
                ),
                related = listOf("dns-srv-ldap-msdcs", "dns-srv-ldap"),
            )
        }

        if (missingSrv("dns-srv-gc")) {
            findings += finding(
                id = "adv-no-gc-srv",
                severity = FindingSeverity.MEDIUM,
                category = "DNS",
                title = "No Global Catalog found",
                description = "No _gc._tcp SRV records were discovered.",
                evidence = listOfNotNull(byId["dns-srv-gc"]?.summary),
                causes = listOf("GC not published in DNS", "Forest DNS issues"),
                recommendations = listOf("Verify Global Catalog DNS publication", "Test TCP/3268 against known DCs"),
                related = listOf("dns-srv-gc"),
            )
        }

        if (byId["tcp-389"]?.status == DiagnosticStatus.ERROR) {
            findings += finding(
                id = "adv-ldap-unreachable",
                severity = FindingSeverity.HIGH,
                category = "TCP",
                title = "LDAP unreachable",
                description = "TCP/389 to the target host failed.",
                evidence = listOfNotNull(byId["tcp-389"]?.summary),
                causes = listOf("Firewall filtering", "Wrong host", "LDAP service down"),
                recommendations = listOf("Verify host/IP", "Check firewall paths to TCP/389"),
                related = listOf("tcp-389"),
            )
        }

        if (byId["tcp-636"]?.status == DiagnosticStatus.ERROR) {
            findings += finding(
                id = "adv-ldaps-unreachable",
                severity = FindingSeverity.HIGH,
                category = "TCP",
                title = "LDAPS unreachable",
                description = "TCP/636 to the target host failed.",
                evidence = listOfNotNull(byId["tcp-636"]?.summary),
                causes = listOf("LDAPS not enabled", "Firewall filtering", "Certificate/service issue"),
                recommendations = listOf("Confirm LDAPS listener", "Test certificate with TLS diagnostic"),
                related = listOf("tcp-636"),
            )
        }

        val tls = byId["tls-handshake"]
        if (tls != null) {
            val details = (tls.technicalDetails ?: "") + " " + tls.summary
            if (tls.status == DiagnosticStatus.ERROR && details.contains("expired", true)) {
                findings += finding(
                    id = "adv-ldaps-cert-expired",
                    severity = FindingSeverity.CRITICAL,
                    category = "TLS",
                    title = "LDAPS certificate expired",
                    description = "The TLS certificate presented by the server appears expired.",
                    evidence = listOf(details.trim()),
                    causes = listOf("Certificate not renewed", "Wrong certificate bound to LDAPS"),
                    recommendations = listOf("Renew and rebind the LDAPS certificate", "Verify chain and time sync"),
                    related = listOf("tls-handshake"),
                )
            } else if (tls.status == DiagnosticStatus.WARNING && details.contains("fingerprint", true)) {
                findings += finding(
                    id = "adv-ldaps-cert-near-expiry",
                    severity = FindingSeverity.MEDIUM,
                    category = "TLS",
                    title = "LDAPS certificate near expiration",
                    description = "The TLS certificate is valid but near expiration.",
                    evidence = listOf(details.trim()),
                    causes = listOf("Upcoming certificate expiry"),
                    recommendations = listOf("Plan certificate renewal before expiry"),
                    related = listOf("tls-handshake"),
                )
            }
            if (details.contains("hostnameMatched=false", true) ||
                details.contains("hostname", true) && tls.status == DiagnosticStatus.ERROR
            ) {
                findings += finding(
                    id = "adv-ldaps-hostname-mismatch",
                    severity = FindingSeverity.HIGH,
                    category = "TLS",
                    title = "LDAPS hostname mismatch",
                    description = "The certificate hostname does not match the connection target.",
                    evidence = listOf(details.trim()),
                    causes = listOf("Connecting by IP", "Missing SAN", "Wrong certificate"),
                    recommendations = listOf("Connect using a DNS name present in SAN", "Update certificate SANs"),
                    related = listOf("tls-handshake"),
                )
            }
            if (details.contains("trust", true) || details.contains("PKIX", true)) {
                findings += finding(
                    id = "adv-ldaps-untrusted",
                    severity = FindingSeverity.HIGH,
                    category = "TLS",
                    title = "LDAPS untrusted certificate",
                    description = "The server certificate is not trusted by the configured trust mode.",
                    evidence = listOf(details.trim()),
                    causes = listOf("Internal PKI not imported", "Incomplete chain"),
                    recommendations = listOf(
                        "Import the enterprise CA into CUSTOM_CA trust",
                        "Do not permanently disable certificate validation",
                    ),
                    related = listOf("tls-handshake"),
                )
            }
            val incompleteChain = details.contains("incomplete chain", true) ||
                details.contains("unable to find valid certification path", true) ||
                details.contains("cert path", true) ||
                tls.evidence.any {
                    it.contains("incomplete chain", true) ||
                        it.contains("unable to find valid certification path", true)
                }
            if (incompleteChain &&
                (tls.status == DiagnosticStatus.ERROR || tls.status == DiagnosticStatus.WARNING)
            ) {
                findings += finding(
                    id = "adv-ldaps-incomplete-chain",
                    severity = FindingSeverity.HIGH,
                    category = "TLS",
                    title = "Incomplete TLS certificate chain",
                    description = "TLS evidence indicates the server did not present a complete, verifiable certificate chain.",
                    evidence = listOf(details.trim()).filter { it.isNotBlank() } + tls.evidence.take(3),
                    causes = listOf(
                        "Intermediate CA missing from server handshake",
                        "Enterprise CA not imported on the device",
                    ),
                    recommendations = listOf(
                        "Ensure the LDAPS listener sends intermediate certificates",
                        "Import the issuing CA via CUSTOM_CA trust mode",
                    ),
                    related = listOf("tls-handshake"),
                )
            }
        }

        if (byId["ad-gc-ready"]?.summary.equals("FALSE", true)) {
            findings += finding(
                id = "adv-gc-not-ready",
                severity = FindingSeverity.MEDIUM,
                category = "AD",
                title = "GC not ready",
                description = "RootDSE reports isGlobalCatalogReady=FALSE.",
                evidence = listOfNotNull(byId["ad-gc-ready"]?.summary),
                causes = listOf("DC not advertising as GC", "Replication/GC promotion pending"),
                recommendations = listOf("Verify GC role on the DC", "Check AD replication health on a workstation tool"),
                related = listOf("ad-gc-ready"),
            )
        }

        if (profile.securityMode == SecurityMode.LDAP && profile.bindIdentity.isNotBlank()) {
            findings += finding(
                id = "adv-simple-bind-no-tls",
                severity = FindingSeverity.HIGH,
                category = "Security",
                title = "Simple Bind configured without TLS",
                description = "The profile uses plaintext LDAP with a bind identity. Credentials may be exposed.",
                evidence = listOf("securityMode=LDAP", "bindIdentity configured"),
                causes = listOf("Profile intentionally using LDAP:389"),
                recommendations = listOf("Prefer LDAPS or StartTLS for authenticated binds"),
                related = emptyList(),
            )
        }

        if (profile.trustMode == TrustMode.INSECURE_NO_VERIFY) {
            findings += finding(
                id = "adv-insecure-trust",
                severity = FindingSeverity.HIGH,
                category = "Security",
                title = "TLS certificate verification disabled",
                description = "The profile uses INSECURE_NO_VERIFY. Server certificates and hostnames are not validated, enabling MITM attacks.",
                evidence = listOf("trustMode=INSECURE_NO_VERIFY"),
                causes = listOf("Lab or self-signed certificate without imported CA", "Temporary troubleshooting"),
                recommendations = listOf(
                    "Import the enterprise CA (CUSTOM_CA)",
                    "Or pin the server certificate fingerprint (PINNED)",
                    "Use INSECURE_NO_VERIFY only on trusted networks briefly",
                ),
                related = emptyList(),
            )
        }

        if (extraSignals.userDisabled) {
            findings += finding(
                id = "adv-user-disabled",
                severity = FindingSeverity.MEDIUM,
                category = "User",
                title = "User disabled",
                description = "The inspected user account has ACCOUNTDISABLE set.",
                evidence = extraSignals.evidence,
                causes = listOf("Administrative disable", "Provisioning incomplete"),
                recommendations = listOf("Confirm whether the account should be enabled"),
                related = emptyList(),
            )
        }
        if (extraSignals.userLocked) {
            findings += finding(
                id = "adv-user-locked",
                severity = FindingSeverity.HIGH,
                category = "User",
                title = "User locked",
                description = "The inspected user appears locked out.",
                evidence = extraSignals.evidence,
                causes = listOf("Bad password threshold reached"),
                recommendations = listOf("Unlock after verifying user identity", "Review lockout policy"),
                related = emptyList(),
            )
        }
        if (extraSignals.userExpired) {
            findings += finding(
                id = "adv-user-expired",
                severity = FindingSeverity.MEDIUM,
                category = "User",
                title = "User expired",
                description = "The inspected user accountExpires value indicates expiry.",
                evidence = extraSignals.evidence,
                causes = listOf("Temporary account expired"),
                recommendations = listOf("Extend accountExpires if still required"),
                related = emptyList(),
            )
        }
        if (extraSignals.passwordExpired) {
            findings += finding(
                id = "adv-password-expired",
                severity = FindingSeverity.HIGH,
                category = "User",
                title = "Password expired",
                description = "Computed account control indicates the password is marked expired.",
                evidence = extraSignals.evidence,
                causes = listOf("maxPwdAge reached", "Admin forced expire"),
                recommendations = listOf("Reset password over LDAPS/StartTLS"),
                related = emptyList(),
            )
        }
        if (extraSignals.passwordExpiresSoon) {
            findings += finding(
                id = "adv-password-expires-soon",
                severity = FindingSeverity.LOW,
                category = "User",
                title = "Password expires soon",
                description = "Password expiry is approaching based on computed attributes.",
                evidence = extraSignals.evidence,
                causes = listOf("Normal password aging"),
                recommendations = listOf("Notify the user to change password"),
                related = emptyList(),
            )
        }
        if (extraSignals.passwordNeverExpires) {
            findings += finding(
                id = "adv-password-never-expires",
                severity = FindingSeverity.LOW,
                category = "User",
                title = "Password never expires",
                description = "DONT_EXPIRE_PASSWORD is set on the account.",
                evidence = extraSignals.evidence,
                causes = listOf("Service account configuration", "Policy exception"),
                recommendations = listOf("Confirm this exception is intentional and documented"),
                related = emptyList(),
            )
        }
        if (extraSignals.computerDnsAMissing) {
            findings += finding(
                id = "adv-computer-dns-a-missing",
                severity = FindingSeverity.MEDIUM,
                category = "Computer",
                title = "Computer DNS A missing",
                description = "No A record was found for the computer hostname.",
                evidence = extraSignals.evidence,
                causes = listOf("Stale computer object", "DNS scavenging", "Wrong DNS zone"),
                recommendations = listOf("Verify DNS registration", "Check dNSHostName attribute"),
                related = emptyList(),
            )
        }
        if (extraSignals.computerPtrMissing) {
            findings += finding(
                id = "adv-computer-ptr-missing",
                severity = FindingSeverity.LOW,
                category = "Computer",
                title = "Computer PTR missing",
                description = "No PTR record was found for the computer address.",
                evidence = extraSignals.evidence,
                causes = listOf("Reverse zone not maintained"),
                recommendations = listOf("Create PTR if required by environment policy"),
                related = emptyList(),
            )
        }

        val ldapTcpTests = tests.filter { it.id.startsWith("tcp-389") || it.id == "tcp-389" }
        if (ldapTcpTests.isNotEmpty() && ldapTcpTests.all { it.status == DiagnosticStatus.ERROR } &&
            tests.any { it.id.startsWith("dns-srv-ldap") && it.evidence.isNotEmpty() }
        ) {
            findings += finding(
                id = "adv-all-dcs-ldap-unreachable",
                severity = FindingSeverity.CRITICAL,
                category = "AD",
                title = "All discovered DCs unreachable on LDAP",
                description = "DNS discovery returned DC names but LDAP TCP checks failed.",
                evidence = ldapTcpTests.map { it.summary },
                causes = listOf("Network path blocked", "Wrong VLAN/VPN", "All DCs down"),
                recommendations = listOf("Validate routing/VPN", "Test from another client on the same network"),
                related = listOf("tcp-389"),
            )
        }

        if (missingSrv("dns-srv-kerberos-tcp") && missingSrv("dns-srv-kerberos-udp")) {
            findings += finding(
                id = "adv-kerberos-srv-missing",
                severity = FindingSeverity.MEDIUM,
                category = "Kerberos",
                title = "Kerberos SRV missing",
                description = "No Kerberos SRV records were found for TCP or UDP.",
                evidence = listOfNotNull(
                    byId["dns-srv-kerberos-tcp"]?.summary,
                    byId["dns-srv-kerberos-udp"]?.summary,
                ),
                causes = listOf("DNS zone incomplete", "Wrong domain name"),
                recommendations = listOf("Verify _kerberos._tcp/_udp SRV records"),
                related = listOf("dns-srv-kerberos-tcp", "dns-srv-kerberos-udp"),
            )
        }

        return findings
    }

    private fun finding(
        id: String,
        severity: FindingSeverity,
        category: String,
        title: String,
        description: String,
        evidence: List<String>,
        causes: List<String>,
        recommendations: List<String>,
        related: List<String>,
    ) = Finding(
        id = id,
        severity = severity,
        category = category,
        title = title,
        description = description,
        evidence = evidence,
        probableCauses = causes,
        recommendations = recommendations,
        relatedTests = related,
    )
}

data class AdvisorSignals(
    val userDisabled: Boolean = false,
    val userLocked: Boolean = false,
    val userExpired: Boolean = false,
    val passwordExpired: Boolean = false,
    val passwordExpiresSoon: Boolean = false,
    val passwordNeverExpires: Boolean = false,
    val computerDnsAMissing: Boolean = false,
    val computerPtrMissing: Boolean = false,
    val evidence: List<String> = emptyList(),
)
