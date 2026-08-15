package com.jbsan.ldapadvisor.domain.model

sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data class Connected(
        val profileId: String,
        val host: String,
        val port: Int,
        val securityMode: SecurityMode,
        val boundAs: String?,
        val tlsActive: Boolean,
        val responseTimeMs: Long? = null,
        val networkLost: Boolean = false,
        /** True when the profile uses TrustMode.INSECURE_NO_VERIFY. */
        val insecureTrust: Boolean = false,
        /** True when the session was authenticated with Kerberos/GSSAPI. */
        val kerberosBound: Boolean = false,
    ) : ConnectionStatus() {
        /** Password ops allowed over TLS or Kerberos SASL. */
        val allowsPasswordChannel: Boolean get() = tlsActive || kerberosBound
    }

    data class Error(val error: AppError) : ConnectionStatus()
}
