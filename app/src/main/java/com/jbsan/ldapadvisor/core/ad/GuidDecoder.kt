package com.jbsan.ldapadvisor.core.ad

import java.util.UUID

/**
 * Active Directory objectGUID uses mixed-endian encoding:
 * Data1 (int LE), Data2 (short LE), Data3 (short LE), Data4 (8 bytes BE / as-is).
 */
object GuidDecoder {

    fun decode(bytes: ByteArray): String {
        require(bytes.size == 16) { "objectGUID must be 16 bytes" }
        val data1 = ((bytes[3].toInt() and 0xff) shl 24) or
            ((bytes[2].toInt() and 0xff) shl 16) or
            ((bytes[1].toInt() and 0xff) shl 8) or
            (bytes[0].toInt() and 0xff)
        val data2 = ((bytes[5].toInt() and 0xff) shl 8) or (bytes[4].toInt() and 0xff)
        val data3 = ((bytes[7].toInt() and 0xff) shl 8) or (bytes[6].toInt() and 0xff)
        val node = ByteArray(8)
        System.arraycopy(bytes, 8, node, 0, 8)
        val msb = ((data1.toLong() and 0xffffffffL) shl 32) or
            ((data2.toLong() and 0xffffL) shl 16) or
            (data3.toLong() and 0xffffL)
        var lsb = 0L
        for (b in node) {
            lsb = (lsb shl 8) or (b.toLong() and 0xff)
        }
        return UUID(msb, lsb).toString()
    }

    fun tryDecode(bytes: ByteArray?): String? =
        try {
            if (bytes == null || bytes.size != 16) null else decode(bytes)
        } catch (_: Exception) {
            null
        }
}
