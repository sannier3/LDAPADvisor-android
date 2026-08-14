package com.jbsan.ldapadvisor.data.kerberos

import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey
import org.apache.kerby.kerberos.kerb.type.base.EncryptionType

/**
 * Dispatch Wrap unwrap/wrap between RFC 4121 CFX (AES) and RFC 1964/4757 (RC4 / AD SASL).
 */
object GssWrapToken {
    fun unwrapPayload(token: ByteArray, sessionKey: EncryptionKey, fromAcceptor: Boolean): ByteArray {
        val inner = GssTokenCodec.unwrapInitialContextTokenInner(token)
        require(inner.isNotEmpty()) { "Empty Wrap token after ICT unwrap" }
        return when {
            inner[0] == 0x05.toByte() && inner.size > 1 && inner[1] == 0x04.toByte() ->
                GssWrapTokenCfx.unwrapPayload(token, sessionKey, fromAcceptor)
            inner[0] == 0x02.toByte() && inner.size > 1 && inner[1] == 0x01.toByte() ->
                GssWrapTokenRfc1964.unwrapPayload(token, sessionKey, fromAcceptor)
            else -> error(
                "Unrecognized Wrap TOK_ID " +
                    "(${inner[0].toInt() and 0xff}/${inner.getOrNull(1)?.toInt()?.and(0xff)}); " +
                    "head=${inner.take(8).joinToString("") { "%02x".format(it) }} size=${inner.size} " +
                    "keyType=${sessionKey.keyType}",
            )
        }
    }

    fun wrapPayload(
        payload: ByteArray,
        sessionKey: EncryptionKey,
        fromAcceptor: Boolean,
        seq: Long = 0L,
        wrapInInitialContextToken: Boolean = true,
        preferRfc1964: Boolean = false,
    ): ByteArray {
        val use1964 = preferRfc1964 ||
            sessionKey.keyType == EncryptionType.ARCFOUR_HMAC ||
            sessionKey.keyType == EncryptionType.ARCFOUR_HMAC_EXP
        return if (use1964) {
            GssWrapTokenRfc1964.wrapPayload(
                payload = payload,
                key = sessionKey,
                fromAcceptor = fromAcceptor,
                seq = seq,
                wrapInInitialContextToken = wrapInInitialContextToken,
            )
        } else {
            GssWrapTokenCfx.wrapPayload(
                payload = payload,
                sessionKey = sessionKey,
                fromAcceptor = fromAcceptor,
                seq = seq,
                wrapInInitialContextToken = wrapInInitialContextToken,
            )
        }
    }
}
