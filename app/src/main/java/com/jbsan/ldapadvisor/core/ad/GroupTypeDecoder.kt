package com.jbsan.ldapadvisor.core.ad

object GroupTypeDecoder {
    const val GLOBAL = 0x00000002
    const val DOMAIN_LOCAL = 0x00000004
    const val UNIVERSAL = 0x00000008
    const val SECURITY = 0x80000000.toInt()
    const val APP_BASIC = 0x00000010
    const val APP_QUERY = 0x00000020

    enum class Scope { GLOBAL, DOMAIN_LOCAL, UNIVERSAL, UNKNOWN }
    enum class Category { SECURITY, DISTRIBUTION }

    data class Decoded(
        val raw: Int,
        val scope: Scope,
        val category: Category,
        val labels: List<String>,
    )

    fun decode(raw: Int): Decoded {
        val scope = when {
            raw and GLOBAL != 0 -> Scope.GLOBAL
            raw and DOMAIN_LOCAL != 0 -> Scope.DOMAIN_LOCAL
            raw and UNIVERSAL != 0 -> Scope.UNIVERSAL
            else -> Scope.UNKNOWN
        }
        val category = if (raw and SECURITY != 0) Category.SECURITY else Category.DISTRIBUTION
        val labels = buildList {
            when (scope) {
                Scope.GLOBAL -> add("Global")
                Scope.DOMAIN_LOCAL -> add("Domain Local")
                Scope.UNIVERSAL -> add("Universal")
                Scope.UNKNOWN -> add("Unknown scope")
            }
            add(if (category == Category.SECURITY) "Security" else "Distribution")
            if (raw and APP_BASIC != 0) add("APP_BASIC")
            if (raw and APP_QUERY != 0) add("APP_QUERY")
        }
        return Decoded(raw, scope, category, labels)
    }

    fun decode(raw: String?): Decoded? =
        raw?.trim()?.toIntOrNull()?.let { decode(it) }

    fun encode(scope: Scope, security: Boolean): Int {
        var value = when (scope) {
            Scope.GLOBAL -> GLOBAL
            Scope.DOMAIN_LOCAL -> DOMAIN_LOCAL
            Scope.UNIVERSAL -> UNIVERSAL
            Scope.UNKNOWN -> GLOBAL
        }
        if (security) value = value or SECURITY
        return value
    }
}
