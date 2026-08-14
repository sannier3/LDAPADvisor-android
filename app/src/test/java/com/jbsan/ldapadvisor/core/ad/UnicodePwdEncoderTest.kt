package com.jbsan.ldapadvisor.core.ad

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class UnicodePwdEncoderTest {
    @Test
    fun encodesQuotedUtf16Le() {
        val encoded = UnicodePwdEncoder.encode("Secret123!")
        val expected = "\"Secret123!\"".toByteArray(StandardCharsets.UTF_16LE)
        assertArrayEquals(expected, encoded)
        assertEquals(expected.size, encoded.size)
    }
}
