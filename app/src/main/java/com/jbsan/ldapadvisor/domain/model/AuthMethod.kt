package com.jbsan.ldapadvisor.domain.model

/**
 * LDAP bind authentication method for a connection profile.
 *
 * [KERBEROS] uses an embedded Kerberos client (Apache Kerby) to obtain tickets,
 * then performs a SASL GSS-SPNEGO / GSSAPI bind. It does not rely on Android OS Kerberos.
 */
enum class AuthMethod {
    SIMPLE,
    KERBEROS,
}
