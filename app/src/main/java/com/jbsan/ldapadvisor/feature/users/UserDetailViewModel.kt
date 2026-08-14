package com.jbsan.ldapadvisor.feature.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.FileTimeConverter
import com.jbsan.ldapadvisor.core.ad.GroupTypeDecoder
import com.jbsan.ldapadvisor.core.ad.GuidDecoder
import com.jbsan.ldapadvisor.core.ad.SidDecoder
import com.jbsan.ldapadvisor.core.ad.UserAccountControl
import com.jbsan.ldapadvisor.core.util.DnUtils
import com.jbsan.ldapadvisor.core.util.LdapFilterEscaper
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapModificationSpec
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserGroupMembership(
    val dn: String,
    val name: String,
    val typeLabel: String = "",
    val isPrimary: Boolean = false,
    /** True when listed in LDAP memberOf (primary-only memberships are false). */
    val listedInMemberOf: Boolean = true,
)

data class UserEditForm(
    val dn: String = "",
    val givenName: String = "",
    val sn: String = "",
    val displayName: String = "",
    val mail: String = "",
    val telephoneNumber: String = "",
    val mobile: String = "",
    val wwwHomePage: String = "",
    val streetAddress: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "",
    val title: String = "",
    val department: String = "",
    val company: String = "",
    val samAccountName: String = "",
    val userPrincipalName: String = "",
    val memberOf: List<String> = emptyList(),
    /** Effective memberships: primary group + memberOf (AD primary is not in memberOf). */
    val memberships: List<UserGroupMembership> = emptyList(),
    val primaryGroupId: String = "",
    val primaryGroupDn: String = "",
    val primaryGroupLabel: String = "",
    val primaryGroupSid: String = "",
    val mustChangePassword: Boolean = false,
    val passwordNeverExpires: Boolean = false,
    val enabled: Boolean = true,
    val sid: String? = null,
    val guid: String? = null,
)

data class UserDetailUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val form: UserEditForm = UserEditForm(),
    val readOnly: Boolean = true,
    val isAd: Boolean = false,
    val tlsActive: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val dirty: Boolean = false,
    val addGroupDn: String = "",
    val showResetPassword: Boolean = false,
    val passwordResetError: String? = null,
    val passwordResetBusy: Boolean = false,
)

class UserDetailViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(UserDetailUiState())
    val uiState: StateFlow<UserDetailUiState> = _ui.asStateFlow()

    fun load(dn: String) = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null) {
            _ui.value = UserDetailUiState(error = "Not connected")
            return@launch
        }
        _ui.value = _ui.value.copy(loading = true, error = null, message = null)
        val entry = session.client.search(
            LdapSearchRequest(
                baseDn = dn,
                filter = "(objectClass=*)",
                scope = SearchScopeMode.BASE,
                attributes = arrayOf(
                    "givenName", "sn", "displayName", "mail", "telephoneNumber", "mobile",
                    "wWWHomePage", "streetAddress", "l", "st", "postalCode", "c", "co",
                    "title", "department", "company", "sAMAccountName", "userPrincipalName",
                    "memberOf", "primaryGroupID", "userAccountControl", "pwdLastSet",
                    "objectSid", "objectGUID", "distinguishedName",
                ),
            ),
        ).getOrElse {
            _ui.value = _ui.value.copy(loading = false, error = it.message)
            return@launch
        }.firstOrNull()

        if (entry == null) {
            _ui.value = _ui.value.copy(loading = false, error = "User not found")
            return@launch
        }

        val form = entry.toForm()
        val userSidBytes = entry.attributes.entries
            .firstOrNull { it.key.equals("objectSid", true) }
            ?.value
            ?.firstOrNull()
        val primary = resolvePrimaryGroup(
            primaryGroupId = form.primaryGroupId,
            userSidBytes = userSidBytes,
            userDn = form.dn,
        )
        val memberships = enrichMembershipTypes(
            session = session,
            memberships = buildMemberships(form.memberOf, primary),
        )
        _ui.value = UserDetailUiState(
            loading = false,
            form = form.copy(
                primaryGroupDn = primary?.dn.orEmpty(),
                primaryGroupLabel = primary?.name.orEmpty(),
                primaryGroupSid = primary?.sid.orEmpty(),
                memberships = memberships,
            ),
            readOnly = session.readOnly,
            isAd = session.capabilities.isActiveDirectory,
            tlsActive = session.tlsActive,
            dirty = false,
        )
    }

    fun update(transform: (UserEditForm) -> UserEditForm) {
        _ui.value = _ui.value.copy(
            form = transform(_ui.value.form),
            dirty = true,
            message = null,
            error = null,
        )
    }

    fun setAddGroupDn(dn: String) {
        _ui.value = _ui.value.copy(addGroupDn = dn)
    }

    fun showReset(v: Boolean) {
        _ui.value = _ui.value.copy(
            showResetPassword = v,
            passwordResetError = null,
            passwordResetBusy = false,
            error = if (v) null else _ui.value.error,
        )
    }

    fun showResetPasswordDialog() = showReset(true)

    fun dismissResetPassword() = showReset(false)

    fun saveIdentityAndContact() = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        if (session.readOnly) {
            _ui.value = _ui.value.copy(error = "read_only")
            return@launch
        }
        val f = _ui.value.form
        _ui.value = _ui.value.copy(saving = true, error = null)
        val mods = buildList {
            addReplace("givenName", f.givenName)
            addReplace("sn", f.sn)
            addReplace("displayName", f.displayName)
            addReplace("mail", f.mail)
            addReplace("telephoneNumber", f.telephoneNumber)
            addReplace("mobile", f.mobile)
            addReplace("wWWHomePage", f.wwwHomePage)
            addReplace("streetAddress", f.streetAddress)
            addReplace("l", f.city)
            addReplace("st", f.state)
            addReplace("postalCode", f.postalCode)
            addReplace("c", f.country)
            addReplace("title", f.title)
            addReplace("department", f.department)
            addReplace("company", f.company)
        }
        session.client.modify(f.dn, mods).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(saving = false, message = "saved", dirty = false)
                load(f.dn)
            },
            onFailure = { _ui.value = _ui.value.copy(saving = false, error = it.message) },
        )
    }

    fun savePasswordFlags() = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        if (session.readOnly || !session.capabilities.isActiveDirectory) {
            _ui.value = _ui.value.copy(error = "read_only")
            return@launch
        }
        val f = _ui.value.form
        _ui.value = _ui.value.copy(saving = true, error = null)
        val entry = session.client.search(
            LdapSearchRequest(f.dn, "(objectClass=*)", SearchScopeMode.BASE, arrayOf("userAccountControl", "pwdLastSet")),
        ).getOrNull()?.firstOrNull()
        val rawUac = entry?.firstString("userAccountControl")?.toIntOrNull()
        if (rawUac == null) {
            _ui.value = _ui.value.copy(saving = false, error = "userAccountControl missing")
            return@launch
        }
        var next = UserAccountControl.withFlag(rawUac, UserAccountControl.DONT_EXPIRE_PASSWORD, f.passwordNeverExpires)
        // Must-change-password and never-expire are mutually exclusive in practice.
        if (f.mustChangePassword && f.passwordNeverExpires) {
            next = UserAccountControl.withFlag(next, UserAccountControl.DONT_EXPIRE_PASSWORD, false)
        }
        val mods = mutableListOf(
            LdapModificationSpec(
                "userAccountControl",
                LdapModificationSpec.Type.REPLACE,
                listOf(next.toString().toByteArray()),
            ),
        )
        if (f.mustChangePassword) {
            mods += LdapModificationSpec(
                "pwdLastSet",
                LdapModificationSpec.Type.REPLACE,
                listOf("0".toByteArray()),
            )
        } else {
            // If admin clears "must change", set pwdLastSet=-1 (now) only when currently 0.
            val currentPwdLastSet = entry.firstString("pwdLastSet")
            if (currentPwdLastSet == "0") {
                mods += LdapModificationSpec(
                    "pwdLastSet",
                    LdapModificationSpec.Type.REPLACE,
                    listOf("-1".toByteArray()),
                )
            }
        }
        session.client.modify(f.dn, mods).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(saving = false, message = "flags_saved", dirty = false)
                load(f.dn)
            },
            onFailure = { _ui.value = _ui.value.copy(saving = false, error = it.message) },
        )
    }

    fun addToGroup() = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        if (session.readOnly) return@launch
        val groupDn = _ui.value.addGroupDn.trim()
        val userDn = _ui.value.form.dn
        if (groupDn.isBlank()) return@launch
        _ui.value = _ui.value.copy(saving = true, error = null)
        session.client.addGroupMember(groupDn, userDn).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(saving = false, addGroupDn = "", message = "group_added")
                load(userDn)
            },
            onFailure = { _ui.value = _ui.value.copy(saving = false, error = it.message) },
        )
    }

    fun removeFromGroup(groupDn: String) = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        if (session.readOnly) return@launch
        val form = _ui.value.form
        val primaryDn = form.primaryGroupDn
        if (primaryDn.isNotBlank() && groupDn.equals(primaryDn, ignoreCase = true)) {
            _ui.value = _ui.value.copy(
                saving = false,
                error = "primary_remove_blocked",
            )
            return@launch
        }
        if (form.memberships.any { it.isPrimary && it.dn.equals(groupDn, ignoreCase = true) }) {
            _ui.value = _ui.value.copy(
                saving = false,
                error = "primary_remove_blocked",
            )
            return@launch
        }
        val userDn = form.dn
        _ui.value = _ui.value.copy(saving = true, error = null)
        session.client.removeGroupMember(groupDn, userDn).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(saving = false, message = "group_removed")
                load(userDn)
            },
            onFailure = { _ui.value = _ui.value.copy(saving = false, error = it.message) },
        )
    }

    fun setPrimaryGroup(groupDn: String) = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        if (session.readOnly || !session.capabilities.isActiveDirectory) return@launch
        val userDn = _ui.value.form.dn
        _ui.value = _ui.value.copy(saving = true, error = null)
        val group = session.client.search(
            LdapSearchRequest(groupDn, "(objectClass=group)", SearchScopeMode.BASE, arrayOf("objectSid", "cn")),
        ).getOrNull()?.firstOrNull()
        val sid = group?.attributes?.entries?.firstOrNull { it.key.equals("objectSid", true) }
            ?.value?.firstOrNull()
        val rid = SidDecoder.tryExtractRid(sid)
        if (rid == null) {
            _ui.value = _ui.value.copy(saving = false, error = "Unable to resolve group RID")
            return@launch
        }
        // User must already be a member before primary group can change.
        if (!_ui.value.form.memberOf.any { it.equals(groupDn, ignoreCase = true) }) {
            session.client.addGroupMember(groupDn, userDn).onFailure {
                _ui.value = _ui.value.copy(saving = false, error = it.message)
                return@launch
            }
        }
        session.client.modify(
            userDn,
            listOf(
                LdapModificationSpec(
                    "primaryGroupID",
                    LdapModificationSpec.Type.REPLACE,
                    listOf(rid.toString().toByteArray()),
                ),
            ),
        ).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(saving = false, message = "primary_group_saved")
                load(userDn)
            },
            onFailure = { _ui.value = _ui.value.copy(saving = false, error = it.message) },
        )
    }

    fun unlock() = viewModelScope.launch {
        val dn = _ui.value.form.dn
        val session = sessionManager.currentSession() ?: return@launch
        session.client.unlockAdUser(dn).fold(
            onSuccess = { load(dn); _ui.value = _ui.value.copy(message = "unlocked") },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    fun setDisabled(disabled: Boolean) = viewModelScope.launch {
        val dn = _ui.value.form.dn
        val session = sessionManager.currentSession() ?: return@launch
        session.client.setAdAccountDisabled(dn, disabled).fold(
            onSuccess = { load(dn); _ui.value = _ui.value.copy(message = "uac") },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    fun resetPassword(password: CharArray) = viewModelScope.launch {
        val dn = _ui.value.form.dn
        if (dn.isBlank()) {
            password.fill('\u0000')
            _ui.value = _ui.value.copy(
                passwordResetBusy = false,
                passwordResetError = "User DN is missing",
            )
            return@launch
        }
        val session = sessionManager.currentSession()
        if (session == null) {
            password.fill('\u0000')
            _ui.value = _ui.value.copy(
                passwordResetBusy = false,
                passwordResetError = "Not connected to a directory",
            )
            return@launch
        }
        if (session.readOnly) {
            password.fill('\u0000')
            _ui.value = _ui.value.copy(
                passwordResetBusy = false,
                passwordResetError = "Operation blocked by read-only mode",
            )
            return@launch
        }
        if (!session.tlsActive) {
            password.fill('\u0000')
            _ui.value = _ui.value.copy(
                passwordResetBusy = false,
                passwordResetError = "A secure channel (LDAPS or StartTLS) is required",
            )
            return@launch
        }

        _ui.value = _ui.value.copy(
            passwordResetBusy = true,
            passwordResetError = null,
            error = null,
            message = null,
        )
        session.client.resetAdPassword(dn, password).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(
                    showResetPassword = false,
                    passwordResetBusy = false,
                    passwordResetError = null,
                    message = "pwd",
                    error = null,
                )
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(
                    passwordResetBusy = false,
                    // Keep dialog open and show the failure where the user is looking.
                    passwordResetError = formatError(err),
                    showResetPassword = true,
                )
            },
        )
    }

    private fun formatError(err: Throwable): String {
        val app = err as? com.jbsan.ldapadvisor.domain.model.AppError
        val message = app?.message ?: err.message ?: "Password reset failed"
        val details = app?.technicalDetails?.takeIf { it.isNotBlank() && it != message }
        return if (details == null) message else "$message — $details"
    }

    fun fileTimeLabel(raw: String?): String = when (val v = FileTimeConverter.parse(raw)) {
        is FileTimeConverter.FileTimeValue.InstantValue -> v.instant.toString()
        FileTimeConverter.FileTimeValue.Never -> "Never"
        FileTimeConverter.FileTimeValue.Zero -> "0"
        else -> raw ?: "—"
    }

    private data class PrimaryGroupInfo(
        val dn: String,
        val name: String,
        val sid: String,
        val typeLabel: String = "",
    )

    private fun buildMemberships(
        memberOf: List<String>,
        primary: PrimaryGroupInfo?,
    ): List<UserGroupMembership> {
        val primaryDn = primary?.dn?.takeIf { it.isNotBlank() }
        val result = mutableListOf<UserGroupMembership>()
        if (primary != null && (primaryDn != null || primary.name.isNotBlank())) {
            result += UserGroupMembership(
                dn = primaryDn.orEmpty(),
                name = primary.name.ifBlank {
                    primaryDn?.let { DnUtils.objectNameFromDn(it) }.orEmpty()
                }.ifBlank { "RID" },
                typeLabel = primary.typeLabel,
                isPrimary = true,
                listedInMemberOf = primaryDn != null &&
                    memberOf.any { it.equals(primaryDn, ignoreCase = true) },
            )
        }
        memberOf
            .filter { dn -> primaryDn == null || !dn.equals(primaryDn, ignoreCase = true) }
            .sortedBy { DnUtils.objectNameFromDn(it).lowercase() }
            .forEach { dn ->
                result += UserGroupMembership(
                    dn = dn,
                    name = DnUtils.objectNameFromDn(dn),
                    typeLabel = "",
                    isPrimary = false,
                    listedInMemberOf = true,
                )
            }
        return result
    }

    /** Fetch cn/groupType for non-primary memberships (capped to keep load snappy). */
    private suspend fun enrichMembershipTypes(
        session: com.jbsan.ldapadvisor.data.ldap.LdapSession,
        memberships: List<UserGroupMembership>,
    ): List<UserGroupMembership> {
        val limit = 40
        return memberships.mapIndexed { index, membership ->
            if (membership.isPrimary && membership.typeLabel.isNotBlank()) {
                membership
            } else if (membership.dn.isBlank() || index >= limit) {
                membership
            } else {
                val entry = session.client.search(
                    LdapSearchRequest(
                        baseDn = membership.dn,
                        filter = "(objectClass=group)",
                        scope = SearchScopeMode.BASE,
                        attributes = arrayOf("cn", "name", "groupType"),
                        sizeLimit = 1,
                    ),
                ).getOrNull()?.firstOrNull()
                if (entry == null) {
                    membership
                } else {
                    val name = entry.firstString("cn")
                        ?: entry.firstString("name")
                        ?: membership.name
                    val typeLabel = GroupTypeDecoder.decode(entry.firstString("groupType"))
                        ?.labels
                        ?.joinToString()
                        .orEmpty()
                    membership.copy(
                        name = name,
                        typeLabel = typeLabel.ifBlank { membership.typeLabel },
                    )
                }
            }
        }
    }

    private suspend fun resolvePrimaryGroup(
        primaryGroupId: String,
        userSidBytes: ByteArray?,
        userDn: String,
    ): PrimaryGroupInfo? {
        val session = sessionManager.currentSession() ?: return null
        val rid = primaryGroupId.toIntOrNull() ?: return null
        val groupSidBytes = SidDecoder.tryWithRid(userSidBytes, rid)
        val groupSid = groupSidBytes?.let { SidDecoder.tryDecode(it) }
            ?: run {
                val userSid = SidDecoder.tryDecode(userSidBytes) ?: return null
                "${userSid.substringBeforeLast('-')}-$rid"
            }
        val base = searchBaseDn(session, userDn)
        if (base.isBlank()) {
            return PrimaryGroupInfo(dn = "", name = "RID $rid", sid = groupSid)
        }

        val attrs = arrayOf("cn", "name", "sAMAccountName", "distinguishedName", "objectSid", "groupType")
        val found = if (groupSidBytes != null) {
            val sidFilter = LdapFilterEscaper.and(
                "(objectClass=group)",
                LdapFilterEscaper.equalsBinaryFilter("objectSid", groupSidBytes),
            )
            searchFirst(session, base, sidFilter, attrs)
        } else {
            null
        } ?: run {
            val tokenFilter = LdapFilterEscaper.and(
                "(objectClass=group)",
                "(primaryGroupToken=${LdapFilterEscaper.escapeFilterValue(rid.toString())})",
            )
            searchFirst(session, base, tokenFilter, attrs)
        }

        return if (found != null) {
            val name = found.firstString("cn")
                ?: found.firstString("name")
                ?: found.firstString("sAMAccountName")
                ?: DnUtils.objectNameFromDn(found.dn)
            val typeLabel = GroupTypeDecoder.decode(found.firstString("groupType"))
                ?.labels
                ?.joinToString()
                .orEmpty()
            PrimaryGroupInfo(dn = found.dn, name = name, sid = groupSid, typeLabel = typeLabel)
        } else {
            PrimaryGroupInfo(dn = "", name = "RID $rid", sid = groupSid)
        }
    }

    private suspend fun searchFirst(
        session: com.jbsan.ldapadvisor.data.ldap.LdapSession,
        baseDn: String,
        filter: String,
        attributes: Array<String>,
    ): LdapEntryData? =
        session.client.search(
            LdapSearchRequest(
                baseDn = baseDn,
                filter = filter,
                scope = SearchScopeMode.SUB,
                attributes = attributes,
                sizeLimit = 5,
                pageSize = 5,
            ),
        ).getOrNull()?.firstOrNull()

    private fun searchBaseDn(
        session: com.jbsan.ldapadvisor.data.ldap.LdapSession,
        userDn: String,
    ): String =
        session.capabilities.defaultNamingContext?.takeIf { it.isNotBlank() }
            ?: session.rootDse?.defaultNamingContext?.takeIf { it.isNotBlank() }
            ?: domainDnFrom(userDn)

    private fun domainDnFrom(dn: String): String {
        val parts = dn.split(',')
        val idx = parts.indexOfFirst { it.trim().startsWith("DC=", ignoreCase = true) }
        return if (idx >= 0) parts.drop(idx).joinToString(",") { it.trim() } else dn
    }

    private fun MutableList<LdapModificationSpec>.addReplace(attr: String, value: String) {
        if (value.isBlank()) {
            add(LdapModificationSpec(attr, LdapModificationSpec.Type.REPLACE, emptyList()))
        } else {
            add(
                LdapModificationSpec(
                    attr,
                    LdapModificationSpec.Type.REPLACE,
                    listOf(value.toByteArray(Charsets.UTF_8)),
                ),
            )
        }
    }

    private fun LdapEntryData.toForm(): UserEditForm {
        val uac = UserAccountControl.decode(firstString("userAccountControl"))
        val pwdLastSet = firstString("pwdLastSet")
        val mustChange = pwdLastSet == "0"
        return UserEditForm(
            dn = dn,
            givenName = firstString("givenName").orEmpty(),
            sn = firstString("sn").orEmpty(),
            displayName = firstString("displayName").orEmpty(),
            mail = firstString("mail").orEmpty(),
            telephoneNumber = firstString("telephoneNumber").orEmpty(),
            mobile = firstString("mobile").orEmpty(),
            wwwHomePage = firstString("wWWHomePage").orEmpty(),
            streetAddress = firstString("streetAddress").orEmpty(),
            city = firstString("l").orEmpty(),
            state = firstString("st").orEmpty(),
            postalCode = firstString("postalCode").orEmpty(),
            country = firstString("c") ?: firstString("co").orEmpty(),
            title = firstString("title").orEmpty(),
            department = firstString("department").orEmpty(),
            company = firstString("company").orEmpty(),
            samAccountName = firstString("sAMAccountName").orEmpty(),
            userPrincipalName = firstString("userPrincipalName").orEmpty(),
            memberOf = stringValues("memberOf"),
            primaryGroupId = firstString("primaryGroupID").orEmpty(),
            mustChangePassword = mustChange,
            passwordNeverExpires = uac?.passwordNeverExpires == true,
            enabled = uac?.enabled != false,
            sid = attributes.entries.firstOrNull { it.key.equals("objectSid", true) }
                ?.value?.firstOrNull()?.let { SidDecoder.tryDecode(it) },
            guid = attributes.entries.firstOrNull { it.key.equals("objectGUID", true) }
                ?.value?.firstOrNull()?.let { GuidDecoder.tryDecode(it) },
        )
    }
}
