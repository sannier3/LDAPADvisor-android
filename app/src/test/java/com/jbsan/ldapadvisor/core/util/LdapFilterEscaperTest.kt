package com.jbsan.ldapadvisor.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LdapFilterEscaperTest {
    @Test
    fun escapesSpecialCharacters() {
        assertEquals("a\\2ab\\28c\\29d\\5ce\\00f", LdapFilterEscaper.escapeFilterValue("a*b(c)d\\e\u0000f"))
    }

    @Test
    fun buildsEqualsFilter() {
        assertEquals("(cn=John\\2aDoe)", LdapFilterEscaper.equalsFilter("cn", "John*Doe"))
    }

    @Test
    fun escapesBinaryForObjectSidFilter() {
        val bytes = byteArrayOf(0x01, 0x05, 0xff.toByte())
        assertEquals("\\01\\05\\ff", LdapFilterEscaper.escapeBinary(bytes))
        assertEquals(
            "(objectSid=\\01\\05\\ff)",
            LdapFilterEscaper.equalsBinaryFilter("objectSid", bytes),
        )
    }

    @Test
    fun dnEscapeAndValidation() {
        assertEquals("cn=Doe\\, John", DnUtils.rdn("cn", "Doe, John"))
        assertTrue(DnUtils.isPlausibleDn("CN=User,OU=Users,DC=corp,DC=example,DC=com"))
        assertTrue(DnUtils.isPlausibleDn(""))
    }
}
