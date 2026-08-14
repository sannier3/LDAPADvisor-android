package com.jbsan.ldapadvisor.feature.advisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.data.repository.HistoryRepository
import com.jbsan.ldapadvisor.domain.model.DiagnosticRun
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AdvisorViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {
    private val _run = MutableStateFlow<DiagnosticRun?>(null)
    val run: StateFlow<DiagnosticRun?> = _run.asStateFlow()

    fun loadLatest() = viewModelScope.launch {
        val entity = historyRepository.observeDiagnosticRuns().first().firstOrNull() ?: return@launch
        _run.value = historyRepository.getDiagnosticRun(entity.id)
    }
}
