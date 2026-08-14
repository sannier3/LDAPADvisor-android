package com.jbsan.ldapadvisor.feature.directory

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.DirectoryObjectClassifier
import com.jbsan.ldapadvisor.core.ad.DirectoryObjectKind
import com.jbsan.ldapadvisor.core.util.DnUtils
import com.jbsan.ldapadvisor.core.util.HexUtils
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapModificationSpec
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.data.repository.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ObjectEditForm(
    val displayName: String = "",
    val givenName: String = "",
    val sn: String = "",
    val mail: String = "",
    val telephoneNumber: String = "",
    val title: String = "",
    val department: String = "",
    val description: String = "",
    val cn: String = "",
    val ou: String = "",
    val dnsHostName: String = "",
)

data class ObjectDetailsUiState(
    val dn: String = "",
    val entry: LdapEntryData? = null,
    val kind: DirectoryObjectKind = DirectoryObjectKind.GENERIC,
    val displayName: String = "",
    val form: ObjectEditForm = ObjectEditForm(),
    val overview: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val connected: Boolean = false,
    val readOnly: Boolean = true,
    val favorite: Boolean = false,
    val busy: Boolean = false,
    val compareResult: String? = null,
)

class ObjectDetailsViewModel(
    private val sessionManager: SessionManager,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ObjectDetailsUiState())
    val uiState: StateFlow<ObjectDetailsUiState> = _ui.asStateFlow()

    fun load(dn: String) = viewModelScope.launch {
        val session = sessionManager.currentSession()
        _ui.value = ObjectDetailsUiState(
            dn = dn,
            loading = true,
            connected = session?.isConnected() == true,
            readOnly = session?.readOnly != false,
            favorite = favoritesRepository.isFavorite(dn),
        )
        if (session == null) {
            _ui.value = _ui.value.copy(loading = false, error = "Not connected")
            return@launch
        }
        val result = session.client.search(
            LdapSearchRequest(dn, "(objectClass=*)", SearchScopeMode.BASE, arrayOf("*", "+")),
        )
        result.fold(
            onSuccess = { list ->
                val entry = list.firstOrNull()
                val classes = entry?.stringValues("objectClass").orEmpty()
                val kind = DirectoryObjectClassifier.classify(classes)
                val display = DirectoryObjectClassifier.displayName(
                    dn = dn,
                    objectClasses = classes,
                    name = entry?.firstString("name"),
                    cn = entry?.firstString("cn"),
                    ou = entry?.firstString("ou"),
                    dc = entry?.firstString("dc"),
                    displayName = entry?.firstString("displayName"),
                    samAccountName = entry?.firstString("sAMAccountName"),
                )
                _ui.value = _ui.value.copy(
                    entry = entry,
                    kind = kind,
                    displayName = display,
                    form = entry.toForm(),
                    loading = false,
                    error = null,
                )
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(loading = false, error = err.message)
            },
        )
    }

    fun setOverview(v: Boolean) {
        _ui.value = _ui.value.copy(overview = v)
    }

    fun updateForm(transform: (ObjectEditForm) -> ObjectEditForm) {
        _ui.value = _ui.value.copy(form = transform(_ui.value.form), message = null, error = null)
    }

    fun savePrimaryAttributes() = viewModelScope.launch {
        val session = sessionManager.currentSession() ?: return@launch
        if (session.readOnly) {
            _ui.value = _ui.value.copy(error = "read_only")
            return@launch
        }
        val dn = _ui.value.dn
        val f = _ui.value.form
        val kind = _ui.value.kind
        val mods = buildList {
            when (kind) {
                DirectoryObjectKind.USER, DirectoryObjectKind.CONTACT -> {
                    addReplace("givenName", f.givenName)
                    addReplace("sn", f.sn)
                    addReplace("displayName", f.displayName)
                    addReplace("mail", f.mail)
                    addReplace("telephoneNumber", f.telephoneNumber)
                    addReplace("title", f.title)
                    addReplace("department", f.department)
                    addReplace("description", f.description)
                }
                DirectoryObjectKind.GROUP -> {
                    addReplace("description", f.description)
                    addReplace("mail", f.mail)
                }
                DirectoryObjectKind.COMPUTER -> {
                    addReplace("description", f.description)
                    addReplace("dNSHostName", f.dnsHostName)
                }
                DirectoryObjectKind.OU, DirectoryObjectKind.CONTAINER, DirectoryObjectKind.DOMAIN -> {
                    addReplace("description", f.description)
                }
                DirectoryObjectKind.GENERIC -> {
                    addReplace("description", f.description)
                    addReplace("cn", f.cn)
                }
            }
        }
        if (mods.isEmpty()) return@launch
        _ui.value = _ui.value.copy(saving = true, error = null)
        session.client.modify(dn, mods).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(saving = false, message = "saved")
                load(dn)
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(saving = false, error = err.message)
            },
        )
    }

    fun toggleFavorite() = viewModelScope.launch {
        val dn = _ui.value.dn
        if (dn.isBlank()) return@launch
        val nowFavorite = favoritesRepository.toggleFavorite(dn, DnUtils.objectNameFromDn(dn))
        _ui.value = _ui.value.copy(favorite = nowFavorite)
    }

    fun deleteObject(confirmName: String) = viewModelScope.launch {
        val dn = _ui.value.dn
        val expected = DnUtils.objectNameFromDn(dn)
        if (!confirmName.equals(expected, ignoreCase = true) && !confirmName.equals(dn, ignoreCase = true)) {
            _ui.value = _ui.value.copy(error = "Confirmation name does not match")
            return@launch
        }
        val session = sessionManager.currentSession() ?: return@launch
        _ui.value = _ui.value.copy(busy = true, error = null)
        session.client.delete(dn).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(busy = false, message = "deleted", entry = null)
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(busy = false, error = err.message)
            },
        )
    }

    fun rename(newRdn: String) = viewModelScope.launch {
        val dn = _ui.value.dn
        val session = sessionManager.currentSession() ?: return@launch
        val rdn = newRdn.trim()
        if (rdn.isBlank() || !rdn.contains('=')) {
            _ui.value = _ui.value.copy(error = "New RDN must look like CN=Name")
            return@launch
        }
        _ui.value = _ui.value.copy(busy = true, error = null)
        session.client.modifyDn(dn, rdn, deleteOldRdn = true, newSuperior = null).fold(
            onSuccess = {
                val parent = dn.substringAfter(',', missingDelimiterValue = "")
                val nextDn = if (parent.isBlank()) rdn else "$rdn,$parent"
                load(nextDn)
                _ui.value = _ui.value.copy(busy = false, message = "renamed")
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(busy = false, error = err.message)
            },
        )
    }

    fun move(newSuperiorDn: String) = viewModelScope.launch {
        val dn = _ui.value.dn
        val session = sessionManager.currentSession() ?: return@launch
        val superior = newSuperiorDn.trim()
        if (superior.isBlank()) {
            _ui.value = _ui.value.copy(error = "New superior DN required")
            return@launch
        }
        val currentRdn = dn.substringBefore(',')
        _ui.value = _ui.value.copy(busy = true, error = null)
        session.client.modifyDn(dn, currentRdn, deleteOldRdn = true, newSuperior = superior).fold(
            onSuccess = {
                load("$currentRdn,$superior")
                _ui.value = _ui.value.copy(busy = false, message = "moved")
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(busy = false, error = err.message)
            },
        )
    }

    fun modifyAttribute(
        attribute: String,
        operation: LdapModificationSpec.Type,
        value: String,
        oldValue: String = "",
    ) = viewModelScope.launch {
        val dn = _ui.value.dn
        val session = sessionManager.currentSession() ?: return@launch
        val attr = attribute.trim()
        if (attr.isBlank()) {
            _ui.value = _ui.value.copy(error = "Attribute name required")
            return@launch
        }
        val values = when (operation) {
            LdapModificationSpec.Type.DELETE ->
                if (value.isBlank()) emptyList() else listOf(value.toByteArray())
            else -> listOf(value.toByteArray())
        }
        _ui.value = _ui.value.copy(busy = true, error = null)
        session.client.modify(
            dn,
            listOf(LdapModificationSpec(attr, operation, values)),
        ).fold(
            onSuccess = {
                load(dn)
                _ui.value = _ui.value.copy(
                    busy = false,
                    message = "modify ${operation.name} $attr" +
                        if (oldValue.isNotBlank()) " (was: $oldValue)" else "",
                )
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(busy = false, error = err.message)
            },
        )
    }

    fun compare(attribute: String, assertion: String) = viewModelScope.launch {
        val dn = _ui.value.dn
        val session = sessionManager.currentSession() ?: return@launch
        if (attribute.isBlank()) {
            _ui.value = _ui.value.copy(error = "Attribute name required")
            return@launch
        }
        _ui.value = _ui.value.copy(busy = true, error = null, compareResult = null)
        session.client.compare(dn, attribute.trim(), assertion).fold(
            onSuccess = { matched ->
                _ui.value = _ui.value.copy(
                    busy = false,
                    compareResult = if (matched) "matched" else "not_matched",
                )
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(busy = false, error = err.message)
            },
        )
    }

    fun hexPreview(bytes: ByteArray, max: Int = 64): String =
        HexUtils.toHex(bytes.take(max).toByteArray(), separator = " ")

    fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

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

    private fun LdapEntryData?.toForm(): ObjectEditForm {
        if (this == null) return ObjectEditForm()
        return ObjectEditForm(
            displayName = firstString("displayName").orEmpty(),
            givenName = firstString("givenName").orEmpty(),
            sn = firstString("sn").orEmpty(),
            mail = firstString("mail").orEmpty(),
            telephoneNumber = firstString("telephoneNumber").orEmpty(),
            title = firstString("title").orEmpty(),
            department = firstString("department").orEmpty(),
            description = firstString("description").orEmpty(),
            cn = firstString("cn").orEmpty(),
            ou = firstString("ou").orEmpty(),
            dnsHostName = firstString("dNSHostName").orEmpty(),
        )
    }
}
