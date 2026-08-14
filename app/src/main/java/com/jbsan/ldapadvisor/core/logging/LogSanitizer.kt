package com.jbsan.ldapadvisor.core.logging

object LogSanitizer {
    private val SENSITIVE_KEYS = listOf(
        "password",
        "passwd",
        "unicodepwd",
        "secret",
        "authorization",
        "private key",
        "privatekey",
        "client-secret",
        "bindpw",
        "credentials",
    )

    private val KEY_VALUE = Regex(
        pattern = "(?i)(${SENSITIVE_KEYS.joinToString("|") { Regex.escape(it) }})\\s*([:=])\\s*([^\\s,;]+)",
    )
    private val JSONISH = Regex(
        pattern = "(?i)(\"(?:${SENSITIVE_KEYS.joinToString("|") { Regex.escape(it) }})\"\\s*:\\s*\")([^\"]*)(\")",
    )
    private val PEM_PRIVATE = Regex(
        pattern = "-----BEGIN ([A-Z0-9 ]*PRIVATE KEY)-----[\\s\\S]*?-----END \\1-----",
        option = RegexOption.IGNORE_CASE,
    )

    fun sanitize(input: String): String {
        var out = input
        out = PEM_PRIVATE.replace(out, "[REDACTED_PRIVATE_KEY]")
        out = JSONISH.replace(out, "$1[REDACTED]$3")
        out = KEY_VALUE.replace(out, "$1$2[REDACTED]")
        return out
    }

    fun containsSensitiveHint(input: String): Boolean {
        val lower = input.lowercase()
        return SENSITIVE_KEYS.any { lower.contains(it) }
    }
}
