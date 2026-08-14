package com.jbsan.ldapadvisor.data.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.minidns.hla.ResolverApi
import org.minidns.record.A
import org.minidns.record.AAAA
import org.minidns.record.PTR
import org.minidns.record.SRV
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

data class SrvRecord(
    val target: String,
    val port: Int,
    val priority: Int,
    val weight: Int,
)

data class DnsLookupResult(
    val name: String,
    val type: String,
    val answers: List<String>,
    val rawError: String? = null,
)

/**
 * DNS resolver using MiniDNS with the device/system resolver path.
 * Does not hardcode Google/Cloudflare resolvers.
 */
class DnsResolver {
    private val api: ResolverApi = ResolverApi.INSTANCE

    suspend fun resolveA(name: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val result = api.resolve(name, A::class.java)
            if (!result.wasSuccessful()) {
                return@withContext Result.failure(
                    IllegalStateException(result.responseCode?.name ?: "A lookup failed for $name"),
                )
            }
            Result.success(result.answersOrEmptySet.map { it.inetAddress.hostAddress!! })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveAAAA(name: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val result = api.resolve(name, AAAA::class.java)
            if (!result.wasSuccessful()) {
                return@withContext Result.failure(
                    IllegalStateException(result.responseCode?.name ?: "AAAA lookup failed for $name"),
                )
            }
            Result.success(result.answersOrEmptySet.map { it.inetAddress.hostAddress!! })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveSrv(name: String): Result<List<SrvRecord>> = withContext(Dispatchers.IO) {
        try {
            val result = api.resolve(name, SRV::class.java)
            if (!result.wasSuccessful()) {
                return@withContext Result.failure(
                    IllegalStateException(result.responseCode?.name ?: "SRV lookup failed for $name"),
                )
            }
            val records = result.answersOrEmptySet.map {
                SrvRecord(
                    target = it.target.toString().trimEnd('.'),
                    port = it.port,
                    priority = it.priority,
                    weight = it.weight,
                )
            }.sortedWith(compareBy<SrvRecord> { it.priority }.thenByDescending { it.weight })
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolvePtr(ip: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val arpa = toArpaName(ip)
            val result = api.resolve(arpa, PTR::class.java)
            if (!result.wasSuccessful()) {
                return@withContext Result.failure(
                    IllegalStateException(result.responseCode?.name ?: "PTR lookup failed for $ip"),
                )
            }
            Result.success(result.answersOrEmptySet.map { it.target.toString().trimEnd('.') })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun lookup(name: String, type: String): DnsLookupResult = withContext(Dispatchers.IO) {
        try {
            when (type.uppercase()) {
                "A" -> {
                    val r = resolveA(name)
                    DnsLookupResult(name, "A", r.getOrDefault(emptyList()), r.exceptionOrNull()?.message)
                }
                "AAAA" -> {
                    val r = resolveAAAA(name)
                    DnsLookupResult(name, "AAAA", r.getOrDefault(emptyList()), r.exceptionOrNull()?.message)
                }
                "SRV" -> {
                    val r = resolveSrv(name)
                    DnsLookupResult(
                        name,
                        "SRV",
                        r.getOrDefault(emptyList()).map { "${it.priority} ${it.weight} ${it.port} ${it.target}" },
                        r.exceptionOrNull()?.message,
                    )
                }
                "PTR" -> {
                    val r = resolvePtr(name)
                    DnsLookupResult(name, "PTR", r.getOrDefault(emptyList()), r.exceptionOrNull()?.message)
                }
                else -> DnsLookupResult(name, type, emptyList(), "Unsupported type $type")
            }
        } catch (e: Exception) {
            DnsLookupResult(name, type, emptyList(), e.message)
        }
    }

    fun toArpaName(ip: String): String {
        val address = InetAddress.getByName(ip)
        return when (address) {
            is Inet4Address -> {
                val parts = address.hostAddress!!.split('.')
                parts.reversed().joinToString(".") + ".in-addr.arpa"
            }
            is Inet6Address -> {
                val bytes = address.address
                val nibbles = buildString {
                    for (b in bytes) {
                        val v = b.toInt() and 0xff
                        append(Integer.toHexString(v ushr 4))
                        append(Integer.toHexString(v and 0x0f))
                    }
                }
                nibbles.reversed().toCharArray().joinToString(".") + ".ip6.arpa"
            }
            else -> error("Unsupported address type")
        }
    }
}
