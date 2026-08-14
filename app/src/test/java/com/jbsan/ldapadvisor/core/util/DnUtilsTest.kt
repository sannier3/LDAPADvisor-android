package com.jbsan.ldapadvisor.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnUtilsTest {
    @Test
    fun buildChildDnEscapesAndJoins() {
        val dn = DnUtils.buildChildDn("CN", "Doe, John", "OU=Users,DC=corp,DC=example,DC=com")
        assertEquals("CN=Doe\\, John,OU=Users,DC=corp,DC=example,DC=com", dn)
    }

    @Test
    fun buildChildDnForOu() {
        val dn = DnUtils.buildChildDn("OU", "Sales", "DC=corp,DC=example,DC=com")
        assertEquals("OU=Sales,DC=corp,DC=example,DC=com", dn)
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildChildDnRequiresParent() {
        DnUtils.buildChildDn("CN", "User", "  ")
    }

    @Test
    fun objectNameFromDn() {
        assertEquals("Alice", DnUtils.objectNameFromDn("CN=Alice,OU=Users,DC=corp,DC=example,DC=com"))
    }
}

class FavoritesFilterTest {
    @Test
    fun rejectsPasswordLikeFilters() {
        assertTrue(
            com.jbsan.ldapadvisor.data.repository.FavoritesRepository.looksLikePasswordFilter(
                "(unicodePwd=*)",
            ),
        )
        assertTrue(
            com.jbsan.ldapadvisor.data.repository.FavoritesRepository.looksLikePasswordFilter(
                "(password=secret)",
            ),
        )
        assertFalse(
            com.jbsan.ldapadvisor.data.repository.FavoritesRepository.looksLikePasswordFilter(
                "(sAMAccountName=alice)",
            ),
        )
    }
}

class CustomCaPrivateKeyGuardTest {
    @Test
    fun detectsPemPrivateKey() {
        val pem = "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----".toByteArray()
        assertTrue(
            com.jbsan.ldapadvisor.data.repository.CustomCaRepository.bytesLooksLikePrivateKey(pem),
        )
        assertFalse(
            com.jbsan.ldapadvisor.data.repository.CustomCaRepository.bytesLooksLikePrivateKey(
                "-----BEGIN CERTIFICATE-----\nBBBB\n-----END CERTIFICATE-----".toByteArray(),
            ),
        )
    }
}
