package com.jbsan.ldapadvisor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrustModeParseTest {
    @Test
    fun mapsLegacyDiagnosticOnlyToInsecure() {
        assertEquals(TrustMode.INSECURE_NO_VERIFY, parseTrustMode("DIAGNOSTIC_ONLY"))
    }

    @Test
    fun parsesCurrentValues() {
        assertEquals(TrustMode.SYSTEM, parseTrustMode("SYSTEM"))
        assertEquals(TrustMode.CUSTOM_CA, parseTrustMode("CUSTOM_CA"))
        assertEquals(TrustMode.PINNED, parseTrustMode("PINNED"))
        assertEquals(TrustMode.INSECURE_NO_VERIFY, parseTrustMode("INSECURE_NO_VERIFY"))
    }
}
