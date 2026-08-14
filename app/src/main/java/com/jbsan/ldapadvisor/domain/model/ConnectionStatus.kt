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
    ) : ConnectionStatus()

    data class Error(val error: AppError) : ConnectionStatus()
}
