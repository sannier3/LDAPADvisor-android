package com.jbsan.ldapadvisor.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jbsan.ldapadvisor.data.database.entity.DiagnosticRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticRunDao {
    @Query("SELECT * FROM diagnostic_runs ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<DiagnosticRunEntity>>

    @Query("SELECT * FROM diagnostic_runs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DiagnosticRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DiagnosticRunEntity)

    @Query("DELETE FROM diagnostic_runs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM diagnostic_runs")
    suspend fun clearAll()

    @Query("DELETE FROM diagnostic_runs WHERE startedAt < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long)
}
