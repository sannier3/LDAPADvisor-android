package com.jbsan.ldapadvisor.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_dns")
data class FavoriteDnEntity(
    @PrimaryKey val id: String,
    val dn: String,
    val label: String,
    val createdAt: Long,
)
