package com.jbsan.ldapadvisor.feature.raw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RawLdapUiState(
    val baseDn: String = "",
    val filter: String = "(objectClass=*)",
    val scope: SearchScopeMode = SearchScopeMode.SUB,
    val compareDn: String = "",
    val compareAttribute: String = "",
    val compareValue: String = "",
    val compareResult: String? = null,
    val results: List<LdapEntryData> = emptyList(),
    val baseEntry: LdapEntryData? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
)

/**
 * Read-only technician tools against the live [LdapClient].
 * Mutations remain on Object Details.
 */
class RawLdapViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(RawLdapUiState())
    val uiState: StateFlow<RawLdapUiState> = _ui.asStateFlow()

    fun prepare() {
        val session = sessionManager.currentSession()
        val base = session?.capabilities?.defaultNamingContext.orEmpty()
        _ui.value = _ui.value.copy(
            connected = session != null,
            baseDn = _ui.value.baseDn.ifBlank { base },
            compareDn = _ui.value.compareDn.ifBlank { base },
        )
    }

    fun update(transform: (RawLdapUiState) -> RawLdapUiState) {
        _ui.value = transform(_ui.value)
    }

    fun search() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null) {
            _ui.value = _ui.value.copy(error = "Not connected", connected = false)
            return@launch
        }
        val f = _ui.value
        _ui.value = f.copy(loading = true, error = null, connected = true)
        session.client.search(
            LdapSearchRequest(
                baseDn = f.baseDn,
                filter = f.filter,
                scope = f.scope,
                attributes = arrayOf("*"),
                sizeLimit = 200,
                pageSize = 100,
            ),
        ).fold(
            onSuccess = { _ui.value = _ui.value.copy(results = it, loading = false) },
            onFailure = { _ui.value = _ui.value.copy(loading = false, error = it.message) },
        )
    }

    fun readBase() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null) {
            _ui.value = _ui.value.copy(error = "Not connected", connected = false)
            return@launch
        }
        val dn = _ui.value.baseDn
        _ui.value = _ui.value.copy(loading = true, error = null)
        session.client.search(
            LdapSearchRequest(dn, "(objectClass=*)", SearchScopeMode.BASE, arrayOf("*", "+")),
        ).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(baseEntry = it.firstOrNull(), loading = false)
            },
            onFailure = { _ui.value = _ui.value.copy(loading = false, error = it.message) },
        )
    }

    fun compare() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null) {
            _ui.value = _ui.value.copy(error = "Not connected", connected = false)
            return@launch
        }
        val f = _ui.value
        if (f.compareDn.isBlank() || f.compareAttribute.isBlank()) {
            _ui.value = f.copy(error = "Compare DN and attribute are required")
            return@launch
        }
        _ui.value = f.copy(loading = true, error = null, compareResult = null)
        session.client.compare(f.compareDn, f.compareAttribute, f.compareValue).fold(
            onSuccess = { matched ->
                _ui.value = _ui.value.copy(
                    loading = false,
                    compareResult = if (matched) "COMPARE_TRUE" else "COMPARE_FALSE",
                )
            },
            onFailure = { _ui.value = _ui.value.copy(loading = false, error = it.message) },
        )
    }
}
