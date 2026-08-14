package com.jbsan.ldapadvisor.core.ad

import java.nio.charset.StandardCharsets

/**
 * Encodes a password for Active Directory unicodePwd:
 * quoted UTF-16LE bytes of "password".
 */
object UnicodePwdEncoder {

    fun encode(password: CharArray): ByteArray {
        val quoted = CharArray(password.size + 2)
        quoted[0] = '"'
        password.copyInto(quoted, destinationOffset = 1)
        quoted[quoted.lastIndex] = '"'
        val bytes = String(quoted).toByteArray(StandardCharsets.UTF_16LE)
        quoted.fill('\u0000')
        return bytes
    }

    fun encode(password: String): ByteArray = encode(password.toCharArray())
}
