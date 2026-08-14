package com.jbsan.ldapadvisor.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jbsan.ldapadvisor.data.database.entity.CustomCaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCaDao {
    @Query("SELECT * FROM custom_cas ORDER BY alias COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CustomCaEntity>>

    @Query("SELECT * FROM custom_cas WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomCaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomCaEntity)

    @Query("DELETE FROM custom_cas WHERE id = :id")
    suspend fun deleteById(id: String)
}
