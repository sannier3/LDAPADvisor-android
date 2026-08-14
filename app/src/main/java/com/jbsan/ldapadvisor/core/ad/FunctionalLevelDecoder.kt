package com.jbsan.ldapadvisor.core.ad

object FunctionalLevelDecoder {

    fun label(level: Int?): String = when (level) {
        null -> "Unknown"
        0 -> "Windows 2000"
        1 -> "Windows 2003 Interim / Windows Server 2003 interim"
        2 -> "Windows Server 2003"
        3 -> "Windows Server 2008"
        4 -> "Windows Server 2008 R2"
        5 -> "Windows Server 2012"
        6 -> "Windows Server 2012 R2"
        7 -> "Windows Server 2016"
        8 -> "Windows Server 2019 / 2022 / 2025 family"
        9 -> "Windows Server 2025+"
        else -> "Unknown ($level)"
    }

    fun parse(raw: String?): Int? = raw?.trim()?.toIntOrNull()

    fun label(raw: String?): String = label(parse(raw))
}
