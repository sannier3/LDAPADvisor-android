package com.jbsan.ldapadvisor.core.ad

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidDecoderTest {
    @Test
    fun decodeMixedEndianObjectGuid() {
        // UUID 3f2504e0-4f89-11d3-9a0c-0305e82c3301 encoded as AD objectGUID bytes
        val uuid = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
        val bytes = byteArrayOf(
            0xe0.toByte(), 0x04, 0x25, 0x3f,
            0x89.toByte(), 0x4f,
            0xd3.toByte(), 0x11,
            0x9a.toByte(), 0x0c, 0x03, 0x05, 0xe8.toByte(), 0x2c, 0x33, 0x01,
        )
        assertEquals(uuid, GuidDecoder.decode(bytes))
    }
}
