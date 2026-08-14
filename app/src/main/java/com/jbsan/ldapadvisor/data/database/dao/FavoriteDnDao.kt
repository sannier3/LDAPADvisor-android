package com.jbsan.ldapadvisor.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jbsan.ldapadvisor.data.database.entity.FavoriteDnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDnDao {
    @Query("SELECT * FROM favorite_dns ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteDnEntity>>

    @Query("SELECT * FROM favorite_dns WHERE dn = :dn LIMIT 1")
    suspend fun getByDn(dn: String): FavoriteDnEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteDnEntity)

    @Query("DELETE FROM favorite_dns WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM favorite_dns WHERE dn = :dn")
    suspend fun deleteByDn(dn: String)
}
