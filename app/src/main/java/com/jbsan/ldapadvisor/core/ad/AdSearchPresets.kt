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
    fun classify(objectClasses: List<String>, dn: String = ""): DirectoryObjectKind {
        val lower = objectClasses.map { it.lowercase() }
        return when {
            lower.any { it == "computer" } -> DirectoryObjectKind.COMPUTER
            // Samba/AD account classes — check before generic container heuristics on CN=
            lower.any {
                it == "user" || it == "inetorgperson" || it == "posixaccount" || it == "sambasamaccount"
            } && !looksLikeWellKnownContainerDn(dn) -> DirectoryObjectKind.USER
            lower.any {
                it == "group" || it == "groupofnames" || it == "groupofuniquenames" ||
                    it == "posixgroup" || it == "sambagroupmapping"
            } -> DirectoryObjectKind.GROUP
            lower.any { it == "contact" } -> DirectoryObjectKind.CONTACT
            lower.any { it == "organizationalunit" } -> DirectoryObjectKind.OU
            lower.any {
                it == "container" || it == "organizationalrole" || it == "builtindomain"
            } -> DirectoryObjectKind.CONTAINER
            // OpenLDAP / RFC2307 trees: dcObject, organization, locality, country
            lower.any {
                it == "domaindns" || it == "domain" || it == "dcobject" ||
                    it == "organization" || it == "locality" || it == "country"
            } -> DirectoryObjectKind.DOMAIN
            // Samba / AD-style CN=Users (often only objectClass=top or sparse classes)
            looksLikeWellKnownContainerDn(dn) -> DirectoryObjectKind.CONTAINER
            else -> DirectoryObjectKind.GENERIC
        }
    }

    /**
     * Well-known folder RDNs that should expand even when objectClass is sparse
     * (common on Samba / OpenLDAP trees: cn=Users, cn=Groups, …).
     */
    fun looksLikeWellKnownContainerDn(dn: String): Boolean {
        val rdn = dn.substringBefore(',').trim()
        val attr = rdn.substringBefore('=').trim()
        val value = rdn.substringAfter('=', missingDelimiterValue = "").trim()
        if (attr.equals("ou", ignoreCase = true) ||
            attr.equals("dc", ignoreCase = true) ||
            attr.equals("o", ignoreCase = true) ||
            attr.equals("c", ignoreCase = true) ||
            attr.equals("l", ignoreCase = true)
        ) {
            return true
        }
        if (!attr.equals("cn", ignoreCase = true) || value.isEmpty()) return false
        val v = value.lowercase()
        return v in WELL_KNOWN_CONTAINER_CNS
    }

    private val WELL_KNOWN_CONTAINER_CNS = setOf(
        "users", "groups", "computers", "builtin", "system",
        "foreignsecurityprincipals", "managed service accounts",
        "program data", "microsoft exchange system objects",
        "ntfrs subscriptions", "winsockservices", "rpcservices",
    )

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
