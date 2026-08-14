package com.jbsan.ldapadvisor.data.diagnostics

import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class TcpPortProbe(
    val port: Int,
    val label: String,
)

class TcpDiagnosticService {
    companion object {
        val DEFAULT_PORTS = listOf(
            TcpPortProbe(53, "DNS TCP"),
            TcpPortProbe(88, "Kerberos"),
            TcpPortProbe(135, "RPC Endpoint Mapper"),
            TcpPortProbe(389, "LDAP"),
            TcpPortProbe(445, "SMB"),
            TcpPortProbe(636, "LDAPS"),
            TcpPortProbe(3268, "Global Catalog"),
            TcpPortProbe(3269, "Global Catalog TLS"),
            TcpPortProbe(5985, "WinRM HTTP"),
            TcpPortProbe(5986, "WinRM HTTPS"),
            TcpPortProbe(9389, "AD Web Services"),
        )
    }

    suspend fun probeHost(
        host: String,
        ports: List<TcpPortProbe> = DEFAULT_PORTS,
        timeoutMs: Int = 3_000,
        concurrency: Int = 4,
    ): List<DiagnosticTestResult> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(concurrency.coerceIn(1, 16))
        coroutineScope {
            ports.map { probe ->
                async {
                    semaphore.withPermit {
                        probePort(host, probe, timeoutMs)
                    }
                }
            }.awaitAll()
        }
    }

    private fun probePort(host: String, probe: TcpPortProbe, timeoutMs: Int): DiagnosticTestResult {
        val started = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, probe.port), timeoutMs)
                val completed = System.currentTimeMillis()
                DiagnosticTestResult(
                    id = "tcp-${probe.port}",
                    category = "TCP",
                    title = "TCP/${probe.port} ${probe.label}",
                    status = DiagnosticStatus.SUCCESS,
                    startedAt = started,
                    completedAt = completed,
                    durationMs = completed - started,
                    target = "$host:${probe.port}",
                    summary = "TCP/${probe.port} reachable",
                    recommendations = listOf(
                        "Reachability does not prove the application protocol is healthy.",
                    ),
                )
            }
        } catch (e: Exception) {
            val completed = System.currentTimeMillis()
            val summary = when {
                e.message?.contains("refused", true) == true -> "Connection refused"
                e.message?.contains("timed out", true) == true -> "Timeout"
                e is java.net.UnknownHostException -> "DNS failure"
                else -> e.message ?: "Closed / unreachable"
            }
            DiagnosticTestResult(
                id = "tcp-${probe.port}",
                category = "TCP",
                title = "TCP/${probe.port} ${probe.label}",
                status = DiagnosticStatus.ERROR,
                startedAt = started,
                completedAt = completed,
                durationMs = completed - started,
                target = "$host:${probe.port}",
                summary = summary,
                technicalDetails = e.message,
                probableCause = "Host unreachable, filtered, or service not listening",
            )
        }
    }
}
