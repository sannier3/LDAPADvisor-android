package com.jbsan.ldapadvisor.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.data.database.entity.FavoriteDnEntity
import com.jbsan.ldapadvisor.data.database.entity.SearchHistoryEntity
import com.jbsan.ldapadvisor.data.repository.FavoritesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {
    val favorites: StateFlow<List<FavoriteDnEntity>> = favoritesRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = favoritesRepository.observeSearchHistory(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(id: String) = viewModelScope.launch {
        favoritesRepository.removeFavorite(id)
    }
}
