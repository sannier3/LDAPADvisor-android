package com.jbsan.ldapadvisor.data.kerberos

/**
 * DER helpers for GSS-API / SPNEGO tokens used by LDAP SASL binds.
 * Pure Kotlin — no JDK GSS / sun.security dependency (unavailable on Android).
 */
object GssTokenCodec {
    /** Kerberos V5 mechanism OID 1.2.840.113554.1.2.2 */
    val KERBEROS_V5_OID: ByteArray = byteArrayOf(
        0x06, 0x09,
        0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x12, 0x01, 0x02, 0x02,
    )

    /** SPNEGO mechanism OID 1.3.6.1.5.5.2 */
    val SPNEGO_OID: ByteArray = byteArrayOf(
        0x06, 0x06,
        0x2b, 0x06, 0x01, 0x05, 0x05, 0x02,
    )

    /** TOK_ID for KRB5_AP_REQ */
    private val AP_REQ_TOK_ID = byteArrayOf(0x01, 0x00)

    /** TOK_ID for KRB5_AP_REP */
    private val AP_REP_TOK_ID = byteArrayOf(0x02, 0x00)

    /** APPLICATION 15 — AP-REP */
    private const val TAG_AP_REP = 0x6f

    /**
     * Wrap an AP-REQ ASN.1 encoding as a Kerberos GSS inner token (TOK_ID || AP-REQ),
     * then as an InitialContextToken ([APPLICATION 0] OID + inner).
     */
    fun wrapKerberosApReq(apReqDer: ByteArray): ByteArray {
        val inner = AP_REQ_TOK_ID + apReqDer
        return wrapInitialContextToken(KERBEROS_V5_OID, inner)
    }

    /**
     * Build a SPNEGO NegTokenInit wrapping a Kerberos GSS token, then wrap as InitialContextToken.
     */
    fun wrapSpnegoNegTokenInit(kerberosGssToken: ByteArray): ByteArray {
        val negTokenInit = encodeNegTokenInit(kerberosGssToken)
        return wrapInitialContextToken(SPNEGO_OID, negTokenInit)
    }

    /**
     * Convenience: AP-REQ → Kerberos GSS → SPNEGO NegTokenInit InitialContextToken.
     */
    fun wrapSpnegoFromApReq(apReqDer: ByteArray): ByteArray =
        wrapSpnegoNegTokenInit(wrapKerberosApReq(apReqDer))

    fun wrapInitialContextToken(mechOidDer: ByteArray, innerToken: ByteArray): ByteArray {
        val content = mechOidDer + innerToken
        return encodeTagged(tag = 0x60, content = content) // APPLICATION 0, constructed
    }

    /**
     * Extract AP-REP DER (APPLICATION 15) from a server SASL credential blob.
     * Accepts SPNEGO NegTokenResp and/or Kerberos GSS InitialContextToken wrappers.
     */
    fun extractApRepDer(serverToken: ByteArray): ByteArray {
        require(serverToken.isNotEmpty()) { "Empty server SASL credentials" }
        var bytes = serverToken

        if (bytes[0] == 0x60.toByte()) {
            val content = unwrapTaggedContent(bytes, 0x60)
            val afterOid = skipDerOid(content)
            bytes = afterOid
        }

        // NegTokenResp ::= [1] SEQUENCE { ... responseToken [2] OCTET STRING ... }
        if (bytes.isNotEmpty() && bytes[0] == 0xa1.toByte()) {
            val seq = unwrapTaggedContent(bytes, 0xa1)
            val responseToken = findContextOctetString(seq, tag = 0xa2)
                ?: error("SPNEGO NegTokenResp missing responseToken [2]")
            bytes = responseToken
        }

        if (bytes.isNotEmpty() && bytes[0] == 0x60.toByte()) {
            val content = unwrapTaggedContent(bytes, 0x60)
            bytes = skipDerOid(content)
        }

        if (bytes.size >= 2 && bytes[0] == AP_REP_TOK_ID[0] && bytes[1] == AP_REP_TOK_ID[1]) {
            return bytes.copyOfRange(2, bytes.size)
        }

        if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xff) == TAG_AP_REP) {
            return bytes
        }

        error(
            "Unrecognized Kerberos/SPNEGO server token " +
                "(first=${bytes.firstOrNull()?.toInt()?.and(0xff)?.toString(16)}, size=${bytes.size})",
        )
    }

    /**
     * NegTokenInit ::= SEQUENCE {
     *   mechTypes [0] MechTypeList OPTIONAL,
     *   reqFlags  [1] ContextFlags OPTIONAL,  -- omitted
     *   mechToken [2] OCTET STRING OPTIONAL,
     *   ...
     * }
     * MechTypeList ::= SEQUENCE OF MechType
     */
    fun encodeNegTokenInit(mechToken: ByteArray): ByteArray {
        val mechTypesSeq = encodeTagged(0x30, KERBEROS_V5_OID)
        val mechTypesField = encodeTagged(0xa0, mechTypesSeq) // [0] IMPLICIT
        val mechTokenOctet = encodeOctetString(mechToken)
        val mechTokenField = encodeTagged(0xa2, mechTokenOctet) // [2] IMPLICIT
        val negTokenInitSeq = encodeTagged(0x30, mechTypesField + mechTokenField)
        // NegotiationToken CHOICE { negTokenInit [0] NegTokenInit, ... }
        return encodeTagged(0xa0, negTokenInitSeq)
    }

    fun encodeOctetString(value: ByteArray): ByteArray = encodeTagged(0x04, value)

    fun encodeTagged(tag: Int, content: ByteArray): ByteArray {
        val length = encodeLength(content.size)
        return byteArrayOf(tag.toByte()) + length + content
    }

    fun encodeLength(length: Int): ByteArray = when {
        length < 0x80 -> byteArrayOf(length.toByte())
        length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
        length <= 0xFFFF -> byteArrayOf(
            0x82.toByte(),
            (length shr 8).toByte(),
            (length and 0xFF).toByte(),
        )
        else -> byteArrayOf(
            0x83.toByte(),
            (length shr 16).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte(),
        )
    }

    fun unwrapTaggedContent(der: ByteArray, expectedTag: Int): ByteArray {
        require(der.isNotEmpty()) { "Empty DER" }
        require((der[0].toInt() and 0xff) == (expectedTag and 0xff)) {
            "Expected tag 0x${(expectedTag and 0xff).toString(16)}, got 0x${(der[0].toInt() and 0xff).toString(16)}"
        }
        val (len, lenSize) = decodeLength(der, 1)
        val contentStart = 1 + lenSize
        require(contentStart + len <= der.size) { "Truncated DER content" }
        return der.copyOfRange(contentStart, contentStart + len)
    }

    fun decodeLength(der: ByteArray, offset: Int): Pair<Int, Int> {
        require(offset < der.size) { "Missing DER length" }
        val first = der[offset].toInt() and 0xff
        if (first < 0x80) return first to 1
        val count = first and 0x7f
        require(count in 1..3) { "Unsupported DER length form ($count)" }
        require(offset + 1 + count <= der.size) { "Truncated DER length" }
        var value = 0
        for (i in 0 until count) {
            value = (value shl 8) or (der[offset + 1 + i].toInt() and 0xff)
        }
        return value to (1 + count)
    }

    /**
     * If [token] is a Kerberos GSS InitialContextToken ([APPLICATION 0] + OID + inner),
     * return the inner mech token; otherwise return [token] unchanged.
     */
    fun unwrapInitialContextTokenInner(token: ByteArray): ByteArray {
        if (token.isEmpty() || token[0] != 0x60.toByte()) return token
        val content = unwrapTaggedContent(token, 0x60)
        return skipDerOid(content)
    }

    private fun skipDerOid(content: ByteArray): ByteArray {
        require(content.isNotEmpty() && content[0] == 0x06.toByte()) { "Expected OID after ICT header" }
        val (len, lenSize) = decodeLength(content, 1)
        val end = 1 + lenSize + len
        require(end <= content.size) { "Truncated OID" }
        return content.copyOfRange(end, content.size)
    }

    private fun findContextOctetString(seqContent: ByteArray, tag: Int): ByteArray? {
        var offset = 0
        while (offset < seqContent.size) {
            val t = seqContent[offset].toInt() and 0xff
            val (len, lenSize) = decodeLength(seqContent, offset + 1)
            val valueStart = offset + 1 + lenSize
            val valueEnd = valueStart + len
            require(valueEnd <= seqContent.size) { "Truncated SEQUENCE field" }
            if (t == (tag and 0xff)) {
                val field = seqContent.copyOfRange(valueStart, valueEnd)
                // [2] IMPLICIT OCTET STRING — may be raw octets or an explicit 0x04 wrapper
                return if (field.isNotEmpty() && field[0] == 0x04.toByte()) {
                    unwrapTaggedContent(field, 0x04)
                } else {
                    field
                }
            }
            offset = valueEnd
        }
        return null
    }
}
