package com.jbsan.ldapadvisor.feature.computers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.AdSearchPresets
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComputersUiState(
    val query: String = "",
    val results: List<LdapEntryData> = emptyList(),
    val selected: LdapEntryData? = null,
    val isAd: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class ComputersViewModel(private val sessionManager: SessionManager) : ViewModel() {
    private val _ui = MutableStateFlow(ComputersUiState())
    val uiState: StateFlow<ComputersUiState> = _ui.asStateFlow()

    fun refreshCaps() {
        _ui.value = _ui.value.copy(isAd = sessionManager.currentSession()?.capabilities?.isActiveDirectory == true)
    }

    fun setQuery(q: String) { _ui.value = _ui.value.copy(query = q) }

    fun search() = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        if (!session.capabilities.isActiveDirectory) {
            _ui.value = _ui.value.copy(isAd = false)
            return@launch
        }
        _ui.value = _ui.value.copy(loading = true, isAd = true)
        val base = session.capabilities.defaultNamingContext.orEmpty()
        val filter = if (_ui.value.query.isBlank()) AdSearchPresets.ALL_COMPUTERS else AdSearchPresets.computerQuery(_ui.value.query)
        session.client.search(
            LdapSearchRequest(
                base, filter, SearchScopeMode.SUB,
                arrayOf(
                    "name", "dNSHostName", "operatingSystem", "operatingSystemVersion",
                    "operatingSystemServicePack", "lastLogonTimestamp", "pwdLastSet",
                    "servicePrincipalName", "userAccountControl", "distinguishedName",
                ),
                pageSize = 100,
            ),
        ).fold(
            onSuccess = { _ui.value = _ui.value.copy(results = it, loading = false) },
            onFailure = { _ui.value = _ui.value.copy(loading = false, error = it.message) },
        )
    }

    fun open(dn: String) = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        val entry = session.client.search(
            LdapSearchRequest(dn, "(objectClass=*)", SearchScopeMode.BASE, arrayOf("*", "+")),
        ).getOrNull()?.firstOrNull()
        _ui.value = _ui.value.copy(selected = entry)
    }
}
