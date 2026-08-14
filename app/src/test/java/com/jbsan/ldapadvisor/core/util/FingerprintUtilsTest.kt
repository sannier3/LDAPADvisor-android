package com.jbsan.ldapadvisor.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintUtilsTest {
    @Test
    fun sha256ColonFormat() {
        val fp = FingerprintUtils.sha256Colon("abc".toByteArray())
        assertEquals(32 * 3 - 1, fp.length) // 32 bytes -> 32 hex pairs with colons
        assertTrue(fp.contains(":"))
        assertEquals(fp, FingerprintUtils.normalizeSha256Fingerprint(fp.replace(":", "")))
    }
}
