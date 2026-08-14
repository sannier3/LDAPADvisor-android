package com.jbsan.ldapadvisor.data.repository

import com.jbsan.ldapadvisor.data.database.dao.FavoriteDnDao
import com.jbsan.ldapadvisor.data.database.dao.SearchHistoryDao
import com.jbsan.ldapadvisor.data.database.entity.FavoriteDnEntity
import com.jbsan.ldapadvisor.data.database.entity.SearchHistoryEntity
import com.jbsan.ldapadvisor.core.util.DnUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class FavoritesRepository(
    private val favoriteDnDao: FavoriteDnDao,
    private val searchHistoryDao: SearchHistoryDao,
) {
    fun observeFavorites(): Flow<List<FavoriteDnEntity>> = favoriteDnDao.observeAll()

    fun observeSearchHistory(limit: Int = 20): Flow<List<SearchHistoryEntity>> =
        searchHistoryDao.observeRecent(limit)

    suspend fun isFavorite(dn: String): Boolean = favoriteDnDao.getByDn(dn) != null

    suspend fun toggleFavorite(dn: String, label: String? = null): Boolean {
        val existing = favoriteDnDao.getByDn(dn)
        return if (existing != null) {
            favoriteDnDao.deleteByDn(dn)
            false
        } else {
            favoriteDnDao.upsert(
                FavoriteDnEntity(
                    id = UUID.randomUUID().toString(),
                    dn = dn,
                    label = label?.takeIf { it.isNotBlank() } ?: DnUtils.objectNameFromDn(dn),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            true
        }
    }

    suspend fun addFavorite(dn: String, label: String) {
        favoriteDnDao.upsert(
            FavoriteDnEntity(
                id = UUID.randomUUID().toString(),
                dn = dn,
                label = label.ifBlank { DnUtils.objectNameFromDn(dn) },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeFavorite(id: String) {
        favoriteDnDao.deleteById(id)
    }

    suspend fun saveSearch(filter: String, baseDn: String) {
        if (looksLikePasswordFilter(filter)) return
        searchHistoryDao.insert(
            SearchHistoryEntity(
                id = UUID.randomUUID().toString(),
                filter = filter.take(2_000),
                baseDn = baseDn.take(1_000),
                createdAt = System.currentTimeMillis(),
            ),
        )
        searchHistoryDao.trimTo(20)
    }

    companion object {
        fun looksLikePasswordFilter(filter: String): Boolean {
            val lower = filter.lowercase()
            return lower.contains("unicodepwd") ||
                lower.contains("password=") ||
                lower.contains("userpassword")
        }
    }
}
