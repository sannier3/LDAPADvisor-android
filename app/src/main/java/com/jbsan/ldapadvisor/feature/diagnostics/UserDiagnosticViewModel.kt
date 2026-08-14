package com.jbsan.ldapadvisor.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.AdSearchPresets
import com.jbsan.ldapadvisor.core.ad.FileTimeConverter
import com.jbsan.ldapadvisor.core.ad.UserAccountControl
import com.jbsan.ldapadvisor.core.util.LdapFilterEscaper
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserDiagnosticUiState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val entry: LdapEntryData? = null,
    val enabledLabel: String = "",
    val lockedLabel: String = "",
    val passwordStatusLabel: String = "",
    val passwordExpiryLabel: String = "",
    val uacFlags: String = "",
    val computedFlags: String = "",
    val notes: List<String> = emptyList(),
)

class UserDiagnosticViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(UserDiagnosticUiState())
    val uiState: StateFlow<UserDiagnosticUiState> = _ui.asStateFlow()

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
    }

    fun run() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null || !session.isConnected()) {
            _ui.value = _ui.value.copy(error = "Not connected")
            return@launch
        }
        if (!session.capabilities.isActiveDirectory) {
            _ui.value = _ui.value.copy(error = "Active Directory session required")
            return@launch
        }
        val q = _ui.value.query.trim()
        if (q.isBlank()) {
            _ui.value = _ui.value.copy(error = "Enter sAMAccountName or UPN")
            return@launch
        }
        _ui.value = _ui.value.copy(loading = true, error = null, entry = null)
        val base = session.capabilities.defaultNamingContext.orEmpty()
        val filter = if (q.contains('@')) {
            "(&(objectCategory=person)(objectClass=user)(userPrincipalName=${LdapFilterEscaper.escapeFilterValue(q)}))"
        } else {
            AdSearchPresets.userQuery(q)
        }
        val result = session.client.search(
            LdapSearchRequest(
                baseDn = base,
                filter = filter,
                scope = SearchScopeMode.SUB,
                attributes = arrayOf(
                    "cn", "displayName", "sAMAccountName", "userPrincipalName", "distinguishedName",
                    "userAccountControl", "msDS-User-Account-Control-Computed",
                    "msDS-UserPasswordExpiryTimeComputed", "pwdLastSet", "lockoutTime",
                    "badPwdCount", "accountExpires", "memberOf",
                ),
                pageSize = 20,
                sizeLimit = 20,
            ),
        )
        result.fold(
            onSuccess = { list ->
                val entry = list.firstOrNull()
                if (entry == null) {
                    _ui.value = _ui.value.copy(loading = false, error = "No user found")
                    return@fold
                }
                _ui.value = decode(entry).copy(loading = false, query = q)
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(loading = false, error = err.message)
            },
        )
    }

    private fun decode(entry: LdapEntryData): UserDiagnosticUiState {
        val uac = UserAccountControl.decode(entry.firstString("userAccountControl"))
        val computed = UserAccountControl.decode(entry.firstString("msDS-User-Account-Control-Computed"))
        val enabledLabel = when {
            uac == null -> "userAccountControl unavailable"
            uac.enabled -> "Account enabled (ACCOUNTDISABLE not set)"
            else -> "Account disabled (ACCOUNTDISABLE set)"
        }
        val lockedLabel = when {
            computed != null && computed.flags.contains("LOCKOUT") -> "Locked (msDS-User-Account-Control-Computed LOCKOUT)"
            entry.firstString("lockoutTime")?.let { it != "0" && it.isNotBlank() } == true ->
                "lockoutTime is non-zero (may indicate lockout; verify with computed UAC)"
            else -> "Not marked locked from available attributes"
        }
        val passwordExpired = computed?.flags?.contains("PASSWORD_EXPIRED") == true
        val passwordStatusLabel = when {
            computed == null -> "Computed password status unavailable"
            passwordExpired -> "Password marked expired (PASSWORD_EXPIRED in computed UAC)"
            else -> "Password not marked as expired"
        }
        val expiryRaw = entry.firstString("msDS-UserPasswordExpiryTimeComputed")
        val passwordExpiryLabel = when (val v = FileTimeConverter.parse(expiryRaw)) {
            is FileTimeConverter.FileTimeValue.InstantValue -> "Password expiry (computed): ${v.instant}"
            FileTimeConverter.FileTimeValue.Never -> "Password expiry (computed): never / not applicable"
            FileTimeConverter.FileTimeValue.Zero -> "Password expiry (computed): 0"
            else -> if (expiryRaw.isNullOrBlank()) {
                "Password expiry attribute not present"
            } else {
                "Password expiry (raw): $expiryRaw"
            }
        }
        val notes = listOf(
            "This diagnostic reads directory attributes only; it never authenticates as the target user.",
            "Never interpret results as \"password is valid\".",
            "pwdLastSet: ${fileTime(entry.firstString("pwdLastSet"))}",
            "badPwdCount: ${entry.firstString("badPwdCount") ?: "—"}",
        )
        return UserDiagnosticUiState(
            entry = entry,
            enabledLabel = enabledLabel,
            lockedLabel = lockedLabel,
            passwordStatusLabel = passwordStatusLabel,
            passwordExpiryLabel = passwordExpiryLabel,
            uacFlags = uac?.flags?.sorted()?.joinToString().orEmpty(),
            computedFlags = computed?.flags?.sorted()?.joinToString().orEmpty(),
            notes = notes,
        )
    }

    private fun fileTime(raw: String?): String = when (val v = FileTimeConverter.parse(raw)) {
        is FileTimeConverter.FileTimeValue.InstantValue -> v.instant.toString()
        FileTimeConverter.FileTimeValue.Never -> "Never"
        FileTimeConverter.FileTimeValue.Zero -> "0"
        else -> raw ?: "—"
    }
}
