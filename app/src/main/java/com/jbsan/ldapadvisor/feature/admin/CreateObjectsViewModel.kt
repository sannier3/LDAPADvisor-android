package com.jbsan.ldapadvisor.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.GroupTypeDecoder
import com.jbsan.ldapadvisor.core.ad.SidDecoder
import com.jbsan.ldapadvisor.core.ad.UserAccountControl
import com.jbsan.ldapadvisor.core.util.DnUtils
import com.jbsan.ldapadvisor.core.util.LdapFilterEscaper
import com.jbsan.ldapadvisor.data.ldap.LdapModificationSpec
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.domain.model.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateUserForm(
    val parentDn: String = "",
    val cn: String = "",
    val sAMAccountName: String = "",
    val userPrincipalName: String = "",
    val displayName: String = "",
    val givenName: String = "",
    val sn: String = "",
    val mail: String = "",
    val telephoneNumber: String = "",
    val mobile: String = "",
    val title: String = "",
    val department: String = "",
    val company: String = "",
    val streetAddress: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "",
    val description: String = "",
    val wwwHomePage: String = "",
    val initialPassword: String = "",
    val enableAfterPassword: Boolean = false,
    val sourceDn: String = "",
    val memberOf: List<String> = emptyList(),
    val primaryGroupId: String = "",
    val primaryGroupDn: String = "",
    val primaryGroupLabel: String = "",
    val copyGroups: Boolean = true,
    val copyPrimaryGroup: Boolean = true,
)

data class CreateGroupForm(
    val parentDn: String = "",
    val cn: String = "",
    val sAMAccountName: String = "",
    val description: String = "",
    val scope: GroupTypeDecoder.Scope = GroupTypeDecoder.Scope.GLOBAL,
    val security: Boolean = true,
)

data class CreateOuForm(
    val parentDn: String = "",
    val ouName: String = "",
    val description: String = "",
)

data class CreateObjectsUiState(
    val connected: Boolean = false,
    val isAd: Boolean = false,
    val readOnly: Boolean = true,
    val tlsActive: Boolean = false,
    val defaultBaseDn: String = "",
    val userForm: CreateUserForm = CreateUserForm(),
    val groupForm: CreateGroupForm = CreateGroupForm(),
    val ouForm: CreateOuForm = CreateOuForm(),
    val busy: Boolean = false,
    val loadingSource: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val createdDn: String? = null,
)

class CreateObjectsViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(CreateObjectsUiState())
    val uiState: StateFlow<CreateObjectsUiState> = _ui.asStateFlow()

    fun refreshCaps() {
        val session = sessionManager.currentSession()
        val base = session?.capabilities?.defaultNamingContext.orEmpty()
        _ui.value = _ui.value.copy(
            connected = session?.isConnected() == true,
            isAd = session?.capabilities?.isActiveDirectory == true,
            readOnly = session?.readOnly != false,
            tlsActive = session?.tlsActive == true,
            defaultBaseDn = base,
            userForm = _ui.value.userForm.let { if (it.parentDn.isBlank()) it.copy(parentDn = base) else it },
            groupForm = _ui.value.groupForm.let { if (it.parentDn.isBlank()) it.copy(parentDn = base) else it },
            ouForm = _ui.value.ouForm.let { if (it.parentDn.isBlank()) it.copy(parentDn = base) else it },
        )
    }

    fun updateUser(transform: (CreateUserForm) -> CreateUserForm) {
        _ui.value = _ui.value.copy(userForm = transform(_ui.value.userForm), error = null, success = null)
    }

    fun updateGroup(transform: (CreateGroupForm) -> CreateGroupForm) {
        _ui.value = _ui.value.copy(groupForm = transform(_ui.value.groupForm), error = null, success = null)
    }

    fun updateOu(transform: (CreateOuForm) -> CreateOuForm) {
        _ui.value = _ui.value.copy(ouForm = transform(_ui.value.ouForm), error = null, success = null)
    }

    fun loadUserForCopy(sourceDn: String) = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null || !session.isConnected()) {
            _ui.value = _ui.value.copy(error = "Not connected", loadingSource = false)
            return@launch
        }
        refreshCaps()
        _ui.value = _ui.value.copy(loadingSource = true, error = null, success = null)
        val entry = session.client.search(
            LdapSearchRequest(
                baseDn = sourceDn,
                filter = "(objectClass=*)",
                scope = SearchScopeMode.BASE,
                attributes = arrayOf(
                    "cn", "givenName", "sn", "displayName", "mail", "telephoneNumber", "mobile",
                    "wWWHomePage", "streetAddress", "l", "st", "postalCode", "c", "co",
                    "title", "department", "company", "description",
                    "sAMAccountName", "userPrincipalName", "memberOf", "primaryGroupID", "objectSid",
                ),
            ),
        ).getOrElse {
            _ui.value = _ui.value.copy(loadingSource = false, error = it.message)
            return@launch
        }.firstOrNull()
        if (entry == null) {
            _ui.value = _ui.value.copy(loadingSource = false, error = "Source user not found")
            return@launch
        }
        fun s(name: String) = entry.firstString(name).orEmpty()
        val sam = s("sAMAccountName")
        val cn = s("cn")
        val upn = s("userPrincipalName")
        val sourceParent = sourceDn.substringAfter(',', missingDelimiterValue = "").trim()
            .ifBlank { _ui.value.defaultBaseDn }
        val primaryId = s("primaryGroupID")
        val userSid = entry.attributes.entries
            .firstOrNull { it.key.equals("objectSid", true) }
            ?.value?.firstOrNull()
        val primary = resolvePrimaryGroup(primaryId, userSid, sourceDn)
        val copySam = if (sam.isBlank()) "" else "${sam}_copy".take(20)
        val copyCn = if (cn.isBlank()) "Copy" else "$cn (copy)"
        val copyUpn = when {
            upn.contains('@') -> {
                val local = upn.substringBefore('@')
                val domain = upn.substringAfter('@')
                "${local}_copy@$domain"
            }
            upn.isNotBlank() -> "${upn}_copy"
            else -> ""
        }
        _ui.value = _ui.value.copy(
            loadingSource = false,
            userForm = CreateUserForm(
                parentDn = sourceParent,
                cn = copyCn,
                sAMAccountName = copySam,
                userPrincipalName = copyUpn,
                displayName = s("displayName").ifBlank { copyCn },
                givenName = s("givenName"),
                sn = s("sn"),
                mail = s("mail"),
                telephoneNumber = s("telephoneNumber"),
                mobile = s("mobile"),
                title = s("title"),
                department = s("department"),
                company = s("company"),
                streetAddress = s("streetAddress"),
                city = s("l"),
                state = s("st"),
                postalCode = s("postalCode"),
                country = s("c").ifBlank { s("co") },
                description = s("description"),
                wwwHomePage = s("wWWHomePage"),
                sourceDn = sourceDn,
                memberOf = entry.stringValues("memberOf"),
                primaryGroupId = primaryId,
                primaryGroupDn = primary?.first.orEmpty(),
                primaryGroupLabel = primary?.second.orEmpty(),
                copyGroups = true,
                copyPrimaryGroup = true,
                enableAfterPassword = false,
            ),
        )
    }

    fun createUser() = viewModelScope.launch {
        createOrCopyUser(isCopy = false)
    }

    fun copyUser() = viewModelScope.launch {
        createOrCopyUser(isCopy = true)
    }

    private suspend fun createOrCopyUser(isCopy: Boolean) {
        val session = sessionManager.currentSession()
        if (session == null || !session.isConnected()) {
            _ui.value = _ui.value.copy(error = "Not connected")
            return
        }
        if (!session.capabilities.isActiveDirectory) {
            _ui.value = _ui.value.copy(error = "Active Directory session required")
            return
        }
        if (session.readOnly) {
            _ui.value = _ui.value.copy(error = AppError.ReadOnlyViolation().message)
            return
        }
        val form = _ui.value.userForm
        if (form.cn.isBlank() || form.sAMAccountName.isBlank() || form.parentDn.isBlank()) {
            _ui.value = _ui.value.copy(error = "Destination OU, CN and sAMAccountName are required")
            return
        }
        if (isCopy && form.initialPassword.isBlank()) {
            _ui.value = _ui.value.copy(error = "An initial password is required when copying a user")
            return
        }
        val dn = runCatching {
            DnUtils.buildChildDn("CN", form.cn, form.parentDn)
        }.getOrElse {
            _ui.value = _ui.value.copy(error = it.message)
            return
        }
        _ui.value = _ui.value.copy(busy = true, error = null, success = null, createdDn = null)
        val uac = UserAccountControl.NORMAL_ACCOUNT or UserAccountControl.ACCOUNTDISABLE
        val attrs = linkedMapOf(
            "objectClass" to listOf("top", "person", "organizationalPerson", "user"),
            "cn" to listOf(form.cn.trim()),
            "sAMAccountName" to listOf(form.sAMAccountName.trim()),
            "userAccountControl" to listOf(uac.toString()),
        )
        fun put(attr: String, value: String) {
            if (value.isNotBlank()) attrs[attr] = listOf(value.trim())
        }
        put("userPrincipalName", form.userPrincipalName)
        put("displayName", form.displayName)
        put("givenName", form.givenName)
        put("sn", form.sn)
        put("mail", form.mail)
        put("telephoneNumber", form.telephoneNumber)
        put("mobile", form.mobile)
        put("title", form.title)
        put("department", form.department)
        put("company", form.company)
        put("streetAddress", form.streetAddress)
        put("l", form.city)
        put("st", form.state)
        put("postalCode", form.postalCode)
        put("c", form.country)
        put("description", form.description)
        put("wWWHomePage", form.wwwHomePage)

        val addResult = session.client.add(dn, attrs)
        if (addResult.isFailure) {
            val withoutUac = attrs.toMutableMap().apply { remove("userAccountControl") }
            val retry = session.client.add(dn, withoutUac)
            if (retry.isFailure) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = retry.exceptionOrNull()?.message ?: addResult.exceptionOrNull()?.message,
                )
                return
            }
            session.client.setAdAccountDisabled(dn, true)
        }

        var message = if (isCopy) "User copied (disabled): $dn" else "User created (disabled): $dn"
        val warnings = mutableListOf<String>()

        if (form.initialPassword.isNotEmpty()) {
            if (!session.tlsActive) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    createdDn = dn,
                    error = "Password requires LDAPS/StartTLS. User left disabled.",
                    success = message,
                )
                return
            }
            val pwdResult = session.client.resetAdPassword(dn, form.initialPassword.toCharArray())
            if (pwdResult.isFailure) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    createdDn = dn,
                    error = "Password set failed: ${pwdResult.exceptionOrNull()?.message}. Account left disabled.",
                    success = message,
                )
                return
            }
            message = if (isCopy) "User copied; password set; still disabled: $dn"
            else "User created; password set; still disabled: $dn"
            if (form.enableAfterPassword) {
                val enable = session.client.setAdAccountDisabled(dn, false)
                if (enable.isFailure) {
                    warnings += "enable failed: ${enable.exceptionOrNull()?.message}"
                } else {
                    message = if (isCopy) "User copied, password set, and enabled: $dn"
                    else "User created, password set, and enabled: $dn"
                }
            }
        }

        if (isCopy && form.copyGroups) {
            for (groupDn in form.memberOf) {
                session.client.addGroupMember(groupDn, dn).onFailure { err ->
                    warnings += "group $groupDn: ${err.message}"
                }
            }
        }
        if (isCopy && form.copyPrimaryGroup && form.primaryGroupDn.isNotBlank()) {
            setPrimaryGroup(dn, form.primaryGroupDn)?.let { warnings += it }
        }

        _ui.value = _ui.value.copy(
            busy = false,
            createdDn = dn,
            success = if (warnings.isEmpty()) message
            else "$message (warnings: ${warnings.joinToString("; ")})",
            error = null,
            userForm = form.copy(initialPassword = ""),
        )
    }

    private suspend fun setPrimaryGroup(userDn: String, groupDn: String): String? {
        val session = sessionManager.currentSession() ?: return "no session"
        val group = session.client.search(
            LdapSearchRequest(groupDn, "(objectClass=group)", SearchScopeMode.BASE, arrayOf("objectSid")),
        ).getOrNull()?.firstOrNull()
        val sid = group?.attributes?.entries?.firstOrNull { it.key.equals("objectSid", true) }
            ?.value?.firstOrNull()
        val rid = SidDecoder.tryExtractRid(sid) ?: return "primary group RID unresolved"
        session.client.addGroupMember(groupDn, userDn)
        return session.client.modify(
            userDn,
            listOf(
                LdapModificationSpec(
                    "primaryGroupID",
                    LdapModificationSpec.Type.REPLACE,
                    listOf(rid.toString().toByteArray(Charsets.UTF_8)),
                ),
            ),
        ).exceptionOrNull()?.message?.let { "primary group: $it" }
    }

    private suspend fun resolvePrimaryGroup(
        primaryGroupId: String,
        userSidBytes: ByteArray?,
        userDn: String,
    ): Pair<String, String>? {
        val session = sessionManager.currentSession() ?: return null
        val rid = primaryGroupId.toIntOrNull() ?: return null
        val groupSidBytes = SidDecoder.tryWithRid(userSidBytes, rid) ?: return null
        val base = session.capabilities.defaultNamingContext
            ?: userDn.split(',').dropWhile { !it.trim().startsWith("DC=", true) }.joinToString(",")
        if (base.isBlank()) return null
        val filter = LdapFilterEscaper.and(
            "(objectClass=group)",
            LdapFilterEscaper.equalsBinaryFilter("objectSid", groupSidBytes),
        )
        val found = session.client.search(
            LdapSearchRequest(base, filter, SearchScopeMode.SUB, arrayOf("cn"), sizeLimit = 3, pageSize = 3),
        ).getOrNull()?.firstOrNull()
        return found?.let { it.dn to (it.firstString("cn") ?: it.dn) }
    }

    fun createGroup() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null || !session.isConnected()) {
            _ui.value = _ui.value.copy(error = "Not connected")
            return@launch
        }
        if (session.readOnly) {
            _ui.value = _ui.value.copy(error = AppError.ReadOnlyViolation().message)
            return@launch
        }
        val form = _ui.value.groupForm
        if (form.cn.isBlank() || form.parentDn.isBlank()) {
            _ui.value = _ui.value.copy(error = "Parent DN and CN are required")
            return@launch
        }
        val dn = runCatching {
            DnUtils.buildChildDn("CN", form.cn, form.parentDn)
        }.getOrElse {
            _ui.value = _ui.value.copy(error = it.message)
            return@launch
        }
        _ui.value = _ui.value.copy(busy = true, error = null, success = null)
        val groupType = GroupTypeDecoder.encode(form.scope, form.security)
        val attrs = linkedMapOf(
            "objectClass" to listOf("top", "group"),
            "cn" to listOf(form.cn.trim()),
            "groupType" to listOf(groupType.toString()),
        )
        if (form.sAMAccountName.isNotBlank()) {
            attrs["sAMAccountName"] = listOf(form.sAMAccountName.trim())
        }
        if (form.description.isNotBlank()) {
            attrs["description"] = listOf(form.description.trim())
        }
        session.client.add(dn, attrs).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(busy = false, createdDn = dn, success = "Group created: $dn")
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(busy = false, error = err.message)
            },
        )
    }

    fun createOu() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null || !session.isConnected()) {
            _ui.value = _ui.value.copy(error = "Not connected")
            return@launch
        }
        if (session.readOnly) {
            _ui.value = _ui.value.copy(error = AppError.ReadOnlyViolation().message)
            return@launch
        }
        val form = _ui.value.ouForm
        if (form.ouName.isBlank() || form.parentDn.isBlank()) {
            _ui.value = _ui.value.copy(error = "Parent DN and OU name are required")
            return@launch
        }
        val dn = runCatching {
            DnUtils.buildChildDn("OU", form.ouName, form.parentDn)
        }.getOrElse {
            _ui.value = _ui.value.copy(error = it.message)
            return@launch
        }
        _ui.value = _ui.value.copy(busy = true, error = null, success = null)
        val attrs = linkedMapOf(
            "objectClass" to listOf("top", "organizationalUnit"),
            "ou" to listOf(form.ouName.trim()),
        )
        if (form.description.isNotBlank()) {
            attrs["description"] = listOf(form.description.trim())
        }
        session.client.add(dn, attrs).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(busy = false, createdDn = dn, success = "OU created: $dn")
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(busy = false, error = err.message)
            },
        )
    }
}
