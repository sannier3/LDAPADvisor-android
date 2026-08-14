package com.jbsan.ldapadvisor.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jbsan.ldapadvisor.data.database.entity.TrustedCertEntity

@Dao
interface TrustedCertDao {
    @Query("SELECT * FROM trusted_certs WHERE profileId = :profileId")
    suspend fun getForProfile(profileId: String): List<TrustedCertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrustedCertEntity)

    @Query("DELETE FROM trusted_certs WHERE id = :id")
    suspend fun deleteById(id: String)
}
