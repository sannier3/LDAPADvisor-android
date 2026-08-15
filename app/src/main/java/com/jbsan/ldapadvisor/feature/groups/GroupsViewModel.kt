package com.jbsan.ldapadvisor.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.AdSearchPresets
import com.jbsan.ldapadvisor.core.ad.GroupTypeDecoder
import com.jbsan.ldapadvisor.core.ldap.LdapSearchPresets
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GroupsUiState(
    val query: String = "",
    val results: List<LdapEntryData> = emptyList(),
    val selected: LdapEntryData? = null,
    val members: List<String> = emptyList(),
    val nested: List<LdapEntryData> = emptyList(),
    val isAd: Boolean = false,
    val readOnly: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

class GroupsViewModel(private val sessionManager: SessionManager) : ViewModel() {
    private val _ui = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _ui.asStateFlow()

    fun refreshCaps() {
        val s = sessionManager.currentSession()
        _ui.value = _ui.value.copy(
            isAd = s?.capabilities?.isActiveDirectory == true,
            readOnly = s?.readOnly != false,
        )
    }

    fun setQuery(q: String) { _ui.value = _ui.value.copy(query = q) }

    fun search() = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        val isAd = session.capabilities.isActiveDirectory
        _ui.value = _ui.value.copy(loading = true, isAd = isAd, error = null)
        val base = session.capabilities.defaultNamingContext.orEmpty()
        if (base.isBlank()) {
            _ui.value = _ui.value.copy(
                loading = false,
                error = "No base DN — set Base DN on the profile or check Root DSE namingContexts",
            )
            return@launch
        }
        val filter = when {
            isAd && _ui.value.query.isBlank() -> AdSearchPresets.ALL_GROUPS
            isAd -> AdSearchPresets.groupQuery(_ui.value.query)
            else -> LdapSearchPresets.groupQuery(_ui.value.query)
        }
        val attrs = if (isAd) {
            arrayOf("cn", "sAMAccountName", "description", "groupType", "distinguishedName")
        } else {
            arrayOf("cn", "description", "member", "uniqueMember", "memberUid")
        }
        session.client.search(
            LdapSearchRequest(base, filter, SearchScopeMode.SUB, attrs, pageSize = 100),
        ).fold(
            onSuccess = { _ui.value = _ui.value.copy(results = it, loading = false) },
            onFailure = { _ui.value = _ui.value.copy(loading = false, error = it.message) },
        )
    }

    fun open(dn: String) = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        val isAd = session.capabilities.isActiveDirectory
        val entry = session.client.search(
            LdapSearchRequest(dn, "(objectClass=*)", SearchScopeMode.BASE, arrayOf("*", "+")),
        ).getOrNull()?.firstOrNull()
        val members = if (isAd) {
            session.client.readRangedAttribute(dn, "member").getOrDefault(emptyList())
        } else {
            val fromEntry = buildList {
                entry?.stringValues("member")?.let { addAll(it) }
                entry?.stringValues("uniqueMember")?.let { addAll(it) }
                entry?.stringValues("memberUid")?.let { addAll(it) }
            }.distinct()
            fromEntry
        }
        _ui.value = _ui.value.copy(selected = entry, members = members, nested = emptyList(), isAd = isAd)
    }

    fun loadNested() = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        if (!session.capabilities.isActiveDirectory) {
            _ui.value = _ui.value.copy(error = "Nested group expansion requires Active Directory")
            return@launch
        }
        val groupDn = _ui.value.selected?.dn ?: return@launch
        val base = session.capabilities.defaultNamingContext.orEmpty()
        session.client.searchNestedGroupMembers(groupDn, base).fold(
            onSuccess = { _ui.value = _ui.value.copy(nested = it) },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    fun addMember(memberDn: String) = viewModelScope.launch {
        val groupDn = _ui.value.selected?.dn ?: return@launch
        val session = sessionManager.currentSession() ?: return@launch
        session.client.addGroupMember(groupDn, memberDn).fold(
            onSuccess = { open(groupDn) },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    fun removeMember(memberDn: String) = viewModelScope.launch {
        val groupDn = _ui.value.selected?.dn ?: return@launch
        val session = sessionManager.currentSession() ?: return@launch
        session.client.removeGroupMember(groupDn, memberDn).fold(
            onSuccess = { open(groupDn) },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    fun groupTypeLabel(entry: LdapEntryData): String =
        GroupTypeDecoder.decode(entry.firstString("groupType"))?.labels?.joinToString().orEmpty()
}
