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
import com.jbsan.ldapadvisor.feature.directory.OuTreePickerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateUserForm(
    val parentDn: String = "",
    val cn: String = "",
    val sAMAccountName: String = "",
    val userPrincipalName: String = "",
    val uid: String = "",
    val displayName: String = "",
    val givenName: String = "",
    val sn: String = "",
    val initials: String = "",
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
    /** ISO country code attribute `c` (e.g. FR). */
    val country: String = "",
    /** Friendly country name `co` (AD). */
    val countryName: String = "",
    /** Numeric `countryCode` (AD, e.g. 250). */
    val countryCode: String = "",
    val description: String = "",
    val wwwHomePage: String = "",
    val homePhone: String = "",
    val pager: String = "",
    val facsimileTelephoneNumber: String = "",
    val employeeNumber: String = "",
    val employeeType: String = "",
    val departmentNumber: String = "",
    val roomNumber: String = "",
    val physicalDeliveryOfficeName: String = "",
    val postOfficeBox: String = "",
    /** Organization attribute `o` (inetOrgPerson). */
    val organization: String = "",
    val initialPassword: String = "",
    val enableAfterPassword: Boolean = false,
    val sourceDn: String = "",
    val sourceUid: String = "",
    val memberOf: List<String> = emptyList(),
    val primaryGroupId: String = "",
    val primaryGroupDn: String = "",
    val primaryGroupLabel: String = "",
    val copyGroups: Boolean = true,
    val copyPrimaryGroup: Boolean = true,
    /** Prefer CN= vs uid= RDN to match the source entry naming. */
    val preferUidRdn: Boolean = false,
    /**
     * Samba extras — only set when the source already had them.
     * Never invent sambaSamAccount / sambaSID on plain inetOrgPerson copies.
     */
    val sambaEnabled: Boolean = false,
    val sambaHadSid: Boolean = false,
    val sambaHadSeeAlso: Boolean = false,
    val sambaDomainName: String = "",
    val sambaAcctFlags: String = "",
    val sambaPrimaryGroupSID: String = "",
    val sambaSidTemplate: String = "",
    val sourceSeeAlso: String = "",
    val sambaHomePath: String = "",
    val sambaProfilePath: String = "",
    val sambaLogonScript: String = "",
    val sambaHomeDrive: String = "",
    /**
     * RFC 2307 posixAccount — only when present on source.
     * uidNumber is re-allocated; never copy userPassword / shadow hashes.
     */
    val posixEnabled: Boolean = false,
    val posixHadUidNumber: Boolean = false,
    val gidNumber: String = "",
    val homeDirectory: String = "",
    val loginShell: String = "",
    val gecos: String = "",
    /** shadowAccount objectClass only (no shadow* password fields copied). */
    val shadowEnabled: Boolean = false,
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
    val allowsPasswordChannel: Boolean = false,
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

    val ouPicker = OuTreePickerController(sessionManager, viewModelScope)

    enum class ParentTarget { USER, GROUP, OU }

    private var parentTarget: ParentTarget = ParentTarget.USER

    fun openParentPicker(target: ParentTarget, currentDn: String = "") {
        parentTarget = target
        val preselected = currentDn.ifBlank {
            when (target) {
                ParentTarget.USER -> _ui.value.userForm.parentDn
                ParentTarget.GROUP -> _ui.value.groupForm.parentDn
                ParentTarget.OU -> _ui.value.ouForm.parentDn
            }.ifBlank { _ui.value.defaultBaseDn }
        }
        ouPicker.open(preselected)
    }

    fun confirmParentPicker(dn: String) {
        val selected = dn.trim()
        if (selected.isBlank()) {
            ouPicker.dismiss()
            return
        }
        when (parentTarget) {
            ParentTarget.USER -> updateUser { it.copy(parentDn = selected) }
            ParentTarget.GROUP -> updateGroup { it.copy(parentDn = selected) }
            ParentTarget.OU -> updateOu { it.copy(parentDn = selected) }
        }
        ouPicker.dismiss()
    }

    fun refreshCaps() {
        val session = sessionManager.currentSession()
        val base = session?.capabilities?.defaultNamingContext.orEmpty()
        _ui.value = _ui.value.copy(
            connected = session?.isConnected() == true,
            isAd = session?.capabilities?.isActiveDirectory == true,
            readOnly = session?.readOnly != false,
            tlsActive = session?.tlsActive == true,
            allowsPasswordChannel = session?.allowsPasswordChannel == true,
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
                    "objectClass", "cn", "uid", "givenName", "sn", "initials", "displayName", "mail",
                    "telephoneNumber", "mobile", "homePhone", "pager", "facsimileTelephoneNumber",
                    "wWWHomePage", "labeledURI",
                    "streetAddress", "street", "l", "st", "postalCode", "c", "co", "countryCode",
                    "title", "department", "company", "o", "description", "seeAlso",
                    "employeeNumber", "employeeType", "departmentNumber", "roomNumber",
                    "physicalDeliveryOfficeName", "postOfficeBox",
                    "sAMAccountName", "userPrincipalName", "memberOf", "primaryGroupID", "objectSid",
                    "sambaSID", "sambaPrimaryGroupSID", "sambaDomainName", "sambaAcctFlags",
                    "sambaHomePath", "sambaProfilePath", "sambaLogonScript", "sambaHomeDrive",
                    "uidNumber", "gidNumber", "homeDirectory", "loginShell", "gecos",
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
        fun hasAttr(name: String): Boolean =
            entry.attributes.keys.any { it.equals(name, ignoreCase = true) } &&
                entry.firstString(name)?.isNotBlank() == true
        fun onlyIfPresent(name: String) = if (hasAttr(name)) s(name) else ""
        val isAd = session.capabilities.isActiveDirectory
        val objectClasses = entry.stringValues("objectClass").map { it.lowercase() }
        val hasSambaClass = objectClasses.any { it == "sambasamaccount" }
        val hasAnySambaAttr = listOf(
            "sambaSID", "sambaPrimaryGroupSID", "sambaDomainName", "sambaAcctFlags",
            "sambaHomePath", "sambaProfilePath", "sambaLogonScript", "sambaHomeDrive",
        ).any { hasAttr(it) }
        // Only engage Samba handling when the source already carries Samba data.
        val sambaEnabled = hasSambaClass || hasAnySambaAttr
        val sambaHadSid = hasAttr("sambaSID")
        val sambaHadSeeAlso = hasAttr("seeAlso")
        val hasPosixClass = objectClasses.any { it == "posixaccount" }
        val hasAnyPosixAttr = listOf("uidNumber", "gidNumber", "homeDirectory", "loginShell", "gecos")
            .any { hasAttr(it) }
        val posixEnabled = hasPosixClass || hasAnyPosixAttr
        val shadowEnabled = objectClasses.any { it == "shadowaccount" }
        val sam = s("sAMAccountName")
        val uid = s("uid")
        val cn = s("cn")
        val upn = s("userPrincipalName")
        val sourceParent = sourceDn.substringAfter(',', missingDelimiterValue = "").trim()
            .ifBlank { _ui.value.defaultBaseDn }
        val sourceRdnAttr = sourceDn.substringBefore('=').trim()
        val preferUidRdn = sourceRdnAttr.equals("uid", ignoreCase = true)
        val primaryId = s("primaryGroupID")
        val userSid = entry.attributes.entries
            .firstOrNull { it.key.equals("objectSid", true) }
            ?.value?.firstOrNull()
        val primary = if (isAd) resolvePrimaryGroup(primaryId, userSid, sourceDn) else null
        val copySam = if (sam.isBlank()) "" else "${sam}_copy".take(20)
        val copyUid = when {
            uid.isNotBlank() -> "${uid}_copy"
            sam.isNotBlank() -> "${sam}_copy".take(64)
            cn.isNotBlank() -> cn.lowercase().replace(Regex("[^a-z0-9._-]"), "")
                .ifBlank { "user" } + "_copy"
            else -> "user_copy"
        }
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
        val sambaPrimary = if (sambaEnabled && hasAttr("sambaPrimaryGroupSID")) {
            s("sambaPrimaryGroupSID")
        } else {
            ""
        }
        val sambaSidTemplate = if (sambaHadSid) {
            sambaDomainSidFrom(s("sambaSID")).orEmpty()
        } else {
            ""
        }
        _ui.value = _ui.value.copy(
            loadingSource = false,
            userForm = CreateUserForm(
                parentDn = sourceParent,
                cn = copyCn,
                sAMAccountName = copySam,
                userPrincipalName = copyUpn,
                uid = copyUid,
                displayName = s("displayName").ifBlank { copyCn },
                givenName = s("givenName"),
                sn = s("sn"),
                initials = s("initials"),
                mail = s("mail"),
                telephoneNumber = s("telephoneNumber"),
                mobile = s("mobile"),
                title = s("title"),
                department = s("department"),
                company = s("company"),
                streetAddress = s("streetAddress").ifBlank { s("street") },
                city = s("l"),
                state = s("st"),
                postalCode = s("postalCode"),
                // Keep c / co / countryCode distinct (AD sets all three).
                country = onlyIfPresent("c"),
                countryName = onlyIfPresent("co"),
                countryCode = onlyIfPresent("countryCode"),
                description = s("description"),
                wwwHomePage = s("wWWHomePage").ifBlank { s("labeledURI") },
                homePhone = onlyIfPresent("homePhone"),
                pager = onlyIfPresent("pager"),
                facsimileTelephoneNumber = onlyIfPresent("facsimileTelephoneNumber"),
                employeeNumber = onlyIfPresent("employeeNumber"),
                employeeType = onlyIfPresent("employeeType"),
                departmentNumber = onlyIfPresent("departmentNumber"),
                roomNumber = onlyIfPresent("roomNumber"),
                physicalDeliveryOfficeName = onlyIfPresent("physicalDeliveryOfficeName"),
                postOfficeBox = onlyIfPresent("postOfficeBox"),
                organization = onlyIfPresent("o"),
                sourceDn = sourceDn,
                sourceUid = uid,
                memberOf = entry.stringValues("memberOf"),
                primaryGroupId = primaryId,
                primaryGroupDn = primary?.first.orEmpty(),
                primaryGroupLabel = primary?.second.orEmpty(),
                copyGroups = entry.stringValues("memberOf").isNotEmpty(),
                copyPrimaryGroup = isAd && primary != null,
                enableAfterPassword = false,
                preferUidRdn = preferUidRdn,
                sambaEnabled = sambaEnabled,
                sambaHadSid = sambaHadSid,
                sambaHadSeeAlso = sambaHadSeeAlso,
                sambaDomainName = if (sambaEnabled && hasAttr("sambaDomainName")) s("sambaDomainName") else "",
                sambaAcctFlags = if (sambaEnabled && hasAttr("sambaAcctFlags")) s("sambaAcctFlags") else "",
                sambaPrimaryGroupSID = sambaPrimary,
                sambaSidTemplate = sambaSidTemplate,
                sourceSeeAlso = if (sambaHadSeeAlso) s("seeAlso") else "",
                sambaHomePath = if (sambaEnabled) onlyIfPresent("sambaHomePath") else "",
                sambaProfilePath = if (sambaEnabled) onlyIfPresent("sambaProfilePath") else "",
                sambaLogonScript = if (sambaEnabled) onlyIfPresent("sambaLogonScript") else "",
                sambaHomeDrive = if (sambaEnabled) onlyIfPresent("sambaHomeDrive") else "",
                posixEnabled = posixEnabled,
                posixHadUidNumber = hasAttr("uidNumber"),
                gidNumber = if (posixEnabled) onlyIfPresent("gidNumber") else "",
                homeDirectory = if (posixEnabled) onlyIfPresent("homeDirectory") else "",
                loginShell = if (posixEnabled) onlyIfPresent("loginShell") else "",
                gecos = if (posixEnabled) onlyIfPresent("gecos") else "",
                shadowEnabled = shadowEnabled,
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
        if (session.readOnly) {
            _ui.value = _ui.value.copy(error = AppError.ReadOnlyViolation().message)
            return
        }
        val isAd = session.capabilities.isActiveDirectory
        if (!isAd && !isCopy) {
            _ui.value = _ui.value.copy(error = "Creating users from scratch requires Active Directory for now")
            return
        }
        if (isAd) {
            createOrCopyAdUser(isCopy)
        } else {
            createOrCopyLdapUser(isCopy)
        }
    }

    private suspend fun createOrCopyAdUser(isCopy: Boolean) {
        val session = sessionManager.currentSession() ?: return
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
        // Never set objectSid / objectGUID — AD generates fresh values on add.
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
        put("initials", form.initials)
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
        put("co", form.countryName)
        put("countryCode", form.countryCode)
        put("description", form.description)
        put("wWWHomePage", form.wwwHomePage)
        put("homePhone", form.homePhone)
        put("pager", form.pager)
        put("facsimileTelephoneNumber", form.facsimileTelephoneNumber)
        put("employeeNumber", form.employeeNumber)
        put("employeeType", form.employeeType)
        put("departmentNumber", form.departmentNumber)
        put("roomNumber", form.roomNumber)
        put("physicalDeliveryOfficeName", form.physicalDeliveryOfficeName)
        put("postOfficeBox", form.postOfficeBox)
        put("o", form.organization)

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
            if (!session.allowsPasswordChannel) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    createdDn = dn,
                    error = "Password requires LDAPS/StartTLS or Kerberos. User left disabled.",
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

        // AD assigns a new objectSid / objectGUID — never copy those from the source.
        message += formatNewDirectoryIds(session, dn, isAd = true)

        _ui.value = _ui.value.copy(
            busy = false,
            createdDn = dn,
            success = if (warnings.isEmpty()) message
            else "$message (warnings: ${warnings.joinToString("; ")})",
            error = null,
            userForm = form.copy(initialPassword = ""),
        )
    }

    private suspend fun createOrCopyLdapUser(isCopy: Boolean) {
        val session = sessionManager.currentSession() ?: return
        val form = _ui.value.userForm
        if (form.parentDn.isBlank() || form.cn.isBlank() || form.sn.isBlank()) {
            _ui.value = _ui.value.copy(
                error = "Parent DN, CN and surname (sn) are required for LDAP users",
            )
            return
        }
        val useUidRdn = form.preferUidRdn && form.uid.isNotBlank()
        val dn = runCatching {
            if (useUidRdn) {
                DnUtils.buildChildDn("uid", form.uid, form.parentDn)
            } else {
                DnUtils.buildChildDn("CN", form.cn, form.parentDn)
            }
        }.getOrElse {
            _ui.value = _ui.value.copy(error = it.message)
            return
        }
        _ui.value = _ui.value.copy(busy = true, error = null, success = null, createdDn = null)

        // Never set entryUUID / sambaPwdLastSet / shadow password hashes.
        // Samba / posix / shadow only if present on source.
        val objectClasses = linkedSetOf("top", "person", "organizationalPerson", "inetOrgPerson")
        if (form.sambaEnabled) objectClasses += "sambaSamAccount"
        val canPosix = form.posixEnabled &&
            form.posixHadUidNumber &&
            form.gidNumber.isNotBlank() &&
            (form.homeDirectory.isNotBlank() || form.uid.isNotBlank())
        if (canPosix) objectClasses += "posixAccount"
        if (form.shadowEnabled) objectClasses += "shadowAccount"
        val attrs = linkedMapOf(
            "objectClass" to objectClasses.toList(),
            "cn" to listOf(form.cn.trim()),
            "sn" to listOf(form.sn.trim()),
        )
        fun put(attr: String, value: String) {
            if (value.isNotBlank()) attrs[attr] = listOf(value.trim())
        }
        put("uid", form.uid)
        put("displayName", form.displayName)
        put("givenName", form.givenName)
        put("initials", form.initials)
        put("mail", form.mail)
        put("telephoneNumber", form.telephoneNumber)
        put("mobile", form.mobile)
        put("homePhone", form.homePhone)
        put("pager", form.pager)
        put("facsimileTelephoneNumber", form.facsimileTelephoneNumber)
        put("title", form.title)
        put("department", form.department)
        put("company", form.company)
        put("o", form.organization)
        put("street", form.streetAddress)
        put("streetAddress", form.streetAddress)
        put("l", form.city)
        put("st", form.state)
        put("postalCode", form.postalCode)
        put("c", form.country)
        put("co", form.countryName)
        put("description", form.description)
        put("labeledURI", form.wwwHomePage)
        put("wWWHomePage", form.wwwHomePage)
        put("employeeNumber", form.employeeNumber)
        put("employeeType", form.employeeType)
        put("departmentNumber", form.departmentNumber)
        put("roomNumber", form.roomNumber)
        put("physicalDeliveryOfficeName", form.physicalDeliveryOfficeName)
        put("postOfficeBox", form.postOfficeBox)

        val warnings = mutableListOf<String>()
        if (form.posixEnabled && !canPosix) {
            warnings += "posixAccount skipped (source missing uidNumber/gidNumber/homeDirectory)"
        }
        if (canPosix) {
            val newUid = form.uid.trim()
            put("uidNumber", allocateUidNumber(newUid.ifBlank { form.cn }))
            put("gidNumber", form.gidNumber)
            val home = adaptedHomeDirectory(form.homeDirectory, form.sourceUid, newUid)
                .ifBlank { if (newUid.isNotBlank()) "/home/$newUid" else "" }
            put("homeDirectory", home)
            put("loginShell", form.loginShell)
            put("gecos", form.gecos)
        }
        if (form.sambaEnabled) {
            put("sambaDomainName", form.sambaDomainName)
            put("sambaAcctFlags", form.sambaAcctFlags)
            put("sambaPrimaryGroupSID", form.sambaPrimaryGroupSID)
            put("sambaHomePath", form.sambaHomePath)
            put("sambaProfilePath", form.sambaProfilePath)
            put("sambaLogonScript", form.sambaLogonScript)
            put("sambaHomeDrive", form.sambaHomeDrive)
            // sambaSID: only when the source already had one — allocate a fresh RID, never reuse.
            if (form.sambaHadSid) {
                val newSid = allocateSambaSid(form.sambaSidTemplate, form.uid.ifBlank { form.cn })
                if (newSid != null) {
                    put("sambaSID", newSid)
                } else {
                    warnings += "sambaSID skipped (could not derive domain SID from source)"
                }
            }
            if (form.sambaHadSeeAlso) {
                val seeAlso = when {
                    form.sourceSeeAlso.equals(form.sourceDn, ignoreCase = true) -> dn
                    form.sourceSeeAlso.isNotBlank() -> form.sourceSeeAlso
                    else -> dn
                }
                put("seeAlso", seeAlso)
            }
        }

        val addResult = session.client.add(dn, attrs)
        if (addResult.isFailure) {
            _ui.value = _ui.value.copy(
                busy = false,
                error = addResult.exceptionOrNull()?.message,
            )
            return
        }

        var message = if (isCopy) "User copied: $dn" else "User created: $dn"

        if (form.initialPassword.isNotEmpty()) {
            if (!session.allowsPasswordChannel) {
                warnings += "password skipped (LDAPS/StartTLS or Kerberos required)"
            } else if (session.capabilities.supportsPasswordModify) {
                val pwd = session.client.changePasswordPasswordModify(
                    userIdentity = dn,
                    oldPassword = null,
                    newPassword = form.initialPassword.toCharArray(),
                )
                if (pwd.isFailure) {
                    warnings += "password: ${pwd.exceptionOrNull()?.message}"
                } else {
                    message = if (isCopy) "User copied with password: $dn" else "User created with password: $dn"
                }
            } else {
                warnings += "password skipped (Password Modify not supported)"
            }
        }

        if (isCopy && form.copyGroups) {
            for (groupDn in form.memberOf) {
                session.client.addGroupMember(groupDn, dn).onFailure { err ->
                    warnings += "group $groupDn: ${err.message}"
                }
            }
        }

        message += formatNewDirectoryIds(session, dn, isAd = false)
        if (form.sambaHadSid) {
            val sid = attrs["sambaSID"]?.firstOrNull()
            if (!sid.isNullOrBlank()) message += " — new sambaSID=$sid"
        }
        if (form.posixHadUidNumber && canPosix) {
            val uidNum = attrs["uidNumber"]?.firstOrNull()
            if (!uidNum.isNullOrBlank()) message += " — new uidNumber=$uidNum"
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

    /** Domain part of a Samba SID: S-1-5-21-a-b-c-RID → S-1-5-21-a-b-c */
    private fun sambaDomainSidFrom(sid: String): String? {
        val parts = sid.trim().split('-')
        if (parts.size < 8 || !parts[0].equals("S", true) || parts[1] != "1") return null
        return parts.dropLast(1).joinToString("-")
    }

    /**
     * Allocate a new sambaSID under [domainSid] using a stable RID derived from [seed]
     * (uid/cn). Avoids copying the source account SID.
     */
    private fun allocateSambaSid(domainSid: String, seed: String): String? {
        val domain = domainSid.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalized = domain.removeSuffix("-").ifBlank { return null }
        val rid = 10_000 + (seed.trim().lowercase().hashCode().toLong().and(0x7fff_ffffL) % 1_000_000L)
        return "$normalized-$rid"
    }

    /** Fresh posix uidNumber derived from seed — never reuse the source uidNumber. */
    private fun allocateUidNumber(seed: String): String {
        val n = 10_000 + (seed.trim().lowercase().hashCode().toLong().and(0x7fff_ffffL) % 1_000_000L)
        return n.toString()
    }

    private fun adaptedHomeDirectory(home: String, sourceUid: String, newUid: String): String {
        val h = home.trim()
        if (h.isEmpty()) return ""
        val old = sourceUid.trim()
        val neu = newUid.trim()
        if (old.isEmpty() || neu.isEmpty() || old.equals(neu, ignoreCase = true)) return h
        return h.replace(old, neu, ignoreCase = true)
    }

    /**
     * After create/copy, surface server-assigned identity attributes.
     * Source objectSid / objectGUID / entryUUID must never be written on add.
     */
    private suspend fun formatNewDirectoryIds(
        session: com.jbsan.ldapadvisor.data.ldap.LdapSession,
        dn: String,
        isAd: Boolean,
    ): String {
        val attrs = if (isAd) {
            arrayOf("objectSid", "objectGUID")
        } else {
            arrayOf("entryUUID", "entryDN", "sambaSID")
        }
        val entry = session.client.search(
            LdapSearchRequest(dn, "(objectClass=*)", SearchScopeMode.BASE, attrs),
        ).getOrNull()?.firstOrNull() ?: return ""
        return if (isAd) {
            val sid = entry.attributes.entries
                .firstOrNull { it.key.equals("objectSid", true) }
                ?.value?.firstOrNull()
                ?.let { SidDecoder.tryDecode(it) }
            val guid = entry.attributes.entries
                .firstOrNull { it.key.equals("objectGUID", true) }
                ?.value?.firstOrNull()
                ?.let { com.jbsan.ldapadvisor.core.ad.GuidDecoder.tryDecode(it) }
            buildString {
                if (!sid.isNullOrBlank()) append(" — new SID=$sid")
                if (!guid.isNullOrBlank()) append(" — new GUID=$guid")
            }
        } else {
            buildString {
                entry.firstString("entryUUID")?.takeIf { it.isNotBlank() }?.let {
                    append(" — entryUUID=$it")
                }
                entry.firstString("sambaSID")?.takeIf { it.isNotBlank() }?.let {
                    append(" — sambaSID=$it")
                }
            }
        }
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
        if (!session.capabilities.isActiveDirectory) {
            _ui.value = _ui.value.copy(error = "Active Directory session required")
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
