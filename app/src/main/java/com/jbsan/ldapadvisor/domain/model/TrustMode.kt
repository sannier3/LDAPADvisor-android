package com.jbsan.ldapadvisor.domain.model

/**
 * TLS trust strategy for a connection profile.
 *
 * [INSECURE_NO_VERIFY] skips certificate and hostname verification.
 * Credential binds still require an explicit per-connection confirmation.
 * Prefer [SYSTEM], [CUSTOM_CA], or [PINNED] whenever possible.
 */
enum class TrustMode {
    SYSTEM,
    CUSTOM_CA,
    PINNED,
    INSECURE_NO_VERIFY,
}

fun parseTrustMode(raw: String): TrustMode = when (raw) {
    // Legacy name from DB / older builds
    "DIAGNOSTIC_ONLY" -> TrustMode.INSECURE_NO_VERIFY
    else -> TrustMode.valueOf(raw)
}
