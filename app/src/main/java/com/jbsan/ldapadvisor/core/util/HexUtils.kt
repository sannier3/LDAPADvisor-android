package com.jbsan.ldapadvisor.core.util

object HexUtils {
    private val HEX = "0123456789abcdef".toCharArray()

    fun toHex(bytes: ByteArray, separator: String = ""): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder(bytes.size * (2 + separator.length))
        bytes.forEachIndexed { index, b ->
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0f])
            if (separator.isNotEmpty() && index < bytes.lastIndex) {
                sb.append(separator)
            }
        }
        return sb.toString()
    }

    fun fromHex(hex: String): ByteArray {
        val cleaned = hex.replace(":", "").replace(" ", "").lowercase()
        require(cleaned.length % 2 == 0) { "Hex string must have even length" }
        require(cleaned.all { it in '0'..'9' || it in 'a'..'f' }) { "Invalid hex character" }
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            val hi = cleaned[i * 2].digitToInt(16)
            val lo = cleaned[i * 2 + 1].digitToInt(16)
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
