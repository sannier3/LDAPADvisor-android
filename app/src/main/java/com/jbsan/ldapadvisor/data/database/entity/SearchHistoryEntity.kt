package com.jbsan.ldapadvisor.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val id: String,
    val filter: String,
    val baseDn: String,
    val createdAt: Long,
)
