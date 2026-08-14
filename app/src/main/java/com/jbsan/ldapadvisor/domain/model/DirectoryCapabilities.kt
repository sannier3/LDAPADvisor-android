package com.jbsan.ldapadvisor.domain.model

data class DirectoryCapabilities(
    val isActiveDirectory: Boolean = false,
    val supportsPagedResults: Boolean = false,
    val supportsStartTls: Boolean = false,
    val supportsSchema: Boolean = false,
    val supportsPasswordModify: Boolean = false,
    val supportsAdUnicodePwd: Boolean = false,
    val supportsGlobalCatalog: Boolean = false,
    val supportedSaslMechanisms: List<String> = emptyList(),
    val supportedLdapVersions: List<Int> = emptyList(),
    val namingContexts: List<String> = emptyList(),
    val defaultNamingContext: String? = null,
)
