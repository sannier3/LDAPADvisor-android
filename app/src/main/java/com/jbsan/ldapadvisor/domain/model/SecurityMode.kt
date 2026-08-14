package com.jbsan.ldapadvisor.domain.model

enum class SecurityMode {
    LDAP,
    LDAPS,
    START_TLS;

    fun defaultPort(): Int = when (this) {
        LDAP, START_TLS -> 389
        LDAPS -> 636
    }

    companion object {
        const val PORT_LDAP = 389
        const val PORT_LDAPS = 636
        const val PORT_GC = 3268
        const val PORT_GC_LDAPS = 3269
        const val PORT_KERBEROS = 88
    }
}
