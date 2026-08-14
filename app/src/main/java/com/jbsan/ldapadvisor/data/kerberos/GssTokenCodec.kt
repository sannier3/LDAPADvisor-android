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
        return encodeTagged(0x30, mechTypesField + mechTokenField)
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
}
