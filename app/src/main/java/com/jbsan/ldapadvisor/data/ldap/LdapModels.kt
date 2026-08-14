package com.jbsan.ldapadvisor.data.ldap

import com.jbsan.ldapadvisor.domain.model.DirectoryCapabilities
import com.jbsan.ldapadvisor.domain.model.SecurityMode

data class RootDseInfo(
    val attributes: Map<String, List<String>>,
    val defaultNamingContext: String? = null,
    val rootDomainNamingContext: String? = null,
    val configurationNamingContext: String? = null,
    val schemaNamingContext: String? = null,
    val namingContexts: List<String> = emptyList(),
    val dnsHostName: String? = null,
    val supportedLdapVersions: List<Int> = emptyList(),
    val supportedSaslMechanisms: List<String> = emptyList(),
    val supportedCapabilities: List<String> = emptyList(),
    val supportedControls: List<String> = emptyList(),
    val domainControllerFunctionality: String? = null,
    val domainFunctionality: String? = null,
    val forestFunctionality: String? = null,
    val isGlobalCatalogReady: String? = null,
    val currentTime: String? = null,
) {
    fun toCapabilities(): DirectoryCapabilities {
        val isAd = !defaultNamingContext.isNullOrBlank() &&
            (!configurationNamingContext.isNullOrBlank() ||
                supportedCapabilities.any { it.contains("1.2.840.113556", ignoreCase = true) })
        return DirectoryCapabilities(
            isActiveDirectory = isAd,
            supportsPagedResults = supportedControls.any {
                it == "1.2.840.113556.1.4.319" || it.equals("1.2.840.113556.1.4.319", true)
            } || attributes["supportedControl"].orEmpty().any { it.contains("1.2.840.113556.1.4.319") },
            supportsStartTls = attributes["supportedExtension"].orEmpty().any {
                it.contains("1.3.6.1.4.1.1466.20037")
            },
            supportsSchema = !schemaNamingContext.isNullOrBlank(),
            supportsPasswordModify = attributes["supportedExtension"].orEmpty().any {
                it.contains("1.3.6.1.4.1.4203.1.11.1")
            },
            supportsAdUnicodePwd = isAd,
            supportsGlobalCatalog = isGlobalCatalogReady.equals("TRUE", ignoreCase = true) || isAd,
            supportedSaslMechanisms = supportedSaslMechanisms,
            supportedLdapVersions = supportedLdapVersions,
            namingContexts = namingContexts,
            defaultNamingContext = defaultNamingContext,
        )
    }
}

enum class SearchScopeMode { BASE, ONE, SUB }

data class LdapSearchRequest(
    val baseDn: String,
    val filter: String = "(objectClass=*)",
    val scope: SearchScopeMode = SearchScopeMode.SUB,
    val attributes: Array<String>? = null,
    val sizeLimit: Int = 0,
    val timeLimitSeconds: Int = 0,
    val pageSize: Int? = null,
)

data class LdapEntryData(
    val dn: String,
    val attributes: Map<String, List<ByteArray>>,
) {
    fun stringValues(name: String): List<String> =
        attributes.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.map { String(it, Charsets.UTF_8) }
            .orEmpty()

    fun firstString(name: String): String? = stringValues(name).firstOrNull()
}

data class LdapModificationSpec(
    val attribute: String,
    val type: Type,
    val values: List<ByteArray>,
) {
    enum class Type { ADD, DELETE, REPLACE }
}

sealed class BindOutcome {
    data class Success(val boundAs: String?) : BindOutcome()
    data class RequiresPlaintextConfirmation(val message: String) : BindOutcome()
    data class RequiresInsecureTrustConfirmation(val message: String) : BindOutcome()
    data class Failure(val error: com.jbsan.ldapadvisor.domain.model.AppError) : BindOutcome()
}

data class ConnectOptions(
    val allowPlaintextSimpleBind: Boolean = false,
    val allowInsecureTrust: Boolean = false,
    val preferAnonymous: Boolean = false,
)

data class ActiveSessionInfo(
    val profileId: String,
    val host: String,
    val port: Int,
    val securityMode: SecurityMode,
    val tlsActive: Boolean,
    val boundAs: String?,
    val readOnly: Boolean,
    val rootDse: RootDseInfo?,
    val capabilities: DirectoryCapabilities,
)
