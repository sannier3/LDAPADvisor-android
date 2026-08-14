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
    fun extractApRepDerFromKerberosIct() {
        val apRep = byteArrayOf(0x6f.toByte(), 0x03, 0x01, 0x02, 0x03)
        val inner = byteArrayOf(0x02, 0x00) + apRep
        val token = GssTokenCodec.wrapInitialContextToken(GssTokenCodec.KERBEROS_V5_OID, inner)
        assertArrayEquals(apRep, GssTokenCodec.extractApRepDer(token))
    }

    @Test
    fun extractApRepDerFromSpnegoNegTokenResp() {
        val apRep = byteArrayOf(0x6f.toByte(), 0x02, 0x05, 0x06)
        val kerberosInner = byteArrayOf(0x02, 0x00) + apRep
        val kerberosIct = GssTokenCodec.wrapInitialContextToken(GssTokenCodec.KERBEROS_V5_OID, kerberosInner)
        val responseTokenField = GssTokenCodec.encodeTagged(0xa2, kerberosIct)
        val negState = GssTokenCodec.encodeTagged(0xa0, byteArrayOf(0x0a, 0x01, 0x00)) // accept-completed
        val negTokenResp = GssTokenCodec.encodeTagged(0xa1, negState + responseTokenField)
        val spnegoIct = GssTokenCodec.wrapInitialContextToken(GssTokenCodec.SPNEGO_OID, negTokenResp)
        assertArrayEquals(apRep, GssTokenCodec.extractApRepDer(spnegoIct))
    }

    @Test
    fun wrapRoundTripNoConfidentiality() {
        // Smoke: encode NegTokenInit still starts with ICT APPLICATION 0 after CHOICE fix
        val token = GssTokenCodec.wrapSpnegoFromApReq(byteArrayOf(0x6e, 0x01, 0x05))
        assertEquals(0x60.toByte(), token[0])
        assertTrue(indexOf(token, GssTokenCodec.SPNEGO_OID) >= 0)
        // NegTokenInit CHOICE [0] should appear after SPNEGO OID
        val oidIdx = indexOf(token, GssTokenCodec.SPNEGO_OID)
        assertTrue(oidIdx >= 0)
        assertEquals(0xa0.toByte(), token[oidIdx + GssTokenCodec.SPNEGO_OID.size])
    }

    @Test
    fun unwrapInitialContextTokenInnerStripsOid() {
        val inner = byteArrayOf(0x05, 0x04, 0x01, 0xFF.toByte())
        val ict = GssTokenCodec.wrapInitialContextToken(GssTokenCodec.KERBEROS_V5_OID, inner)
        assertArrayEquals(inner, GssTokenCodec.unwrapInitialContextTokenInner(ict))
        assertArrayEquals(inner, GssTokenCodec.unwrapInitialContextTokenInner(inner))
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
