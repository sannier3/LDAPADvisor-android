package com.jbsan.ldapadvisor.core.ad

object TrustDecoders {

    object Direction {
        const val DISABLED = 0
        const val INBOUND = 1
        const val OUTBOUND = 2
        const val BIDIRECTIONAL = 3

        fun label(value: Int): String = when (value) {
            DISABLED -> "Disabled"
            INBOUND -> "Inbound"
            OUTBOUND -> "Outbound"
            BIDIRECTIONAL -> "Bidirectional"
            else -> "Unknown ($value)"
        }
    }

    object Type {
        const val DOWNLEVEL = 1
        const val UPLEVEL = 2
        const val MIT = 3
        const val DCE = 4

        fun label(value: Int): String = when (value) {
            DOWNLEVEL -> "Downlevel (Windows NT)"
            UPLEVEL -> "Uplevel (AD)"
            MIT -> "MIT"
            DCE -> "DCE"
            else -> "Unknown ($value)"
        }
    }

    object Attributes {
        const val NON_TRANSITIVE = 0x00000001
        const val UPLEVEL_ONLY = 0x00000002
        const val QUARANTINED_DOMAIN = 0x00000004
        const val FOREST_TRANSITIVE = 0x00000008
        const val CROSS_ORGANIZATION = 0x00000010
        const val WITHIN_FOREST = 0x00000020
        const val TREAT_AS_EXTERNAL = 0x00000040
        const val USES_RC4_ENCRYPTION = 0x00000080
        const val CROSS_ORGANIZATION_NO_TGT_DELEGATION = 0x00000200
        const val PIM_TRUST = 0x00000400
        const val CROSS_ORGANIZATION_ENABLE_TGT_DELEGATION = 0x00000800

        private val NAMES = linkedMapOf(
            NON_TRANSITIVE to "NON_TRANSITIVE",
            UPLEVEL_ONLY to "UPLEVEL_ONLY",
            QUARANTINED_DOMAIN to "QUARANTINED_DOMAIN",
            FOREST_TRANSITIVE to "FOREST_TRANSITIVE",
            CROSS_ORGANIZATION to "CROSS_ORGANIZATION",
            WITHIN_FOREST to "WITHIN_FOREST",
            TREAT_AS_EXTERNAL to "TREAT_AS_EXTERNAL",
            USES_RC4_ENCRYPTION to "USES_RC4_ENCRYPTION",
            CROSS_ORGANIZATION_NO_TGT_DELEGATION to "CROSS_ORGANIZATION_NO_TGT_DELEGATION",
            PIM_TRUST to "PIM_TRUST",
            CROSS_ORGANIZATION_ENABLE_TGT_DELEGATION to "CROSS_ORGANIZATION_ENABLE_TGT_DELEGATION",
        )

        fun decode(raw: Int): Set<String> =
            NAMES.filter { (bit, _) -> raw and bit != 0 }.values.toSet()
    }

    data class DecodedTrust(
        val direction: String,
        val type: String,
        val attributes: Set<String>,
    )

    fun decode(direction: Int, type: Int, attributes: Int): DecodedTrust =
        DecodedTrust(
            direction = Direction.label(direction),
            type = Type.label(type),
            attributes = Attributes.decode(attributes),
        )
}
