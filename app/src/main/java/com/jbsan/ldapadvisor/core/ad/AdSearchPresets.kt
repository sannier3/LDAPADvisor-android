package com.jbsan.ldapadvisor.core.ad

import com.jbsan.ldapadvisor.core.util.LdapFilterEscaper

object AdSearchPresets {
    const val ALL_USERS = "(&(objectCategory=person)(objectClass=user))"
    const val ALL_GROUPS = "(objectCategory=group)"
    const val ALL_COMPUTERS = "(objectCategory=computer)"
    const val DISABLED_USERS = "(&(objectCategory=person)(objectClass=user)(userAccountControl:1.2.840.113556.1.4.803:=2))"
    const val USERS_WITH_SPN = "(&(objectCategory=person)(objectClass=user)(servicePrincipalName=*))"
    const val PWD_NEVER_EXPIRES = "(&(objectCategory=person)(objectClass=user)(userAccountControl:1.2.840.113556.1.4.803:=65536))"
    const val DOMAIN_CONTROLLERS = "(&(objectCategory=computer)(userAccountControl:1.2.840.113556.1.4.803:=8192))"
    const val WINDOWS_COMPUTERS = "(&(objectCategory=computer)(!(userAccountControl:1.2.840.113556.1.4.803:=8192)))"
    const val OUS = "(objectClass=organizationalUnit)"

    fun userQuery(q: String): String {
        val e = LdapFilterEscaper.escapeFilterValue(q.trim())
        return "(&(objectCategory=person)(objectClass=user)(|(sAMAccountName=*$e*)(userPrincipalName=*$e*)(displayName=*$e*)(mail=*$e*)))"
    }

    fun groupQuery(q: String): String {
        val e = LdapFilterEscaper.escapeFilterValue(q.trim())
        return "(&(objectCategory=group)(|(cn=*$e*)(sAMAccountName=*$e*)))"
    }

    fun computerQuery(q: String): String {
        val e = LdapFilterEscaper.escapeFilterValue(q.trim())
        return "(&(objectCategory=computer)(|(cn=*$e*)(dNSHostName=*$e*)(name=*$e*)))"
    }
}

enum class DirectoryObjectKind {
    DOMAIN, OU, CONTAINER, USER, GROUP, COMPUTER, CONTACT, GENERIC;

    /** Folders that can be expanded in the directory explorer. */
    fun isExpandable(): Boolean = when (this) {
        DOMAIN, OU, CONTAINER -> true
        else -> false
    }
}

object DirectoryObjectClassifier {
    fun classify(objectClasses: List<String>): DirectoryObjectKind {
        val lower = objectClasses.map { it.lowercase() }
        return when {
            lower.any { it == "computer" } -> DirectoryObjectKind.COMPUTER
            lower.any { it == "user" || it == "inetorgperson" } -> DirectoryObjectKind.USER
            lower.any { it == "group" || it == "groupofnames" || it == "groupofuniquenames" } ->
                DirectoryObjectKind.GROUP
            lower.any { it == "contact" } -> DirectoryObjectKind.CONTACT
            lower.any { it == "organizationalunit" } -> DirectoryObjectKind.OU
            lower.any { it == "container" } -> DirectoryObjectKind.CONTAINER
            lower.any { it == "domaindns" || it == "domain" } -> DirectoryObjectKind.DOMAIN
            else -> DirectoryObjectKind.GENERIC
        }
    }

    fun displayName(
        dn: String,
        objectClasses: List<String>,
        name: String? = null,
        cn: String? = null,
        ou: String? = null,
        dc: String? = null,
        displayName: String? = null,
        samAccountName: String? = null,
    ): String {
        displayName?.takeIf { it.isNotBlank() }?.let { return it }
        name?.takeIf { it.isNotBlank() }?.let { return it }
        cn?.takeIf { it.isNotBlank() }?.let { return it }
        ou?.takeIf { it.isNotBlank() }?.let { return it }
        samAccountName?.takeIf { it.isNotBlank() }?.let { return it }
        dc?.takeIf { it.isNotBlank() }?.let { return it }
        val rdn = dn.substringBefore(',').trim()
        val value = rdn.substringAfter('=', missingDelimiterValue = rdn).trim()
        return value.ifBlank { dn }
    }

    /** OUs/containers first, then other objects A→Z by display name. */
    fun compareExplorerOrder(aName: String, aKind: DirectoryObjectKind, bName: String, bKind: DirectoryObjectKind): Int {
        val aFolder = aKind.isExpandable()
        val bFolder = bKind.isExpandable()
        if (aFolder != bFolder) return if (aFolder) -1 else 1
        return aName.compareTo(bName, ignoreCase = true)
    }
}
