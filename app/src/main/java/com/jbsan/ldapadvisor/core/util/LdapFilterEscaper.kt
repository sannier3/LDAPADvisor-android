package com.jbsan.ldapadvisor.core.util

/**
 * RFC 4515 filter escaping and RFC 4514 DN helpers.
 */
object LdapFilterEscaper {

    fun escapeFilterValue(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '*' -> sb.append("\\2a")
                '(' -> sb.append("\\28")
                ')' -> sb.append("\\29")
                '\\' -> sb.append("\\5c")
                '\u0000' -> sb.append("\\00")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** LDAP filter escaping for binary assertion values (`\xx\xx…`). */
    fun escapeBinary(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 3)
        for (b in bytes) {
            sb.append('\\')
            sb.append("%02x".format(b.toInt() and 0xff))
        }
        return sb.toString()
    }

    fun equalsFilter(attribute: String, value: String): String =
        "($attribute=${escapeFilterValue(value)})"

    fun equalsBinaryFilter(attribute: String, value: ByteArray): String =
        "($attribute=${escapeBinary(value)})"

    fun and(vararg filters: String): String =
        "(&${filters.joinToString(separator = "")})"

    fun or(vararg filters: String): String =
        "(|${filters.joinToString(separator = "")})"

    fun not(filter: String): String = "(!$filter)"
}

object DnUtils {

    /**
     * Escape a single RDN attribute value per RFC 4514.
     */
    fun escapeRdnValue(value: String): String {
        if (value.isEmpty()) return value
        val sb = StringBuilder(value.length + 8)
        value.forEachIndexed { index, ch ->
            val escape = when (ch) {
                '\\', '"', '#', '+', ',', ';', '<', '=', '>' -> true
                ' ' -> index == 0 || index == value.lastIndex
                '\u0000' -> true
                else -> ch.code < 0x20
            }
            if (escape) {
                sb.append('\\')
                if (ch.code < 0x20 || ch == '\u0000') {
                    sb.append("%02x".format(ch.code))
                } else {
                    sb.append(ch)
                }
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun rdn(attribute: String, value: String): String =
        "$attribute=${escapeRdnValue(value)}"

    /** Build `ATTR=value,parentDn` with RFC 4514 escaping of the RDN value. */
    fun buildChildDn(rdnAttribute: String, rdnValue: String, parentDn: String): String {
        val parent = parentDn.trim()
        require(rdnAttribute.isNotBlank()) { "RDN attribute required" }
        require(rdnValue.isNotBlank()) { "RDN value required" }
        require(parent.isNotBlank()) { "Parent DN required" }
        return "${rdn(rdnAttribute.trim(), rdnValue.trim())},$parent"
    }

    fun objectNameFromDn(dn: String): String {
        val rdnPart = dn.substringBefore(',').trim()
        val eq = rdnPart.indexOf('=')
        return if (eq >= 0) rdnPart.substring(eq + 1).trim() else rdnPart
    }

    fun isPlausibleDn(dn: String): Boolean {
        val trimmed = dn.trim()
        if (trimmed.isEmpty()) return true
        // Minimal structural check: at least one attr=value component.
        val parts = trimmed.split(',')
        if (parts.isEmpty()) return false
        return parts.all { part ->
            val p = part.trim()
            val eq = p.indexOf('=')
            eq > 0 && eq < p.lastIndex
        }
    }
}
