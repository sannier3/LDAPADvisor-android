package com.jbsan.ldapadvisor.data.database

import androidx.room.TypeConverter
import com.jbsan.ldapadvisor.domain.model.DirectoryType
import com.jbsan.ldapadvisor.domain.model.SecurityMode
import com.jbsan.ldapadvisor.domain.model.ThemeMode
import com.jbsan.ldapadvisor.domain.model.TrustMode
import com.jbsan.ldapadvisor.domain.model.parseTrustMode

class EnumConverters {
    @TypeConverter
    fun fromDirectoryType(value: DirectoryType?): String? = value?.name

    @TypeConverter
    fun toDirectoryType(value: String?): DirectoryType? =
        value?.let { DirectoryType.valueOf(it) }

    @TypeConverter
    fun fromSecurityMode(value: SecurityMode?): String? = value?.name

    @TypeConverter
    fun toSecurityMode(value: String?): SecurityMode? =
        value?.let { SecurityMode.valueOf(it) }

    @TypeConverter
    fun fromTrustMode(value: TrustMode?): String? = value?.name

    @TypeConverter
    fun toTrustMode(value: String?): TrustMode? =
        value?.let { parseTrustMode(it) }

    @TypeConverter
    fun fromThemeMode(value: ThemeMode?): String? = value?.name

    @TypeConverter
    fun toThemeMode(value: String?): ThemeMode? =
        value?.let { ThemeMode.valueOf(it) }
}
