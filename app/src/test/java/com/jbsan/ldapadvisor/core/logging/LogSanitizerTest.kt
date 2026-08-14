package com.jbsan.ldapadvisor.core.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test
    fun redactsPasswordAndUnicodePwd() {
        val input = "bind password=SuperSecret unicodePwd=abc Authorization: Bearer tok private key material"
        val out = LogSanitizer.sanitize(input)
        assertFalse(out.contains("SuperSecret"))
        assertTrue(out.contains("[REDACTED]"))
        assertTrue(out.lowercase().contains("password"))
    }

    @Test
    fun redactsPemPrivateKey() {
        val pem = """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC7
            -----END PRIVATE KEY-----
        """.trimIndent()
        val out = LogSanitizer.sanitize(pem)
        assertTrue(out.contains("[REDACTED_PRIVATE_KEY]"))
        assertFalse(out.contains("MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC7"))
    }
}
