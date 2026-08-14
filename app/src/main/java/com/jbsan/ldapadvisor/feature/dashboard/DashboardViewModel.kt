package com.jbsan.ldapadvisor.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.FunctionalLevelDecoder
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.data.repository.HistoryRepository
import com.jbsan.ldapadvisor.data.repository.ProfileRepository
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.ConnectionStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticRun
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val activeProfile: ConnectionProfile? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val networkAvailable: Boolean = true,
    val domainLevel: String? = null,
    val forestLevel: String? = null,
    val dcLevel: String? = null,
    val gcReady: String? = null,
    val baseDn: String? = null,
    val lastRun: DiagnosticRun? = null,
)

class DashboardViewModel(
    profileRepository: ProfileRepository,
    sessionManager: SessionManager,
    historyRepository: HistoryRepository,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = combine(
        profileRepository.observeProfiles(),
        sessionManager.status,
        sessionManager.networkAvailable,
        historyRepository.observeDiagnosticRuns(),
    ) { profiles, status, networkAvailable, runs ->
        val activeId = (status as? ConnectionStatus.Connected)?.profileId
        val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
        val session = sessionManager.currentSession()
        val root = session?.rootDse
        @Suppress("UNUSED_VARIABLE")
        val last = runs.firstOrNull()?.let { null as DiagnosticRun? }
        DashboardUiState(
            profiles = profiles,
            activeProfile = active,
            connectionStatus = status,
            networkAvailable = networkAvailable,
            domainLevel = FunctionalLevelDecoder.label(root?.domainFunctionality),
            forestLevel = FunctionalLevelDecoder.label(root?.forestFunctionality),
            dcLevel = FunctionalLevelDecoder.label(root?.domainControllerFunctionality),
            gcReady = root?.isGlobalCatalogReady,
            baseDn = active?.baseDn?.ifBlank { root?.defaultNamingContext },
            lastRun = last,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
