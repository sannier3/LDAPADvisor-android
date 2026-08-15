package com.jbsan.ldapadvisor.feature.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.AdSearchPresets
import com.jbsan.ldapadvisor.core.ad.FileTimeConverter
import com.jbsan.ldapadvisor.core.ad.GuidDecoder
import com.jbsan.ldapadvisor.core.ad.SidDecoder
import com.jbsan.ldapadvisor.core.ad.UserAccountControl
import com.jbsan.ldapadvisor.core.ldap.LdapSearchPresets
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UsersUiState(
    val query: String = "",
    val results: List<LdapEntryData> = emptyList(),
    val selected: LdapEntryData? = null,
    val isAd: Boolean = false,
    val supportsPasswordModify: Boolean = false,
    val readOnly: Boolean = true,
    val tlsActive: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val showResetPassword: Boolean = false,
    val showPasswordModify: Boolean = false,
)

class UsersViewModel(private val sessionManager: SessionManager) : ViewModel() {
    private val _ui = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = _ui.asStateFlow()

    fun refreshCaps() {
        val s = sessionManager.currentSession()
        _ui.value = _ui.value.copy(
            isAd = s?.capabilities?.isActiveDirectory == true,
            supportsPasswordModify = s?.capabilities?.supportsPasswordModify == true,
            readOnly = s?.readOnly != false,
            tlsActive = s?.tlsActive == true,
        )
    }

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
    }

    fun search() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null) {
            _ui.value = _ui.value.copy(error = "Not connected")
            return@launch
        }
        val isAd = session.capabilities.isActiveDirectory
        val canPwdMod = session.capabilities.supportsPasswordModify
        _ui.value = _ui.value.copy(
            loading = true,
            error = null,
            isAd = isAd,
            supportsPasswordModify = canPwdMod,
        )
        val base = session.capabilities.defaultNamingContext.orEmpty()
        if (base.isBlank()) {
            _ui.value = _ui.value.copy(
                loading = false,
                error = "No base DN — set Base DN on the profile or check Root DSE namingContexts",
            )
            return@launch
        }
        val filter = when {
            isAd && _ui.value.query.isBlank() -> AdSearchPresets.ALL_USERS
            isAd -> AdSearchPresets.userQuery(_ui.value.query)
            else -> LdapSearchPresets.userQuery(_ui.value.query)
        }
        val attrs = if (isAd) {
            arrayOf(
                "cn", "displayName", "sAMAccountName", "userPrincipalName", "mail",
                "distinguishedName", "objectSid", "objectGUID", "userAccountControl",
                "msDS-User-Account-Control-Computed", "pwdLastSet", "lastLogonTimestamp",
                "accountExpires", "memberOf", "servicePrincipalName", "badPwdCount", "badPasswordTime",
                "lockoutTime", "msDS-UserPasswordExpiryTimeComputed",
            )
        } else {
            arrayOf("cn", "uid", "mail", "sn", "displayName", "entryDN")
        }
        val result = session.client.search(
            LdapSearchRequest(
                baseDn = base,
                filter = filter,
                scope = SearchScopeMode.SUB,
                attributes = attrs,
                pageSize = 100,
                sizeLimit = 200,
            ),
        )
        result.fold(
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

    fun unlock() = viewModelScope.launch {
        val dn = _ui.value.selected?.dn ?: return@launch
        val session = sessionManager.currentSession() ?: return@launch
        session.client.unlockAdUser(dn).fold(
            onSuccess = { open(dn); _ui.value = _ui.value.copy(message = "unlocked") },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    fun setDisabled(disabled: Boolean) = viewModelScope.launch {
        val dn = _ui.value.selected?.dn ?: return@launch
        val session = sessionManager.currentSession() ?: return@launch
        session.client.setAdAccountDisabled(dn, disabled).fold(
            onSuccess = { open(dn); _ui.value = _ui.value.copy(message = "uac") },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    fun showReset(v: Boolean) {
        _ui.value = _ui.value.copy(showResetPassword = v)
    }

    fun showPasswordModify(v: Boolean) {
        _ui.value = _ui.value.copy(showPasswordModify = v)
    }

    fun resetPassword(password: CharArray) = viewModelScope.launch {
        val dn = _ui.value.selected?.dn ?: return@launch
        val session = sessionManager.currentSession() ?: return@launch
        session.client.resetAdPassword(dn, password).fold(
            onSuccess = { _ui.value = _ui.value.copy(showResetPassword = false, message = "pwd") },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    fun changePasswordPasswordModify(
        oldPassword: CharArray?,
        newPassword: CharArray,
    ) = viewModelScope.launch {
        val dn = _ui.value.selected?.dn ?: return@launch
        val session = sessionManager.currentSession() ?: return@launch
        session.client.changePasswordPasswordModify(
            userIdentity = dn,
            oldPassword = oldPassword,
            newPassword = newPassword,
        ).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(showPasswordModify = false, message = "pwd_modify")
            },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    fun decodeSid(entry: LdapEntryData): String? =
        entry.attributes.entries.firstOrNull { it.key.equals("objectSid", true) }
            ?.value?.firstOrNull()?.let { SidDecoder.tryDecode(it) }

    fun decodeGuid(entry: LdapEntryData): String? =
        entry.attributes.entries.firstOrNull { it.key.equals("objectGUID", true) }
            ?.value?.firstOrNull()?.let { GuidDecoder.tryDecode(it) }

    fun uac(entry: LdapEntryData) =
        UserAccountControl.decode(entry.firstString("userAccountControl"))

    fun fileTimeLabel(raw: String?): String = when (val v = FileTimeConverter.parse(raw)) {
        is FileTimeConverter.FileTimeValue.InstantValue -> v.instant.toString()
        FileTimeConverter.FileTimeValue.Never -> "Never"
        FileTimeConverter.FileTimeValue.Zero -> "0"
        else -> raw ?: "—"
    }
}
