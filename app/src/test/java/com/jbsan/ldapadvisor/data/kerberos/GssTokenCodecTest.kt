package com.jbsan.ldapadvisor.data.kerberos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GssTokenCodecTest {
    @Test
    fun encodeLengthShortAndLong() {
        assertArrayEquals(byteArrayOf(0x05), GssTokenCodec.encodeLength(5))
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0xC8.toByte()), GssTokenCodec.encodeLength(200))
    }

    @Test
    fun wrapInitialContextTokenStartsWithApplication0() {
        val inner = byteArrayOf(0x01, 0x02, 0x03)
        val token = GssTokenCodec.wrapInitialContextToken(GssTokenCodec.KERBEROS_V5_OID, inner)
        assertEquals(0x60.toByte(), token[0])
        // OID bytes follow length
        assertTrue(token.size > GssTokenCodec.KERBEROS_V5_OID.size)
    }

    @Test
    fun wrapKerberosApReqContainsTokId() {
        val apReq = byteArrayOf(0x6e, 0x02, 0x01, 0x00) // fake
        val token = GssTokenCodec.wrapKerberosApReq(apReq)
        assertEquals(0x60.toByte(), token[0])
        // Search for TOK_ID 01 00 after OID
        val oid = GssTokenCodec.KERBEROS_V5_OID
        val idx = indexOf(token, oid)
        assertTrue(idx >= 0)
        val afterOid = idx + oid.size
        assertEquals(0x01.toByte(), token[afterOid])
        assertEquals(0x00.toByte(), token[afterOid + 1])
    }

    @Test
    fun wrapSpnegoContainsSpnegoOidAndKerberosOid() {
        val token = GssTokenCodec.wrapSpnegoFromApReq(byteArrayOf(0x6e, 0x01, 0x05))
        assertEquals(0x60.toByte(), token[0])
        assertTrue(indexOf(token, GssTokenCodec.SPNEGO_OID) >= 0)
        assertTrue(indexOf(token, GssTokenCodec.KERBEROS_V5_OID) >= 0)
    }

    @Test
    fun normalizePrincipal() {
        assertEquals(
            "alice@CORP.EXAMPLE.COM",
            KerberosTicketService.normalizePrincipal("alice", "corp.example.com"),
        )
        assertEquals(
            "alice@CORP.EXAMPLE.COM",
            KerberosTicketService.normalizePrincipal("alice@corp.example.com", "OTHER.COM"),
        )
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
