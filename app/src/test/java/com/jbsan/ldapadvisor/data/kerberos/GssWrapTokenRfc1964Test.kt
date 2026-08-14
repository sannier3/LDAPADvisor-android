package com.jbsan.ldapadvisor.data.kerberos

import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey
import org.apache.kerby.kerberos.kerb.type.base.EncryptionType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GssWrapTokenRfc1964Test {
    @Test
    fun wrapRoundTrip_noSecurityLayerOffer() {
        val key = EncryptionKey(EncryptionType.ARCFOUR_HMAC, ByteArray(16) { (it + 1).toByte() })
        val payload = byteArrayOf(0x07, 0x00, 0x00, 0x00) // typical AD offer mask
        val wrapped = GssWrapTokenRfc1964.wrapPayload(
            payload = payload,
            key = key,
            fromAcceptor = true,
            seq = 0L,
        )
        assertTrue(GssWrapTokenRfc1964.canUnwrap(wrapped))
        val unwrapped = GssWrapTokenRfc1964.unwrapPayload(wrapped, key, fromAcceptor = true)
        assertArrayEquals(payload, unwrapped)
    }

    @Test
    fun wrapRoundTrip_clientNoLayerResponse() {
        val key = EncryptionKey(EncryptionType.ARCFOUR_HMAC, ByteArray(16) { 0xab.toByte() })
        val payload = byteArrayOf(0x01, 0x00, 0x00, 0x00)
        val wrapped = GssWrapTokenRfc1964.wrapPayload(
            payload = payload,
            key = key,
            fromAcceptor = false,
            seq = 0L,
        )
        val unwrapped = GssWrapTokenRfc1964.unwrapPayload(wrapped, key, fromAcceptor = false)
        assertArrayEquals(payload, unwrapped)
    }
}
