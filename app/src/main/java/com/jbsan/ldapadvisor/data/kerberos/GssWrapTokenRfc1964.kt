package com.jbsan.ldapadvisor.data.kerberos

import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey
import org.apache.kerby.kerberos.kerb.type.base.EncryptionType
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 1964 / RFC 4757 Wrap tokens (TOK_ID 0x0201) with SEAL_ALG_NONE,
 * as used by Active Directory LDAP SASL security-layer negotiation when
 * the context key is RC4-HMAC (SGN_ALG 0x0011).
 *
 * Checksum / seq crypto follow OpenJDK ArcFourCrypto (MS little-endian usage salt).
 */
object GssWrapTokenRfc1964 {
    private const val TOK_ID_0: Byte = 0x02
    private const val TOK_ID_1: Byte = 0x01
    private const val SGN_ALG_HMAC_MD5_ARCFOUR: Int = 0x0011
    private const val SEAL_ALG_NONE: Int = 0xffff
    private const val CKSUM_LEN = 8
    private const val CONF_LEN = 8
    private const val SEQ_LEN = 8

    /** OpenJDK KG_USAGE_SIGN — ArcFourCrypto maps 23 → MS usage 13 for Wrap. */
    private const val USAGE_SIGN = 23

    /** MS / RFC 4757 Wrap usage (also tried directly). */
    private const val USAGE_WRAP_MS = 13

    /** MS MIC usage (interop). */
    private const val USAGE_MIC_MS = 15

    fun canUnwrap(token: ByteArray): Boolean {
        val inner = GssTokenCodec.unwrapInitialContextTokenInner(token)
        return inner.size >= 16 && inner[0] == TOK_ID_0 && inner[1] == TOK_ID_1
    }

    fun unwrapPayload(token: ByteArray, key: EncryptionKey, fromAcceptor: Boolean): ByteArray {
        val raw = GssTokenCodec.unwrapInitialContextTokenInner(token)
        require(raw.size >= 16 + CKSUM_LEN + CONF_LEN + 1) {
            "RFC1964 Wrap too short (${raw.size})"
        }
        require(raw[0] == TOK_ID_0 && raw[1] == TOK_ID_1)
        val sgnAlg = u16Le(raw, 2)
        require(sgnAlg == SGN_ALG_HMAC_MD5_ARCFOUR) {
            "Unsupported SGN_ALG=0x${sgnAlg.toString(16)} (expected HMAC-MD5-ARCFOUR)"
        }
        val sealAlg = u16Le(raw, 4)
        require(sealAlg == SEAL_ALG_NONE) {
            "Sealed RFC1964 Wrap not supported (seal=0x${sealAlg.toString(16)})"
        }

        val header8 = raw.copyOfRange(0, 8)
        val encSeq = raw.copyOfRange(8, 16)
        val cksum = raw.copyOfRange(16, 16 + CKSUM_LEN)
        val rest = raw.copyOfRange(16 + CKSUM_LEN, raw.size)
        require(rest.size >= CONF_LEN + 1) { "Missing confounder/data in Wrap" }
        val confounder = rest.copyOfRange(0, CONF_LEN)
        val body = rest.copyOfRange(CONF_LEN, rest.size) // data || pad
        require(body.isNotEmpty()) { "Empty Wrap body" }

        val kss = rc4KeyBytes(key)
        val matched = findMatchingChecksum(kss, header8, confounder, body, cksum)
            ?: error(
                "RFC1964 Wrap checksum mismatch " +
                    "(hdr=${header8.toHex()} bodyLen=${body.size} keyLen=${kss.size})",
            )
        val matchedUsage = matched.usage

        val seqPlain = rc4(hmacMd5(hmacMd5(kss, byteArrayOf(0, 0, 0, 0)), cksum), encSeq)
        val dir = seqPlain.copyOfRange(4, 8)
        val expectDirAcceptor = byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val expectDirInitiator = byteArrayOf(0, 0, 0, 0)
        val dirOk = if (fromAcceptor) {
            dir.contentEquals(expectDirAcceptor) || dir.contentEquals(expectDirInitiator)
        } else {
            dir.contentEquals(expectDirInitiator) || dir.contentEquals(expectDirAcceptor)
        }
        require(dirOk) {
            "RFC1964 Wrap direction mismatch (got=${dir.toHex()} usage=$matchedUsage)"
        }

        val pad = body.last().toInt() and 0xff
        require(pad in 1..body.size) { "Invalid Wrap pad=$pad" }
        // Remember usage/endian for generating the client response.
        lastMatchedUsage = matchedUsage
        lastMatchedLittleEndian = matched.littleEndian
        return body.copyOfRange(0, body.size - pad)
    }

    @Volatile
    private var lastMatchedUsage: Int = USAGE_WRAP_MS

    @Volatile
    private var lastMatchedLittleEndian: Boolean = true

    private data class ChecksumMatch(val usage: Int, val littleEndian: Boolean)

    fun wrapPayload(
        payload: ByteArray,
        key: EncryptionKey,
        fromAcceptor: Boolean,
        seq: Long,
        wrapInInitialContextToken: Boolean = true,
    ): ByteArray {
        val kss = rc4KeyBytes(key)
        val confounder = ByteArray(CONF_LEN).also { SecureRandom().nextBytes(it) }
        val pad = byteArrayOf(0x01)
        val body = payload + pad
        val header8 = ByteArray(8)
        header8[0] = TOK_ID_0
        header8[1] = TOK_ID_1
        putU16Le(header8, 2, SGN_ALG_HMAC_MD5_ARCFOUR)
        putU16Le(header8, 4, SEAL_ALG_NONE)
        // AD sometimes sends filler 0000; RFC filler is FFFF — use FFFF for our tokens.
        header8[6] = 0xff.toByte()
        header8[7] = 0xff.toByte()

        val usage = lastMatchedUsage
        val cksum = md5HmacChecksum(
            kss = kss,
            usage = usage,
            toSign = header8 + confounder + body,
            littleEndian = lastMatchedLittleEndian,
        )

        val seqPlain = ByteArray(SEQ_LEN)
        putU32Be(seqPlain, 0, (seq and 0xffff_ffffL).toInt())
        if (fromAcceptor) {
            seqPlain[4] = 0xff.toByte()
            seqPlain[5] = 0xff.toByte()
            seqPlain[6] = 0xff.toByte()
            seqPlain[7] = 0xff.toByte()
        }
        val encSeq = rc4(hmacMd5(hmacMd5(kss, byteArrayOf(0, 0, 0, 0)), cksum), seqPlain)

        val out = header8 + encSeq + cksum + confounder + body
        return if (wrapInInitialContextToken) {
            GssTokenCodec.wrapInitialContextToken(GssTokenCodec.KERBEROS_V5_OID, out)
        } else {
            out
        }
    }

    fun isArcFourKey(key: EncryptionKey): Boolean {
        return when (key.keyType) {
            EncryptionType.ARCFOUR_HMAC,
            EncryptionType.ARCFOUR_HMAC_EXP,
            -> true
            else -> {
                val n = key.keyData?.size ?: 0
                n == 16 || n == 32
            }
        }
    }

    private fun findMatchingChecksum(
        kss: ByteArray,
        header8: ByteArray,
        confounder: ByteArray,
        body: ByteArray,
        cksum: ByteArray,
    ): ChecksumMatch? {
        val usages = intArrayOf(USAGE_WRAP_MS, USAGE_SIGN, USAGE_MIC_MS)
        val toSignVariants = listOf(
            header8 + confounder + body, // OpenJDK / RFC 4757 Wrap
            header8 + confounder, // draft typo path (header+conf only)
            header8 + body,
            confounder + body,
        )
        for (littleEndian in booleanArrayOf(true, false)) {
            for (usage in usages) {
                for (toSign in toSignVariants) {
                    if (md5HmacChecksum(kss, usage, toSign, littleEndian).contentEquals(cksum)) {
                        return ChecksumMatch(usage, littleEndian)
                    }
                }
            }
        }
        return null
    }

    private fun rc4KeyBytes(key: EncryptionKey): ByteArray {
        val raw = key.keyData ?: error("Missing key data")
        return if (raw.size >= 16) raw.copyOfRange(0, 16) else raw.copyOf(16)
    }

    /**
     * OpenJDK ArcFourCrypto.calculateChecksum: MS little-endian usage salt,
     * with arcfour_translate_usage(23)=13.
     */
    private fun md5HmacChecksum(
        kss: ByteArray,
        usage: Int,
        toSign: ByteArray,
        littleEndian: Boolean = true,
    ): ByteArray {
        val ksign = hmacMd5(kss, "signaturekey\u0000".toByteArray(Charsets.US_ASCII))
        val msUsage = translateArcFourUsage(usage)
        val salt = ByteArray(4).also {
            if (littleEndian) putU32Le(it, 0, msUsage) else putU32Be(it, 0, msUsage)
        }
        val tmp = md5(salt + toSign)
        return hmacMd5(ksign, tmp).copyOfRange(0, CKSUM_LEN)
    }

    private fun translateArcFourUsage(usage: Int): Int = when (usage) {
        3, 9 -> 8
        23 -> 13
        else -> usage
    }

    private fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(key, "HmacMD5"))
        return mac.doFinal(data)
    }

    private fun md5(data: ByteArray): ByteArray =
        MessageDigest.getInstance("MD5").digest(data)

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = try {
            Cipher.getInstance("RC4")
        } catch (_: Exception) {
            Cipher.getInstance("ARCFOUR")
        }
        val algo = cipher.algorithm
        val sk: SecretKey = SecretKeySpec(key, if (algo.contains("ARC", ignoreCase = true)) "ARCFOUR" else "RC4")
        cipher.init(Cipher.ENCRYPT_MODE, sk)
        return cipher.doFinal(data)
    }

    private fun u16Le(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

    private fun putU16Le(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xff).toByte()
        b[off + 1] = ((v ushr 8) and 0xff).toByte()
    }

    private fun putU32Le(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xff).toByte()
        b[off + 1] = ((v ushr 8) and 0xff).toByte()
        b[off + 2] = ((v ushr 16) and 0xff).toByte()
        b[off + 3] = ((v ushr 24) and 0xff).toByte()
    }

    private fun putU32Be(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v ushr 24) and 0xff).toByte()
        b[off + 1] = ((v ushr 16) and 0xff).toByte()
        b[off + 2] = ((v ushr 8) and 0xff).toByte()
        b[off + 3] = (v and 0xff).toByte()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
