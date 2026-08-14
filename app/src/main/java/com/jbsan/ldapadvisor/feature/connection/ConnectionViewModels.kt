package com.jbsan.ldapadvisor.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.security.SecretStore
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.data.repository.ProfileRepository
import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val sessionManager: SessionManager,
    private val profileRepository: ProfileRepository,
    private val secretStore: SecretStore,
) : ViewModel() {
    val status: StateFlow<ConnectionStatus> = sessionManager.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatus.Disconnected)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _needPlaintext = MutableStateFlow(false)
    val needPlaintext: StateFlow<Boolean> = _needPlaintext.asStateFlow()
    private val _needInsecureTrust = MutableStateFlow(false)
    val needInsecureTrust: StateFlow<Boolean> = _needInsecureTrust.asStateFlow()
    private var pendingProfileId: String? = null

    fun confirmPlaintext() {
        val id = pendingProfileId ?: return
        connect(id, allowPlaintext = true, allowInsecureTrust = true)
    }

    fun confirmInsecureTrust() {
        val id = pendingProfileId ?: return
        connect(id, allowPlaintext = true, allowInsecureTrust = true)
    }

    private var connectJob: kotlinx.coroutines.Job? = null

    fun connect(
        profileId: String,
        password: CharArray? = null,
        allowPlaintext: Boolean = false,
        allowInsecureTrust: Boolean = false,
    ) {
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            val profile = profileRepository.getById(profileId) ?: return@launch
            val pwd = password ?: if (profile.rememberPassword) secretStore.getPassword(profileId) else null
            sessionManager.connect(
                profile,
                pwd,
                profile.bindIdentity.isBlank(),
                allowPlaintext,
                allowInsecureTrust,
            ).fold(
                onSuccess = {
                    _error.value = null
                    _needPlaintext.value = false
                    _needInsecureTrust.value = false
                    pendingProfileId = null
                },
                onFailure = { err ->
                    when (err) {
                        is AppError.PlaintextBindRequiresConfirmation -> {
                            pendingProfileId = profileId
                            _needPlaintext.value = true
                            _needInsecureTrust.value = false
                        }
                        is AppError.InsecureTrustRequiresConfirmation -> {
                            pendingProfileId = profileId
                            _needInsecureTrust.value = true
                            _needPlaintext.value = false
                        }
                        else -> {
                            _needPlaintext.value = false
                            _needInsecureTrust.value = false
                        }
                    }
                    _error.value = err.message
                },
            )
        }
    }

    fun dismissInsecureTrust() {
        _needInsecureTrust.value = false
        pendingProfileId = null
    }

    fun disconnect() = viewModelScope.launch { sessionManager.disconnect() }
}

class RootDseViewModel(private val sessionManager: SessionManager) : ViewModel() {
    private val _attrs = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val attrs: StateFlow<Map<String, List<String>>> = _attrs.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null) {
            _error.value = "Not connected"
            return@launch
        }
        session.client.readRootDse().fold(
            onSuccess = { _attrs.value = it.attributes; _error.value = null },
            onFailure = { _error.value = it.message },
        )
    }
}

class SchemaViewModel(private val sessionManager: SessionManager) : ViewModel() {
    private val _objectClasses = MutableStateFlow<List<String>>(emptyList())
    val objectClasses: StateFlow<List<String>> = _objectClasses.asStateFlow()
    private val _attributeTypes = MutableStateFlow<List<String>>(emptyList())
    val attributeTypes: StateFlow<List<String>> = _attributeTypes.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null) {
            _error.value = "Not connected"
            return@launch
        }
        session.client.getSchema().fold(
            onSuccess = { schema ->
                _objectClasses.value = schema.objectClasses.map { it.nameOrOID }.sorted()
                _attributeTypes.value = schema.attributeTypes.map { it.nameOrOID }.sorted()
                _error.value = null
            },
            onFailure = { _error.value = it.message },
        )
    }
}
