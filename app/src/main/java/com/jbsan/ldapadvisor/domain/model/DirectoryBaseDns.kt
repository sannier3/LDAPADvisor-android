package com.jbsan.ldapadvisor.domain.model

/**
 * Resolves a usable search/browse base for AD and generic LDAP (OpenLDAP, etc.).
 *
 * OpenLDAP Root DSE typically exposes [DirectoryCapabilities.namingContexts] only —
 * not AD's [DirectoryCapabilities.defaultNamingContext].
 */
fun DirectoryCapabilities.withDirectoryTypeHint(type: DirectoryType): DirectoryCapabilities =
    when (type) {
        DirectoryType.AUTO -> this
        DirectoryType.ACTIVE_DIRECTORY -> copy(
            isActiveDirectory = true,
            supportsAdUnicodePwd = true,
        )
        DirectoryType.GENERIC_LDAP -> copy(
            isActiveDirectory = false,
            supportsAdUnicodePwd = false,
            supportsGlobalCatalog = false,
        )
    }

/**
 * Prefer profile base DN, then AD defaultNamingContext, then a useful namingContexts entry.
 * Writes the result into [DirectoryCapabilities.defaultNamingContext] so existing callers work.
 */
fun DirectoryCapabilities.withResolvedSearchBase(preferredBaseDn: String?): DirectoryCapabilities {
    val resolved = resolvePrimaryBaseDn(preferredBaseDn) ?: return this
    return if (defaultNamingContext.equals(resolved, ignoreCase = true)) this
    else copy(defaultNamingContext = resolved)
}

fun DirectoryCapabilities.resolvePrimaryBaseDn(preferredBaseDn: String? = null): String? {
    preferredBaseDn?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    defaultNamingContext?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return namingContexts.preferUserNamingContext()
}

/**
 * Roots for the directory explorer: preferred base, else default, else all useful naming contexts.
 */
fun DirectoryCapabilities.resolveBrowseRootDns(preferredBaseDn: String? = null): List<String> {
    preferredBaseDn?.trim()?.takeIf { it.isNotEmpty() }?.let { return listOf(it) }
    defaultNamingContext?.trim()?.takeIf { it.isNotEmpty() }?.let { return listOf(it) }
    val contexts = namingContexts.preferUserNamingContexts()
    return contexts.ifEmpty { namingContexts.map { it.trim() }.filter { it.isNotEmpty() } }
}

/** Prefer dc=/o= trees; skip OpenLDAP cn=config / cn=monitor when possible. */
fun List<String>.preferUserNamingContext(): String? =
    preferUserNamingContexts().firstOrNull()

fun List<String>.preferUserNamingContexts(): List<String> {
    val cleaned = map { it.trim() }.filter { it.isNotEmpty() }
    if (cleaned.isEmpty()) return emptyList()
    val notMeta = cleaned.filterNot { it.isMetaNamingContext() }
    val pool = notMeta.ifEmpty { cleaned }
    val dcOrO = pool.filter {
        it.contains("dc=", ignoreCase = true) ||
            it.startsWith("o=", ignoreCase = true)
    }
    return dcOrO.ifEmpty { pool }
}

private fun String.isMetaNamingContext(): Boolean {
    val lower = lowercase()
    return lower.startsWith("cn=config") ||
        lower.startsWith("cn=monitor") ||
        lower == "cn=schema,cn=config" ||
        lower.startsWith("cn=schema,cn=config")
}
