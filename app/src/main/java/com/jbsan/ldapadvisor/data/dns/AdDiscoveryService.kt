package com.jbsan.ldapadvisor.data.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class DiscoveredDc(
    val hostname: String,
    val port: Int,
    val priority: Int,
    val weight: Int,
    val ipv4: List<String> = emptyList(),
    val ipv6: List<String> = emptyList(),
    val sourceSrv: String,
)

data class AdDiscoveryResult(
    val domain: String,
    val ldapDcMsdcs: List<DiscoveredDc>,
    val ldapTcp: List<DiscoveredDc>,
    val globalCatalog: List<DiscoveredDc>,
    val kerberosTcp: List<DiscoveredDc>,
    val kerberosUdp: List<DiscoveredDc>,
)

class AdDiscoveryService(
    private val dnsResolver: DnsResolver = DnsResolver(),
) {
    suspend fun discover(domain: String): Result<AdDiscoveryResult> = withContext(Dispatchers.IO) {
        val d = domain.trim().trimEnd('.')
        if (d.isBlank()) return@withContext Result.failure(IllegalArgumentException("Domain is required"))
        try {
            coroutineScope {
                val ldapMsdcs = async { resolveSrvHosts("_ldap._tcp.dc._msdcs.$d") }
                val ldapTcp = async { resolveSrvHosts("_ldap._tcp.$d") }
                val gc = async { resolveSrvHosts("_gc._tcp.$d") }
                val krbTcp = async { resolveSrvHosts("_kerberos._tcp.$d") }
                val krbUdp = async { resolveSrvHosts("_kerberos._udp.$d") }
                Result.success(
                    AdDiscoveryResult(
                        domain = d,
                        ldapDcMsdcs = ldapMsdcs.await(),
                        ldapTcp = ldapTcp.await(),
                        globalCatalog = gc.await(),
                        kerberosTcp = krbTcp.await(),
                        kerberosUdp = krbUdp.await(),
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resolveSrvHosts(srvName: String): List<DiscoveredDc> {
        val srv = dnsResolver.resolveSrv(srvName).getOrDefault(emptyList())
        return srv.map { record ->
            val a = dnsResolver.resolveA(record.target).getOrDefault(emptyList())
            val aaaa = dnsResolver.resolveAAAA(record.target).getOrDefault(emptyList())
            DiscoveredDc(
                hostname = record.target,
                port = record.port,
                priority = record.priority,
                weight = record.weight,
                ipv4 = a,
                ipv6 = aaaa,
                sourceSrv = srvName,
            )
        }
    }
}
