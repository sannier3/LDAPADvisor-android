package com.jbsan.ldapadvisor.core.ad

/**
 * Decodes Windows Security Identifier (SID) binary form to S-R-I-S... string.
 */
object SidDecoder {

    fun decode(bytes: ByteArray): String {
        require(bytes.size >= 8) { "SID too short" }
        val revision = bytes[0].toInt() and 0xff
        val subAuthorityCount = bytes[1].toInt() and 0xff
        require(bytes.size >= 8 + subAuthorityCount * 4) { "SID truncated" }

        var identifierAuthority = 0L
        for (i in 2..7) {
            identifierAuthority = (identifierAuthority shl 8) or (bytes[i].toLong() and 0xff)
        }

        val sb = StringBuilder("S-")
            .append(revision)
            .append('-')
            .append(identifierAuthority)

        var offset = 8
        repeat(subAuthorityCount) {
            var value = 0L
            // Sub-authorities are little-endian.
            for (b in 0 until 4) {
                value = value or ((bytes[offset + b].toLong() and 0xff) shl (8 * b))
            }
            sb.append('-').append(value)
            offset += 4
        }
        return sb.toString()
    }

    fun tryDecode(bytes: ByteArray?): String? =
        try {
            if (bytes == null || bytes.isEmpty()) null else decode(bytes)
        } catch (_: Exception) {
            null
        }

    /** Last sub-authority (RID) used for AD primaryGroupID. */
    fun extractRid(bytes: ByteArray): Int {
        require(bytes.size >= 8) { "SID too short" }
        val subAuthorityCount = bytes[1].toInt() and 0xff
        require(subAuthorityCount >= 1) { "SID has no RID" }
        require(bytes.size >= 8 + subAuthorityCount * 4) { "SID truncated" }
        val offset = 8 + (subAuthorityCount - 1) * 4
        var value = 0
        for (b in 0 until 4) {
            value = value or ((bytes[offset + b].toInt() and 0xff) shl (8 * b))
        }
        return value
    }

    fun tryExtractRid(bytes: ByteArray?): Int? =
        try {
            if (bytes == null || bytes.isEmpty()) null else extractRid(bytes)
        } catch (_: Exception) {
            null
        }

    /**
     * Returns a copy of [sidBytes] with the last sub-authority (RID) replaced by [rid].
     * Used to build the primary group SID from a user objectSid + primaryGroupID.
     */
    fun withRid(sidBytes: ByteArray, rid: Int): ByteArray {
        require(sidBytes.size >= 8) { "SID too short" }
        val subAuthorityCount = sidBytes[1].toInt() and 0xff
        require(subAuthorityCount >= 1) { "SID has no RID" }
        require(sidBytes.size >= 8 + subAuthorityCount * 4) { "SID truncated" }
        val copy = sidBytes.copyOf()
        val offset = 8 + (subAuthorityCount - 1) * 4
        val value = rid.toLong() and 0xffffffffL
        for (b in 0 until 4) {
            copy[offset + b] = ((value ushr (8 * b)) and 0xff).toByte()
        }
        return copy
    }

    fun tryWithRid(sidBytes: ByteArray?, rid: Int): ByteArray? =
        try {
            if (sidBytes == null || sidBytes.isEmpty()) null else withRid(sidBytes, rid)
        } catch (_: Exception) {
            null
        }
}
