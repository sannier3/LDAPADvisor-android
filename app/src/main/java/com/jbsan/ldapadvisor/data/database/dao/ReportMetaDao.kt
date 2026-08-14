package com.jbsan.ldapadvisor.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jbsan.ldapadvisor.data.database.entity.ReportMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportMetaDao {
    @Query("SELECT * FROM report_meta ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReportMetaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReportMetaEntity)

    @Query("DELETE FROM report_meta WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM report_meta")
    suspend fun clearAll()

    @Query("DELETE FROM report_meta WHERE createdAt < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long)
}
