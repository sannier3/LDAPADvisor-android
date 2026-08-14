package com.jbsan.ldapadvisor.data.kerberos

import org.apache.kerby.kerberos.kerb.KrbCodec
import org.apache.kerby.kerberos.kerb.common.EncryptionUtil
import org.apache.kerby.kerberos.kerb.type.KerberosTime
import org.apache.kerby.kerberos.kerb.type.ap.ApRep
import org.apache.kerby.kerberos.kerb.type.ap.EncAPRepPart
import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey
import org.apache.kerby.kerberos.kerb.type.base.KeyUsage

/**
 * Holds the service-ticket session key and AP-REQ authenticator clock values
 * so the client can verify the server's mutual-auth AP-REP and complete
 * RFC 4752 GSSAPI SASL security-layer negotiation.
 */
class KerberosMutualAuthState(
    private val sessionKey: EncryptionKey,
    private val expectedCtime: KerberosTime,
    private val expectedCusec: Int,
) {
    private var initiatorWrapSeq: Long = 0L
    private var wrapKey: EncryptionKey = sessionKey
    private var lastServerUsedRfc1964: Boolean = false

    fun verifyServerSaslCredentials(serverToken: ByteArray) {
        val apRepDer = GssTokenCodec.extractApRepDer(serverToken)
        val apRep = KrbCodec.decode(apRepDer, ApRep::class.java)
        val encPart = EncryptionUtil.unseal(
            apRep.encryptedEncPart,
            sessionKey,
            KeyUsage.AP_REP_ENCPART,
            EncAPRepPart::class.java,
        )
        val ctime = encPart.ctime
            ?: error("AP-REP missing ctime")
        val cusec = encPart.cusec
        if (!ctimeEquals(ctime, expectedCtime) || cusec != expectedCusec) {
            error(
                "AP-REP authenticator mismatch " +
                    "(got ctime=${ctime.time} cusec=$cusec, " +
                    "expected ctime=${expectedCtime.time} cusec=$expectedCusec)",
            )
        }
        // Prefer AP-REP subkey for subsequent GSS Wrap (common on AD).
        val sub = try {
            encPart.subkey
        } catch (_: Exception) {
            null
        }
        wrapKey = sub ?: sessionKey
    }

    /**
     * After the empty second SASL bind, AD sends a wrapped security-layer offer.
     * Build the client wrap response selecting **no** SASL security layer (0x01).
     */
    fun buildSaslNoSecurityLayerResponse(serverWrapToken: ByteArray): ByteArray {
        lastServerUsedRfc1964 = GssWrapTokenRfc1964.canUnwrap(serverWrapToken)
        // AD may sign the wrap offer with either the AP-REP subkey or the ticket session key.
        val keysToTry = linkedSetOf(wrapKey, sessionKey)
        var offer: ByteArray? = null
        var keyUsed: EncryptionKey = wrapKey
        var lastError: Throwable? = null
        for (key in keysToTry) {
            try {
                offer = GssWrapToken.unwrapPayload(
                    token = serverWrapToken,
                    sessionKey = key,
                    fromAcceptor = true,
                )
                keyUsed = key
                wrapKey = key
                break
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (offer == null) {
            throw IllegalStateException(
                "SASL wrap unwrap failed with subkey and session key: ${lastError?.message}",
                lastError,
            )
        }
        require(offer.size == 4) {
            "SASL security-layer offer must be 4 bytes, got ${offer.size}"
        }
        val serverLayers = offer[0].toInt() and 0xff
        require(serverLayers and 0x01 != 0) {
            "Server does not offer 'no security layer' (mask=0x${serverLayers.toString(16)})"
        }
        val response = byteArrayOf(
            0x01,
            0x00, 0x00, 0x00,
        )
        val seq = initiatorWrapSeq
        initiatorWrapSeq += 1
        return GssWrapToken.wrapPayload(
            payload = response,
            sessionKey = keyUsed,
            fromAcceptor = false,
            seq = seq,
            preferRfc1964 = lastServerUsedRfc1964,
        )
    }

    fun wrapKeyTypeLabel(): String =
        "session=${sessionKey.keyType} wrap=${wrapKey.keyType}"

    fun clear() {
        try {
            sessionKey.keyData?.fill(0)
            wrapKey.keyData?.fill(0)
        } catch (_: Exception) {
            // best-effort wipe
        }
    }

    private fun ctimeEquals(a: KerberosTime, b: KerberosTime): Boolean {
        if (a === b) return true
        return try {
            a.time == b.time
        } catch (_: Exception) {
            a.toString() == b.toString()
        }
    }
}
