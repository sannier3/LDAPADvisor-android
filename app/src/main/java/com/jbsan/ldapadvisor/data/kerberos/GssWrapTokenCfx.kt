package com.jbsan.ldapadvisor.data.kerberos

import org.apache.kerby.kerberos.kerb.crypto.CheckSumHandler
import org.apache.kerby.kerberos.kerb.type.base.CheckSumType
import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey
import org.apache.kerby.kerberos.kerb.type.base.EncryptionType
import org.apache.kerby.kerberos.kerb.type.base.KeyUsage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * RFC 4121 CFX Wrap tokens without confidentiality (TOK_ID 0x0504).
 */
object GssWrapTokenCfx {
    private const val HDR_LEN = 16
    private const val TOK_ID_0: Byte = 0x05
    private const val TOK_ID_1: Byte = 0x04
    private const val FILLER: Byte = -1 // 0xFF
    private const val FLAG_SENT_BY_ACCEPTOR: Int = 0x01

    private val USAGE_ACCEPTOR_SEAL = KeyUsage.GSS_TOK_MIC
    private val USAGE_INITIATOR_SEAL = KeyUsage.GSS_TOK_WRAP_PRIV

    fun unwrapPayload(token: ByteArray, sessionKey: EncryptionKey, fromAcceptor: Boolean): ByteArray {
        require(token.size >= HDR_LEN + 1) { "Wrap token too short (${token.size})" }
        val cfx = GssTokenCodec.unwrapInitialContextTokenInner(token)
        require(cfx.size >= HDR_LEN + 1) {
            "CFX Wrap too short after ICT unwrap (${cfx.size})"
        }
        val rotated = undoRrc(cfx)
        require(rotated[0] == TOK_ID_0 && rotated[1] == TOK_ID_1) {
            "Not a CFX Wrap token (got ${rotated[0].toInt() and 0xff}/${rotated[1].toInt() and 0xff})"
        }
        val flags = rotated[2].toInt() and 0xff
        val sentByAcceptor = flags and FLAG_SENT_BY_ACCEPTOR != 0
        require(sentByAcceptor == fromAcceptor) {
            "Wrap SentByAcceptor=$sentByAcceptor expected fromAcceptor=$fromAcceptor"
        }
        require(rotated[3] == FILLER) { "Wrap filler != 0xFF" }
        val sealed = flags and 0x02 != 0
        require(!sealed) { "Confidential Wrap (sealed) is not supported yet" }
        val ec = u16(rotated, 4)
        require(ec in 1..(rotated.size - HDR_LEN)) { "Invalid Wrap EC=$ec" }
        val payload = rotated.copyOfRange(HDR_LEN, rotated.size - ec)
        val checksum = rotated.copyOfRange(rotated.size - ec, rotated.size)
        val seq = u64(rotated, 8)
        val expected = computeChecksum(
            sessionKey = sessionKey,
            usage = if (fromAcceptor) USAGE_ACCEPTOR_SEAL else USAGE_INITIATOR_SEAL,
            flags = flags.toByte(),
            seq = seq,
            payload = payload,
        )
        require(expected.contentEquals(checksum)) { "Wrap checksum mismatch" }
        return payload
    }

    fun wrapPayload(
        payload: ByteArray,
        sessionKey: EncryptionKey,
        fromAcceptor: Boolean,
        seq: Long = 0L,
        wrapInInitialContextToken: Boolean = true,
    ): ByteArray {
        val flags = if (fromAcceptor) FLAG_SENT_BY_ACCEPTOR else 0
        val checksum = computeChecksum(
            sessionKey = sessionKey,
            usage = if (fromAcceptor) USAGE_ACCEPTOR_SEAL else USAGE_INITIATOR_SEAL,
            flags = flags.toByte(),
            seq = seq,
            payload = payload,
        )
        val out = ByteArray(HDR_LEN + payload.size + checksum.size)
        out[0] = TOK_ID_0
        out[1] = TOK_ID_1
        out[2] = flags.toByte()
        out[3] = FILLER
        putU16(out, 4, checksum.size)
        putU16(out, 6, 0)
        putU64(out, 8, seq)
        payload.copyInto(out, HDR_LEN)
        checksum.copyInto(out, HDR_LEN + payload.size)
        return if (wrapInInitialContextToken) {
            GssTokenCodec.wrapInitialContextToken(GssTokenCodec.KERBEROS_V5_OID, out)
        } else {
            out
        }
    }

    private fun computeChecksum(
        sessionKey: EncryptionKey,
        usage: KeyUsage,
        flags: Byte,
        seq: Long,
        payload: ByteArray,
    ): ByteArray {
        val headerForCksum = ByteArray(HDR_LEN)
        headerForCksum[0] = TOK_ID_0
        headerForCksum[1] = TOK_ID_1
        headerForCksum[2] = flags
        headerForCksum[3] = FILLER
        putU64(headerForCksum, 8, seq)
        val toSign = payload + headerForCksum
        val cksumType = when (sessionKey.keyType) {
            EncryptionType.AES128_CTS_HMAC_SHA1_96 -> CheckSumType.HMAC_SHA1_96_AES128
            EncryptionType.AES256_CTS_HMAC_SHA1_96 -> CheckSumType.HMAC_SHA1_96_AES256
            else -> error("Unsupported session key etype for CFX Wrap: ${sessionKey.keyType}")
        }
        val cksum = CheckSumHandler.checksumWithKey(
            cksumType,
            toSign,
            sessionKey.keyData,
            usage,
        )
        return cksum.checksum ?: error("Empty checksum from Kerby")
    }

    private fun undoRrc(token: ByteArray): ByteArray {
        val rrc = u16(token, 6)
        if (rrc == 0) return token
        val data = token.copyOfRange(HDR_LEN, token.size)
        if (data.isEmpty() || rrc % data.size == 0) {
            val copy = token.copyOf()
            putU16(copy, 6, 0)
            return copy
        }
        val r = rrc % data.size
        val restored = ByteArray(data.size)
        System.arraycopy(data, r, restored, 0, data.size - r)
        System.arraycopy(data, 0, restored, data.size - r, r)
        val out = ByteArray(token.size)
        System.arraycopy(token, 0, out, 0, HDR_LEN)
        putU16(out, 6, 0)
        restored.copyInto(out, HDR_LEN)
        return out
    }

    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xff) shl 8) or (b[off + 1].toInt() and 0xff)

    private fun u64(b: ByteArray, off: Int): Long =
        ByteBuffer.wrap(b, off, 8).order(ByteOrder.BIG_ENDIAN).long

    private fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v ushr 8) and 0xff).toByte()
        b[off + 1] = (v and 0xff).toByte()
    }

    private fun putU64(b: ByteArray, off: Int, v: Long) {
        ByteBuffer.wrap(b, off, 8).order(ByteOrder.BIG_ENDIAN).putLong(v)
    }
}
