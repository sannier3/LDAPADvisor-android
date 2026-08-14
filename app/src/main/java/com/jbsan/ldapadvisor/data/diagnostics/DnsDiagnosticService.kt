package com.jbsan.ldapadvisor.data.diagnostics

import com.jbsan.ldapadvisor.data.dns.AdDiscoveryService
import com.jbsan.ldapadvisor.data.dns.DnsResolver
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DnsDiagnosticService(
    private val dnsResolver: DnsResolver = DnsResolver(),
    private val adDiscoveryService: AdDiscoveryService = AdDiscoveryService(dnsResolver),
) {
    suspend fun runForDomain(domain: String, host: String?): List<DiagnosticTestResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<DiagnosticTestResult>()
            val d = domain.trim().trimEnd('.')
            if (d.isNotBlank()) {
                results += lookupTest("dns-a-domain", "Domain A", d, "A")
                results += lookupTest("dns-aaaa-domain", "Domain AAAA", d, "AAAA")
                val discovery = adDiscoveryService.discover(d)
                results += discovery.fold(
                    onSuccess = { data ->
                        listOf(
                            srvResult("dns-srv-ldap-msdcs", "LDAP DC locator SRV", "_ldap._tcp.dc._msdcs.$d", data.ldapDcMsdcs.map { it.hostname }),
                            srvResult("dns-srv-ldap", "LDAP SRV", "_ldap._tcp.$d", data.ldapTcp.map { it.hostname }),
                            srvResult("dns-srv-gc", "Global Catalog SRV", "_gc._tcp.$d", data.globalCatalog.map { it.hostname }),
                            srvResult("dns-srv-kerberos-tcp", "Kerberos TCP SRV", "_kerberos._tcp.$d", data.kerberosTcp.map { it.hostname }),
                            srvResult("dns-srv-kerberos-udp", "Kerberos UDP SRV", "_kerberos._udp.$d", data.kerberosUdp.map { it.hostname }),
                        )
                    },
                    onFailure = { err ->
                        listOf(
                            DiagnosticTestResult(
                                id = "dns-ad-discovery",
                                category = "DNS",
                                title = "AD DNS discovery",
                                status = DiagnosticStatus.ERROR,
                                startedAt = System.currentTimeMillis(),
                                completedAt = System.currentTimeMillis(),
                                target = d,
                                summary = err.message ?: "Discovery failed",
                            ),
                        )
                    },
                )
            }
            if (!host.isNullOrBlank()) {
                results += lookupTest("dns-a-host", "Host A", host, "A")
                results += lookupTest("dns-aaaa-host", "Host AAAA", host, "AAAA")
                val a = dnsResolver.resolveA(host).getOrDefault(emptyList())
                a.firstOrNull()?.let { ip ->
                    results += lookupTest("dns-ptr", "PTR", ip, "PTR")
                }
            }
            results
        }

    private suspend fun lookupTest(
        id: String,
        title: String,
        name: String,
        type: String,
    ): DiagnosticTestResult {
        val started = System.currentTimeMillis()
        val lookup = dnsResolver.lookup(name, type)
        val completed = System.currentTimeMillis()
        val status = when {
            lookup.answers.isNotEmpty() -> DiagnosticStatus.SUCCESS
            lookup.rawError?.contains("NXDOMAIN", true) == true -> DiagnosticStatus.ERROR
            lookup.rawError != null -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.WARNING
        }
        return DiagnosticTestResult(
            id = id,
            category = "DNS",
            title = title,
            status = status,
            startedAt = started,
            completedAt = completed,
            durationMs = completed - started,
            target = name,
            summary = if (lookup.answers.isEmpty()) {
                lookup.rawError ?: "No records"
            } else {
                lookup.answers.joinToString()
            },
            evidence = lookup.answers,
        )
    }

    private fun srvResult(
        id: String,
        title: String,
        target: String,
        hosts: List<String>,
    ): DiagnosticTestResult {
        val now = System.currentTimeMillis()
        return DiagnosticTestResult(
            id = id,
            category = "DNS",
            title = title,
            status = if (hosts.isEmpty()) DiagnosticStatus.ERROR else DiagnosticStatus.SUCCESS,
            startedAt = now,
            completedAt = now,
            target = target,
            summary = if (hosts.isEmpty()) "No SRV records" else hosts.joinToString(),
            evidence = hosts,
            probableCause = if (hosts.isEmpty()) "Missing AD DNS records or wrong DNS suffix/server" else null,
            recommendations = if (hosts.isEmpty()) {
                listOf("Verify domain DNS suffix and that the device uses internal DNS.")
            } else {
                emptyList()
            },
        )
    }
}
