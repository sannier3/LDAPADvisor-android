package com.jbsan.ldapadvisor.core.ldap

import com.jbsan.ldapadvisor.core.util.LdapFilterEscaper

/** Search filters for generic LDAP (OpenLDAP, 389, etc.) — not Active Directory. */
object LdapSearchPresets {
    const val ALL_USERS =
        "(|(objectClass=inetOrgPerson)(objectClass=posixAccount)(objectClass=person))"
    /** Includes objectClass=group for 389-DS / mis-tagged AD profiles. */
    const val ALL_GROUPS =
        "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group))"
    const val ALL_OUS = "(objectClass=organizationalUnit)"

    fun userQuery(q: String): String {
        val e = LdapFilterEscaper.escapeFilterValue(q.trim())
        if (e.isEmpty()) return ALL_USERS
        return "(&$ALL_USERS(|(uid=*$e*)(cn=*$e*)(mail=*$e*)(displayName=*$e*)))"
    }

    fun groupQuery(q: String): String {
        val e = LdapFilterEscaper.escapeFilterValue(q.trim())
        if (e.isEmpty()) return ALL_GROUPS
        return "(&$ALL_GROUPS(|(cn=*$e*)(uid=*$e*)))"
    }
}
