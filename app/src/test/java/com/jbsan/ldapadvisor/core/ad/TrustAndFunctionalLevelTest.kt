package com.jbsan.ldapadvisor.core.ad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustAndFunctionalLevelTest {
    @Test
    fun trustDecoders() {
        val decoded = TrustDecoders.decode(
            direction = TrustDecoders.Direction.BIDIRECTIONAL,
            type = TrustDecoders.Type.UPLEVEL,
            attributes = TrustDecoders.Attributes.WITHIN_FOREST or TrustDecoders.Attributes.FOREST_TRANSITIVE,
        )
        assertEquals("Bidirectional", decoded.direction)
        assertEquals("Uplevel (AD)", decoded.type)
        assertTrue(decoded.attributes.contains("WITHIN_FOREST"))
        assertTrue(decoded.attributes.contains("FOREST_TRANSITIVE"))
    }

    @Test
    fun functionalLevels() {
        assertEquals("Windows Server 2016", FunctionalLevelDecoder.label(7))
        assertEquals("Unknown", FunctionalLevelDecoder.label(null as Int?))
    }
}
