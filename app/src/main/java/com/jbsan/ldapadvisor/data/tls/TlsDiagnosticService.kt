package com.jbsan.ldapadvisor.data.tls

import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import com.jbsan.ldapadvisor.domain.model.TrustMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

data class TlsHandshakeInfo(
    val protocol: String?,
    val cipherSuite: String?,
    val handshakeDurationMs: Long,
    val certificates: List<CertificateParser.ParsedCertificate>,
    val hostnameMatched: Boolean?,
)

class TlsDiagnosticService(
    private val sslFactoryFactory: ProfileSslSocketFactoryFactory = ProfileSslSocketFactoryFactory(),
) {
    suspend fun probe(
        host: String,
        port: Int,
        trustMode: TrustMode = TrustMode.INSECURE_NO_VERIFY,
        customCas: List<X509Certificate> = emptyList(),
        pinnedFingerprint: String? = null,
        connectTimeoutMs: Int = 5_000,
    ): Result<TlsHandshakeInfo> = withContext(Dispatchers.IO) {
        val bundleResult = sslFactoryFactory.create(
            trustMode = trustMode,
            customCaCertificates = customCas,
            pinnedSha256Colon = pinnedFingerprint,
            hostname = host,
        )
        val bundle = bundleResult.getOrElse { return@withContext Result.failure(it) }
        try {
            val started = System.currentTimeMillis()
            bundle.socketFactory.createSocket().use { raw ->
                val socket = raw as SSLSocket
                socket.soTimeout = connectTimeoutMs
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.startHandshake()
                val duration = System.currentTimeMillis() - started
                val session = socket.session
                val certs = session.peerCertificates
                    .filterIsInstance<X509Certificate>()
                    .map(CertificateParser::toParsed)
                val hostnameOk = try {
                    bundle.hostnameVerifier.verify(host, session)
                } catch (_: Exception) {
                    null
                }
                Result.success(
                    TlsHandshakeInfo(
                        protocol = session.protocol,
                        cipherSuite = session.cipherSuite,
                        handshakeDurationMs = duration,
                        certificates = certs,
                        hostnameMatched = hostnameOk,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(
                AppError.TlsHandshakeFailure(
                    message = "TLS probe failed for $host:$port",
                    technicalDetails = e.message,
                ),
            )
        }
    }

    suspend fun asDiagnosticResult(
        host: String,
        port: Int,
        trustMode: TrustMode = TrustMode.INSECURE_NO_VERIFY,
    ): DiagnosticTestResult {
        val started = System.currentTimeMillis()
        val result = probe(host, port, trustMode)
        val completed = System.currentTimeMillis()
        return result.fold(
            onSuccess = { info ->
                val leaf = info.certificates.firstOrNull()
                val now = System.currentTimeMillis()
                val status = when {
                    leaf != null && leaf.notAfterEpochMs < now -> DiagnosticStatus.ERROR
                    info.hostnameMatched == false -> DiagnosticStatus.ERROR
                    leaf != null && leaf.notAfterEpochMs - now < 30L * 24 * 60 * 60 * 1000 -> DiagnosticStatus.WARNING
                    else -> DiagnosticStatus.SUCCESS
                }
                DiagnosticTestResult(
                    id = "tls-handshake",
                    category = "TLS",
                    title = "TLS handshake",
                    status = status,
                    startedAt = started,
                    completedAt = completed,
                    durationMs = completed - started,
                    target = "$host:$port",
                    summary = "protocol=${info.protocol} cipher=${info.cipherSuite}",
                    technicalDetails = leaf?.let {
                        "subject=${it.subject}; fingerprint=${it.sha256Fingerprint}; hostnameMatched=${info.hostnameMatched}"
                    },
                    evidence = info.certificates.map { "${it.subject} (${it.sha256Fingerprint})" },
                )
            },
            onFailure = { err ->
                DiagnosticTestResult(
                    id = "tls-handshake",
                    category = "TLS",
                    title = "TLS handshake",
                    status = DiagnosticStatus.ERROR,
                    startedAt = started,
                    completedAt = completed,
                    durationMs = completed - started,
                    target = "$host:$port",
                    summary = err.message ?: "TLS failure",
                    technicalDetails = (err as? AppError)?.technicalDetails ?: err.message,
                )
            },
        )
    }
}
