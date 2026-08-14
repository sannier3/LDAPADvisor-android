package com.jbsan.ldapadvisor.core.ad

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupTypeDecoderTest {
    @Test
    fun securityGlobal() {
        val raw = GroupTypeDecoder.GLOBAL or GroupTypeDecoder.SECURITY
        val decoded = GroupTypeDecoder.decode(raw)
        assertEquals(GroupTypeDecoder.Scope.GLOBAL, decoded.scope)
        assertEquals(GroupTypeDecoder.Category.SECURITY, decoded.category)
    }

    @Test
    fun distributionUniversal() {
        val raw = GroupTypeDecoder.UNIVERSAL
        val decoded = GroupTypeDecoder.decode(raw)
        assertEquals(GroupTypeDecoder.Scope.UNIVERSAL, decoded.scope)
        assertEquals(GroupTypeDecoder.Category.DISTRIBUTION, decoded.category)
    }

    @Test
    fun encodeRoundTrip() {
        val encoded = GroupTypeDecoder.encode(GroupTypeDecoder.Scope.DOMAIN_LOCAL, security = true)
        val decoded = GroupTypeDecoder.decode(encoded)
        assertEquals(GroupTypeDecoder.Scope.DOMAIN_LOCAL, decoded.scope)
        assertEquals(GroupTypeDecoder.Category.SECURITY, decoded.category)
    }
}
