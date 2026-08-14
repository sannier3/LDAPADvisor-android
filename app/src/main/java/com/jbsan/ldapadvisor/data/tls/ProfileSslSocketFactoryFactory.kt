package com.jbsan.ldapadvisor.data.tls

import com.jbsan.ldapadvisor.core.util.FingerprintUtils
import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.TrustMode
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class SslSocketFactoryBundle(
    val socketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager,
    val hostnameVerifier: HostnameVerifier,
    val trustMode: TrustMode,
    val allowsCredentialBind: Boolean,
)

/**
 * Builds per-profile SSLContext. Never installs a process-global permissive TrustManager.
 * [TrustMode.INSECURE_NO_VERIFY] skips verification; credential binds still need explicit confirmation.
 */
class ProfileSslSocketFactoryFactory {

    fun create(
        trustMode: TrustMode,
        customCaCertificates: List<X509Certificate> = emptyList(),
        pinnedSha256Colon: String? = null,
        hostname: String,
    ): Result<SslSocketFactoryBundle> {
        return try {
            when (trustMode) {
                TrustMode.SYSTEM -> Result.success(systemBundle(hostname))
                TrustMode.CUSTOM_CA -> {
                    if (customCaCertificates.isEmpty()) {
                        return Result.failure(AppError.Validation("Custom CA certificate required"))
                    }
                    Result.success(customCaBundle(customCaCertificates, hostname))
                }
                TrustMode.PINNED -> {
                    val normalized = FingerprintUtils.normalizeSha256Fingerprint(pinnedSha256Colon.orEmpty())
                        ?: return Result.failure(AppError.Validation("Invalid pinned fingerprint"))
                    Result.success(pinnedBundle(normalized, hostname))
                }
                TrustMode.INSECURE_NO_VERIFY -> Result.success(insecureNoVerifyBundle())
            }
        } catch (e: Exception) {
            Result.failure(
                AppError.TlsHandshakeFailure(
                    message = "Failed to build TLS context",
                    technicalDetails = e.message,
                ),
            )
        }
    }

    private fun systemBundle(hostname: String): SslSocketFactoryBundle {
        val trustManager = systemTrustManager()
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return SslSocketFactoryBundle(
            socketFactory = ctx.socketFactory,
            trustManager = trustManager,
            hostnameVerifier = HostnameVerifierHelper.defaultVerifier(hostname),
            trustMode = TrustMode.SYSTEM,
            allowsCredentialBind = true,
        )
    }

    private fun customCaBundle(
        customCas: List<X509Certificate>,
        hostname: String,
    ): SslSocketFactoryBundle {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        customCas.forEachIndexed { index, cert ->
            keyStore.setCertificateEntry("custom-ca-$index", cert)
        }
        val systemTm = systemTrustManager()
        val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        customTmf.init(keyStore)
        val customTm = customTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        val combined = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                customTm.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    customTm.checkServerTrusted(chain, authType)
                } catch (_: CertificateException) {
                    systemTm.checkServerTrusted(chain, authType)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                (customTm.acceptedIssuers.toList() + systemTm.acceptedIssuers.toList()).toTypedArray()
        }

        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(combined), SecureRandom())
        return SslSocketFactoryBundle(
            socketFactory = ctx.socketFactory,
            trustManager = combined,
            hostnameVerifier = HostnameVerifierHelper.defaultVerifier(hostname),
            trustMode = TrustMode.CUSTOM_CA,
            allowsCredentialBind = true,
        )
    }

    private fun pinnedBundle(expectedSha256Colon: String, hostname: String): SslSocketFactoryBundle {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Client certificates are not supported")
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw CertificateException("Empty certificate chain")
                val actual = FingerprintUtils.x509Sha256Colon(chain[0])
                if (!actual.equals(expectedSha256Colon, ignoreCase = true)) {
                    throw CertificateException(
                        "Pinned certificate mismatch. expected=$expectedSha256Colon actual=$actual",
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return SslSocketFactoryBundle(
            socketFactory = ctx.socketFactory,
            trustManager = trustManager,
            hostnameVerifier = HostnameVerifierHelper.defaultVerifier(hostname),
            trustMode = TrustMode.PINNED,
            allowsCredentialBind = true,
        )
    }

    private fun insecureNoVerifyBundle(): SslSocketFactoryBundle {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return SslSocketFactoryBundle(
            socketFactory = ctx.socketFactory,
            trustManager = trustManager,
            hostnameVerifier = { _, _ -> true },
            trustMode = TrustMode.INSECURE_NO_VERIFY,
            allowsCredentialBind = true,
        )
    }

    private fun systemTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
