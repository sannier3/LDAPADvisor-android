package com.jbsan.ldapadvisor.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.security.SecretStore
import com.jbsan.ldapadvisor.data.diagnostics.TcpDiagnosticService
import com.jbsan.ldapadvisor.data.diagnostics.TcpPortProbe
import com.jbsan.ldapadvisor.data.dns.AdDiscoveryService
import com.jbsan.ldapadvisor.data.dns.DiscoveredDc
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.data.repository.ProfileRepository
import com.jbsan.ldapadvisor.data.repository.SettingsRepository
import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.domain.model.SecurityMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfilesUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val connecting: Boolean = false,
    val requirePassword: Boolean = false,
    val requirePlaintextConfirm: Boolean = false,
    val requireInsecureTrustConfirm: Boolean = false,
    val pendingConnectId: String? = null,
    val pendingBindIdentity: String = "",
    val discovered: List<DiscoveredDc> = emptyList(),
)

class ProfilesViewModel(
    private val profileRepository: ProfileRepository,
    private val sessionManager: SessionManager,
    private val secretStore: SecretStore,
    private val adDiscoveryService: AdDiscoveryService,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _ui.asStateFlow()

    /** Kept only while a connect confirmation dialog is pending; wiped on success/dismiss. */
    private var pendingPassword: CharArray? = null

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            profiles.collect { list ->
                _ui.value = _ui.value.copy(profiles = list)
            }
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        profileRepository.delete(id)
    }

    fun duplicate(id: String) = viewModelScope.launch {
        profileRepository.duplicate(id)
    }

    fun connect(
        id: String,
        password: CharArray? = null,
        allowPlaintext: Boolean = false,
        allowInsecureTrust: Boolean = false,
    ) = viewModelScope.launch {
        val profile = profileRepository.getById(id) ?: return@launch
        val needsPassword = profile.bindIdentity.isNotBlank() ||
            profile.authMethod == com.jbsan.ldapadvisor.domain.model.AuthMethod.KERBEROS
        val stored = if (profile.rememberPassword) secretStore.getPassword(id) else null

        if (password != null && password.isNotEmpty()) {
            clearPendingPassword()
            pendingPassword = password.copyOf()
        }

        // Always copy buffers — never alias `stored` then wipe it before bind.
        val effectivePassword = when {
            pendingPassword != null && pendingPassword!!.isNotEmpty() -> pendingPassword!!.copyOf()
            password != null && password.isNotEmpty() -> password.copyOf()
            stored != null && stored.isNotEmpty() -> stored.copyOf()
            else -> null
        }
        password?.fill('\u0000')
        stored?.fill('\u0000')

        if (needsPassword && (effectivePassword == null || effectivePassword.isEmpty())) {
            effectivePassword?.fill('\u0000')
            _ui.value = _ui.value.copy(
                requirePassword = true,
                pendingConnectId = id,
                pendingBindIdentity = profile.bindIdentity.ifBlank { profile.name },
                connecting = false,
                error = null,
                requirePlaintextConfirm = false,
                requireInsecureTrustConfirm = false,
            )
            return@launch
        }

        _ui.value = _ui.value.copy(
            connecting = true,
            error = null,
            message = null,
            requirePassword = false,
        )
        val result = sessionManager.connect(
            profile = profile,
            password = effectivePassword?.copyOf(),
            anonymous = !needsPassword,
            allowPlaintextSimpleBind = allowPlaintext,
            allowInsecureTrust = allowInsecureTrust,
        )
        effectivePassword?.fill('\u0000')
        result.fold(
            onSuccess = {
                clearPendingPassword()
                _ui.value = _ui.value.copy(
                    message = "connected",
                    error = null,
                    connecting = false,
                    requirePassword = false,
                    requirePlaintextConfirm = false,
                    requireInsecureTrustConfirm = false,
                    pendingConnectId = null,
                    pendingBindIdentity = "",
                )
            },
            onFailure = { err ->
                when (err) {
                    is AppError.PlaintextBindRequiresConfirmation -> {
                        _ui.value = _ui.value.copy(
                            connecting = false,
                            requirePlaintextConfirm = true,
                            requireInsecureTrustConfirm = false,
                            requirePassword = false,
                            pendingConnectId = id,
                            error = err.message,
                        )
                    }
                    is AppError.InsecureTrustRequiresConfirmation -> {
                        _ui.value = _ui.value.copy(
                            connecting = false,
                            requireInsecureTrustConfirm = true,
                            requirePlaintextConfirm = false,
                            requirePassword = false,
                            pendingConnectId = id,
                            error = err.message,
                        )
                    }
                    else -> {
                        clearPendingPassword()
                        _ui.value = _ui.value.copy(
                            connecting = false,
                            error = err.message,
                            requirePlaintextConfirm = false,
                            requireInsecureTrustConfirm = false,
                        )
                    }
                }
            },
        )
    }

    fun submitPassword(password: String) {
        val id = _ui.value.pendingConnectId ?: return
        connect(id, password = password.toCharArray())
    }

    fun confirmPlaintextConnect() {
        val id = _ui.value.pendingConnectId ?: return
        connect(id = id, allowPlaintext = true, allowInsecureTrust = false)
    }

    fun confirmInsecureTrustConnect() {
        val id = _ui.value.pendingConnectId ?: return
        connect(id = id, allowPlaintext = true, allowInsecureTrust = true)
    }

    fun dismissPasswordPrompt() {
        clearPendingPassword()
        _ui.value = _ui.value.copy(
            requirePassword = false,
            pendingConnectId = null,
            pendingBindIdentity = "",
            connecting = false,
        )
    }

    fun dismissPlaintext() {
        clearPendingPassword()
        _ui.value = _ui.value.copy(
            requirePlaintextConfirm = false,
            requireInsecureTrustConfirm = false,
            pendingConnectId = null,
            connecting = false,
        )
    }

    fun dismissInsecureTrust() {
        clearPendingPassword()
        _ui.value = _ui.value.copy(
            requireInsecureTrustConfirm = false,
            pendingConnectId = null,
            connecting = false,
        )
    }

    fun consumeConnectedMessage() {
        if (_ui.value.message == "connected") {
            _ui.value = _ui.value.copy(message = null)
        }
    }

    private fun clearPendingPassword() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
    }

    override fun onCleared() {
        clearPendingPassword()
        super.onCleared()
    }

    fun discover(domain: String) = viewModelScope.launch {
        val result = adDiscoveryService.discover(domain)
        val discovered = result.getOrNull()?.let { discovery ->
            (discovery.ldapDcMsdcs + discovery.ldapTcp).distinctBy { dc -> dc.hostname }
        }.orEmpty()
        _ui.value = _ui.value.copy(
            discovered = discovered,
            error = result.exceptionOrNull()?.message,
        )
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null, error = null)
    }

    suspend fun defaultReadOnly(): Boolean = settingsRepository.settings.first().readOnlyByDefault
}

data class ProfileFormState(
    val id: String? = null,
    val name: String = "",
    val directoryType: String = "AUTO",
    val domain: String = "",
    val host: String = "",
    val port: String = "636",
    val securityMode: String = "LDAPS",
    val authMethod: String = "SIMPLE",
    val bindIdentity: String = "",
    val password: String = "",
    val baseDn: String = "",
    val connectTimeoutMs: String = "5000",
    val readTimeoutMs: String = "10000",
    val followReferrals: Boolean = false,
    val trustMode: String = "SYSTEM",
    val customCaId: String = "",
    val pinnedFingerprint: String = "",
    val rememberPassword: Boolean = false,
    val readOnly: Boolean = true,
    val kerberosRealm: String = "",
    val kerberosKdcHost: String = "",
    val kerberosKdcPort: String = "88",
    val kerberosServicePrincipal: String = "",
    val nameError: String? = null,
    val hostError: String? = null,
    val portError: String? = null,
    val formError: String? = null,
    val saved: Boolean = false,
    val discovered: List<DiscoveredDc> = emptyList(),
    /** hostname -> Open / Timeout / Refused / … */
    val dcTcpResults: Map<String, String> = emptyMap(),
    val dcTcpTesting: String? = null,
)

class ProfileEditViewModel(
    private val profileRepository: ProfileRepository,
    private val secretStore: SecretStore,
    private val settingsRepository: SettingsRepository,
    private val adDiscoveryService: AdDiscoveryService,
    private val customCaRepository: com.jbsan.ldapadvisor.data.repository.CustomCaRepository,
    private val tcpDiagnosticService: TcpDiagnosticService,
) : ViewModel() {
    private val _form = MutableStateFlow(ProfileFormState())
    val form: StateFlow<ProfileFormState> = _form.asStateFlow()

    val customCas: StateFlow<List<com.jbsan.ldapadvisor.data.database.entity.CustomCaEntity>> =
        customCaRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(profileId: String?) = viewModelScope.launch {
        val defaults = settingsRepository.settings.first()
        if (profileId == null) {
            _form.value = ProfileFormState(
                readOnly = defaults.readOnlyByDefault,
                connectTimeoutMs = defaults.defaultConnectTimeoutMs.toString(),
                readTimeoutMs = defaults.defaultReadTimeoutMs.toString(),
            )
            return@launch
        }
        val p = profileRepository.getById(profileId) ?: return@launch
        _form.value = ProfileFormState(
            id = p.id,
            name = p.name,
            directoryType = p.directoryType.name,
            domain = p.domain,
            host = p.host,
            port = p.port.toString(),
            securityMode = p.securityMode.name,
            authMethod = p.authMethod.name,
            bindIdentity = p.bindIdentity,
            baseDn = p.baseDn,
            connectTimeoutMs = p.connectTimeoutMs.toString(),
            readTimeoutMs = p.readTimeoutMs.toString(),
            followReferrals = p.followReferrals,
            trustMode = p.trustMode.name,
            customCaId = p.customCaId.orEmpty(),
            pinnedFingerprint = p.pinnedFingerprint.orEmpty(),
            rememberPassword = p.rememberPassword,
            readOnly = p.readOnly,
            kerberosRealm = p.kerberosRealm,
            kerberosKdcHost = p.kerberosKdcHost,
            kerberosKdcPort = p.kerberosKdcPort.toString(),
            kerberosServicePrincipal = p.kerberosServicePrincipal,
        )
    }

    fun importCa(alias: String, bytes: ByteArray) = viewModelScope.launch {
        customCaRepository.importFromBytes(alias, bytes).fold(
            onSuccess = { entity ->
                _form.value = _form.value.copy(
                    customCaId = entity.id,
                    trustMode = "CUSTOM_CA",
                    formError = null,
                )
            },
            onFailure = { err ->
                _form.value = _form.value.copy(formError = err.message)
            },
        )
    }

    fun selectCa(id: String) {
        _form.value = _form.value.copy(customCaId = id, trustMode = "CUSTOM_CA", formError = null)
    }

    fun deleteCa(id: String) = viewModelScope.launch {
        customCaRepository.delete(id)
        if (_form.value.customCaId == id) {
            _form.value = _form.value.copy(customCaId = "")
        }
    }

    fun update(transform: (ProfileFormState) -> ProfileFormState) {
        _form.value = transform(_form.value)
    }

    fun onSecurityChanged(mode: String) {
        val defaultPort = runCatching { SecurityMode.valueOf(mode).defaultPort() }.getOrDefault(636)
        _form.value = _form.value.copy(securityMode = mode, port = defaultPort.toString())
    }

    fun discover() = viewModelScope.launch {
        val domain = _form.value.domain
        val result = adDiscoveryService.discover(domain)
        _form.value = _form.value.copy(
            discovered = result.getOrNull()?.ldapDcMsdcs.orEmpty() + result.getOrNull()?.ldapTcp.orEmpty(),
            formError = result.exceptionOrNull()?.message,
        )
    }

    fun pickDc(dc: DiscoveredDc) {
        _form.value = _form.value.copy(host = dc.hostname, port = dc.port.toString())
    }

    fun testDcTcp(dc: DiscoveredDc) = viewModelScope.launch {
        val mode = runCatching { SecurityMode.valueOf(_form.value.securityMode) }
            .getOrDefault(SecurityMode.LDAPS)
        val port = when (mode) {
            SecurityMode.LDAPS -> SecurityMode.PORT_LDAPS
            SecurityMode.LDAP, SecurityMode.START_TLS -> SecurityMode.PORT_LDAP
        }
        val label = when (mode) {
            SecurityMode.LDAPS -> "LDAPS"
            else -> "LDAP"
        }
        _form.value = _form.value.copy(dcTcpTesting = dc.hostname)
        val result = tcpDiagnosticService.probeHost(
            host = dc.hostname,
            ports = listOf(TcpPortProbe(port, label)),
            timeoutMs = 3_000,
            concurrency = 1,
        ).firstOrNull()
        val statusLabel = when {
            result == null -> "Unknown"
            result.status == DiagnosticStatus.SUCCESS -> "Open"
            result.summary.contains("refused", true) -> "Refused"
            result.summary.contains("Timeout", true) || result.summary.contains("timed out", true) -> "Timeout"
            else -> result.summary
        }
        val updated = _form.value.dcTcpResults.toMutableMap()
        updated[dc.hostname] = "$statusLabel (TCP/$port)"
        _form.value = _form.value.copy(dcTcpResults = updated, dcTcpTesting = null)
    }

    fun save() = viewModelScope.launch {
        val f = _form.value
        var nameError: String? = null
        var hostError: String? = null
        var portError: String? = null
        if (f.name.isBlank()) nameError = "name"
        if (f.host.isBlank()) hostError = "host"
        val port = f.port.toIntOrNull()
        if (port == null || port !in 1..65535) portError = "port"
        if (f.trustMode == "CUSTOM_CA" && f.customCaId.isBlank()) {
            _form.value = f.copy(formError = "Select an imported Custom CA")
            return@launch
        }
        if (nameError != null || hostError != null || portError != null) {
            _form.value = f.copy(nameError = nameError, hostError = hostError, portError = portError)
            return@launch
        }
        if (f.authMethod == "KERBEROS") {
            if (f.bindIdentity.isBlank()) {
                _form.value = f.copy(formError = "Kerberos principal is required")
                return@launch
            }
            val realm = f.kerberosRealm.ifBlank { f.domain }.trim()
            if (realm.isBlank()) {
                _form.value = f.copy(formError = "Kerberos realm or AD domain is required")
                return@launch
            }
        }
        val now = System.currentTimeMillis()
        val id = f.id ?: UUID.randomUUID().toString()
        val existing = f.id?.let { profileRepository.getById(it) }
        val profile = ConnectionProfile(
            id = id,
            name = f.name.trim(),
            directoryType = runCatching {
                com.jbsan.ldapadvisor.domain.model.DirectoryType.valueOf(f.directoryType)
            }.getOrDefault(com.jbsan.ldapadvisor.domain.model.DirectoryType.AUTO),
            domain = f.domain.trim(),
            host = f.host.trim(),
            port = port!!,
            securityMode = SecurityMode.valueOf(f.securityMode),
            authMethod = runCatching {
                com.jbsan.ldapadvisor.domain.model.AuthMethod.valueOf(f.authMethod)
            }.getOrDefault(com.jbsan.ldapadvisor.domain.model.AuthMethod.SIMPLE),
            bindIdentity = f.bindIdentity.trim(),
            baseDn = f.baseDn.trim(),
            connectTimeoutMs = f.connectTimeoutMs.toIntOrNull() ?: 5000,
            readTimeoutMs = f.readTimeoutMs.toIntOrNull() ?: 10000,
            followReferrals = f.followReferrals,
            trustMode = com.jbsan.ldapadvisor.domain.model.parseTrustMode(f.trustMode),
            customCaId = f.customCaId.ifBlank { null },
            pinnedFingerprint = f.pinnedFingerprint.ifBlank { null },
            rememberPassword = f.rememberPassword,
            readOnly = f.readOnly,
            kerberosRealm = f.kerberosRealm.ifBlank { f.domain }.trim().uppercase(),
            kerberosKdcHost = f.kerberosKdcHost.trim(),
            kerberosKdcPort = f.kerberosKdcPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: 88,
            kerberosServicePrincipal = f.kerberosServicePrincipal.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastSuccessfulConnectionAt = existing?.lastSuccessfulConnectionAt,
        )
        val result = profileRepository.save(profile)
        result.fold(
            onSuccess = {
                if (f.rememberPassword && f.password.isNotEmpty()) {
                    secretStore.savePassword(id, f.password.toCharArray())
                } else if (!f.rememberPassword) {
                    secretStore.deletePassword(id)
                }
                _form.value = f.copy(id = id, saved = true, formError = null, password = "")
            },
            onFailure = { err ->
                _form.value = f.copy(formError = err.message)
            },
        )
    }

    fun forgetPassword() = viewModelScope.launch {
        val id = _form.value.id ?: return@launch
        secretStore.deletePassword(id)
        _form.value = _form.value.copy(rememberPassword = false, password = "")
        val existing = profileRepository.getById(id) ?: return@launch
        profileRepository.save(existing.copy(rememberPassword = false))
    }
}
