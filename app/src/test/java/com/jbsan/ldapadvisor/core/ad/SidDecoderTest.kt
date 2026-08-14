package com.jbsan.ldapadvisor.core.ad

import org.junit.Assert.assertEquals
import org.junit.Test

class SidDecoderTest {
    @Test
    fun decodeEveryone() {
        // S-1-1-0
        val bytes = byteArrayOf(
            0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x00,
        )
        assertEquals("S-1-1-0", SidDecoder.decode(bytes))
    }

    @Test
    fun decodeWorldAuthorityWithSubAuth() {
        // S-1-5-32-544 (Administrators)
        val bytes = byteArrayOf(
            0x01, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05,
            0x20, 0x00, 0x00, 0x00,
            0x20, 0x02, 0x00, 0x00,
        )
        assertEquals("S-1-5-32-544", SidDecoder.decode(bytes))
    }

    @Test
    fun decodeDomainSidStyle() {
        // S-1-5-21-1-2-3-1001
        val bytes = byteArrayOf(
            0x01, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05,
            0x15, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x00,
            0x03, 0x00, 0x00, 0x00,
            0xe9.toByte(), 0x03, 0x00, 0x00,
        )
        assertEquals("S-1-5-21-1-2-3-1001", SidDecoder.decode(bytes))
        assertEquals(1001, SidDecoder.extractRid(bytes))
    }

    @Test
    fun extractRidAdministrators() {
        val bytes = byteArrayOf(
            0x01, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05,
            0x20, 0x00, 0x00, 0x00,
            0x20, 0x02, 0x00, 0x00,
        )
        assertEquals(544, SidDecoder.extractRid(bytes))
    }

    @Test
    fun withRidBuildsPrimaryGroupSid() {
        // S-1-5-21-1-2-3-1001 → replace RID with 513 (Domain Users)
        val userSid = byteArrayOf(
            0x01, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05,
            0x15, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x00,
            0x03, 0x00, 0x00, 0x00,
            0xe9.toByte(), 0x03, 0x00, 0x00,
        )
        val groupSid = SidDecoder.withRid(userSid, 513)
        assertEquals("S-1-5-21-1-2-3-513", SidDecoder.decode(groupSid))
        assertEquals(513, SidDecoder.extractRid(groupSid))
        // Original unchanged
        assertEquals("S-1-5-21-1-2-3-1001", SidDecoder.decode(userSid))
    }
}
