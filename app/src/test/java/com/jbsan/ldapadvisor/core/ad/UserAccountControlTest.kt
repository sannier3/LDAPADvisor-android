package com.jbsan.ldapadvisor.core.ad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAccountControlTest {
    @Test
    fun decodeNormalEnabledAccount() {
        val decoded = UserAccountControl.decode(0x0200)
        assertTrue(decoded.enabled)
        assertTrue(decoded.normalAccount)
        assertTrue(decoded.flags.contains("NORMAL_ACCOUNT"))
    }

    @Test
    fun decodeDisabledAndDontExpire() {
        val raw = UserAccountControl.NORMAL_ACCOUNT or
            UserAccountControl.ACCOUNTDISABLE or
            UserAccountControl.DONT_EXPIRE_PASSWORD
        val decoded = UserAccountControl.decode(raw)
        assertFalse(decoded.enabled)
        assertTrue(decoded.passwordNeverExpires)
        assertTrue(decoded.flags.contains("ACCOUNTDISABLE"))
        assertTrue(decoded.flags.contains("DONT_EXPIRE_PASSWORD"))
    }

    @Test
    fun setDisabledPreservesOtherFlags() {
        val raw = UserAccountControl.NORMAL_ACCOUNT or UserAccountControl.DONT_EXPIRE_PASSWORD
        val disabled = UserAccountControl.setDisabled(raw, true)
        assertEquals(raw or UserAccountControl.ACCOUNTDISABLE, disabled)
        val enabled = UserAccountControl.setDisabled(disabled, false)
        assertEquals(raw, enabled)
    }
}
