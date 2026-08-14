package com.jbsan.ldapadvisor.data.ldap

import com.jbsan.ldapadvisor.core.logging.AppLogger
import com.jbsan.ldapadvisor.core.security.SecretStore
import com.jbsan.ldapadvisor.data.kerberos.KerberosTicketService
import com.jbsan.ldapadvisor.data.repository.ProfileRepository
import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.AuthMethod
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.ConnectionStatus
import com.jbsan.ldapadvisor.domain.model.DirectoryCapabilities
import com.jbsan.ldapadvisor.domain.model.TrustMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * Owns the active LDAP session.
 *
 * Keeps the bind alive with TCP keepalive + periodic RootDSE pings, and silently rebinds
 * after idle drops or brief network loss — until [disconnect] or total network loss without
 * recoverable credentials.
 */
class SessionManager(
    private val clientFactory: LdapClientFactory,
    private val profileRepository: ProfileRepository,
    private val secretStore: SecretStore,
    private val logger: AppLogger? = null,
    private val kerberosTicketService: KerberosTicketService = KerberosTicketService(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()
    private var session: LdapSession? = null
    private var keepAliveJob: Job? = null

    /** Profile id for the intentional session (survives socket close / networkLost). */
    private var activeProfileId: String? = null
    private var sessionAnonymous: Boolean = false
    private var sessionAllowPlaintext: Boolean = false
    private var sessionAllowInsecureTrust: Boolean = false
    /** In-memory password for silent rebind; cleared only on explicit [disconnect]/shutdown]. */
    private var sessionPassword: CharArray? = null

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _networkAvailable = MutableStateFlow(true)
    val networkAvailable: StateFlow<Boolean> = _networkAvailable.asStateFlow()

    fun currentSession(): LdapSession? = session

    /**
     * Network availability callback.
     * - Loss: close socket, keep session intent (`networkLost`), do not force Disconnected.
     * - Restore: silent rebind when credentials/profile are still available.
     */
    fun onNetworkAvailabilityChanged(available: Boolean) {
        _networkAvailable.value = available
        scope.launch {
            if (!available) {
                mutex.withLock {
                    val current = _status.value
                    if (current is ConnectionStatus.Connected && !current.networkLost) {
                        session?.close()
                        session = null
                        _status.value = current.copy(networkLost = true)
                        logger?.info(TAG, "Network lost — LDAP socket closed, session kept for reconnect")
                    }
                }
            } else {
                val current = _status.value
                if (current is ConnectionStatus.Connected && current.networkLost) {
                    logger?.info(TAG, "Network restored — attempting silent reconnect")
                    silentReconnect("network-restored")
                } else if (current is ConnectionStatus.Connected && session == null) {
                    silentReconnect("network-restored-no-socket")
                }
            }
        }
    }

    suspend fun connect(
        profile: ConnectionProfile,
        password: CharArray? = null,
        anonymous: Boolean = false,
        allowPlaintextSimpleBind: Boolean = false,
        allowInsecureTrust: Boolean = false,
    ): Result<LdapSession> = mutex.withLock {
        connectLocked(
            profile = profile,
            password = password,
            anonymous = anonymous,
            allowPlaintextSimpleBind = allowPlaintextSimpleBind,
            allowInsecureTrust = allowInsecureTrust,
            preserveCredentialsOnFailure = false,
        )
    }

    private suspend fun connectLocked(
        profile: ConnectionProfile,
        password: CharArray? = null,
        anonymous: Boolean = false,
        allowPlaintextSimpleBind: Boolean = false,
        allowInsecureTrust: Boolean = false,
        preserveCredentialsOnFailure: Boolean = false,
    ): Result<LdapSession> {
        stopKeepAliveLocked()
        session?.close()
        session = null
        if (!preserveCredentialsOnFailure) {
            // Replace credentials with the new connect attempt.
            clearSessionPasswordLocked()
        }

        _status.value = ConnectionStatus.Connecting
        val started = System.currentTimeMillis()

        // Retain password copy for keep-alive / network recovery (not written to disk unless rememberPassword).
        val passwordForSession = when {
            anonymous -> null
            password != null && password.isNotEmpty() -> password.copyOf()
            else -> secretStore.getPassword(profile.id)?.copyOf()
        }

        val hasCredentials = !anonymous &&
            passwordForSession != null &&
            passwordForSession.isNotEmpty()
        if (profile.trustMode == TrustMode.INSECURE_NO_VERIFY && hasCredentials && !allowInsecureTrust) {
            passwordForSession?.fill('\u0000')
            val err = AppError.InsecureTrustRequiresConfirmation()
            _status.value = ConnectionStatus.Error(err)
            return Result.failure(err)
        }

        val opened = clientFactory.open(profile).getOrElse { err ->
            passwordForSession?.fill('\u0000')
            val mapped = err as? AppError ?: LdapErrorMapper.map(err)
            _status.value = ConnectionStatus.Error(mapped)
            return Result.failure(mapped)
        }

        val client = opened.client
        val bindOutcome = when {
            anonymous || (
                profile.authMethod == AuthMethod.SIMPLE &&
                    profile.bindIdentity.isBlank() &&
                    (passwordForSession == null || passwordForSession.isEmpty())
                ) -> client.bindAnonymous()
            profile.authMethod == AuthMethod.KERBEROS -> {
                if (passwordForSession == null || passwordForSession.isEmpty()) {
                    client.disconnect()
                    val err = AppError.Validation("Kerberos password is required")
                    _status.value = ConnectionStatus.Error(err)
                    return Result.failure(err)
                }
                val bindPwd = passwordForSession.copyOf()
                val tokens = kerberosTicketService.acquireLdapBindTokens(profile, bindPwd).getOrElse { err ->
                    bindPwd.fill('\u0000')
                    passwordForSession.fill('\u0000')
                    client.disconnect()
                    val mapped = err as? AppError ?: AppError.KerberosFailure(err.message ?: "Kerberos failed")
                    _status.value = ConnectionStatus.Error(mapped)
                    return Result.failure(mapped)
                }
                bindPwd.fill('\u0000')
                client.bindKerberos(
                    principalLabel = tokens.principal,
                    spnegoToken = tokens.spnegoToken,
                    gssapiToken = tokens.gssapiToken,
                    allowInsecureTrustConfirmation = allowInsecureTrust,
                )
            }
            else -> {
                if (passwordForSession == null || passwordForSession.isEmpty()) {
                    client.disconnect()
                    val err = AppError.Validation("Password is required for this profile")
                    _status.value = ConnectionStatus.Error(err)
                    return Result.failure(err)
                } else {
                    val bindPwd = passwordForSession.copyOf()
                    client.bindSimple(
                        bindDn = profile.bindIdentity,
                        password = bindPwd,
                        allowPlaintextConfirmation = allowPlaintextSimpleBind,
                        allowInsecureTrustConfirmation = allowInsecureTrust,
                    )
                }
            }
        }

        val boundAs = when (bindOutcome) {
            is BindOutcome.RequiresPlaintextConfirmation -> {
                client.disconnect()
                passwordForSession?.fill('\u0000')
                val err = AppError.PlaintextBindRequiresConfirmation(bindOutcome.message)
                _status.value = ConnectionStatus.Error(err)
                return Result.failure(err)
            }
            is BindOutcome.RequiresInsecureTrustConfirmation -> {
                client.disconnect()
                passwordForSession?.fill('\u0000')
                val err = AppError.InsecureTrustRequiresConfirmation(bindOutcome.message)
                _status.value = ConnectionStatus.Error(err)
                return Result.failure(err)
            }
            is BindOutcome.Failure -> {
                client.disconnect()
                passwordForSession?.fill('\u0000')
                _status.value = ConnectionStatus.Error(bindOutcome.error)
                return Result.failure(bindOutcome.error)
            }
            is BindOutcome.Success -> bindOutcome.boundAs
        }

        val rootDse = client.readRootDse().getOrNull()
        val capabilities = rootDse?.toCapabilities() ?: DirectoryCapabilities()
        val effectiveBase = profile.baseDn.ifBlank { rootDse?.defaultNamingContext.orEmpty() }
        if (profile.baseDn.isBlank() && effectiveBase.isNotBlank()) {
            logger?.info(TAG, "Using defaultNamingContext as base DN")
        }

        val newSession = LdapSession(client, rootDse, capabilities)
        session = newSession
        activeProfileId = profile.id
        sessionAnonymous = anonymous || (
            profile.authMethod == AuthMethod.SIMPLE &&
                profile.bindIdentity.isBlank() &&
                (passwordForSession == null || passwordForSession.isEmpty())
            )
        sessionAllowPlaintext = allowPlaintextSimpleBind
        sessionAllowInsecureTrust = allowInsecureTrust
        clearSessionPasswordLocked()
        sessionPassword = passwordForSession

        val elapsed = System.currentTimeMillis() - started
        _status.value = ConnectionStatus.Connected(
            profileId = profile.id,
            host = profile.host,
            port = profile.port,
            securityMode = profile.securityMode,
            boundAs = boundAs,
            tlsActive = opened.tlsActive,
            responseTimeMs = elapsed,
            networkLost = false,
            insecureTrust = profile.trustMode == TrustMode.INSECURE_NO_VERIFY,
        )
        profileRepository.markLastSuccessfulConnection(profile.id)
        startKeepAliveLocked()
        return Result.success(newSession)
    }

    suspend fun disconnect() = mutex.withLock { disconnectLocked(clearCredentials = true) }

    fun shutdown() {
        scope.launch {
            mutex.withLock { disconnectLocked(clearCredentials = true) }
        }
        // Cancel keep-alive scope children; SupervisorJob cancelled via cancel on jobs only.
        keepAliveJob?.cancel()
    }

    private fun disconnectLocked(clearCredentials: Boolean) {
        stopKeepAliveLocked()
        session?.close()
        session = null
        if (clearCredentials) {
            activeProfileId = null
            sessionAnonymous = false
            sessionAllowPlaintext = false
            sessionAllowInsecureTrust = false
            clearSessionPasswordLocked()
        }
        _status.value = ConnectionStatus.Disconnected
    }

    suspend fun reconnect(
        allowPlaintextSimpleBind: Boolean = false,
        allowInsecureTrust: Boolean = false,
    ): Result<LdapSession> {
        sessionAllowPlaintext = allowPlaintextSimpleBind || sessionAllowPlaintext
        sessionAllowInsecureTrust = allowInsecureTrust || sessionAllowInsecureTrust
        return silentReconnect("manual-reconnect")
    }

    private suspend fun silentReconnect(reason: String): Result<LdapSession> = mutex.withLock {
        val profileId = activeProfileId
            ?: ( _status.value as? ConnectionStatus.Connected)?.profileId
            ?: return@withLock Result.failure(AppError.NotConnected())
        val profile = profileRepository.getById(profileId)
            ?: return@withLock Result.failure(AppError.Validation("Active profile no longer exists"))

        if (!_networkAvailable.value) {
            val current = _status.value
            if (current is ConnectionStatus.Connected) {
                _status.value = current.copy(networkLost = true)
            }
            return@withLock Result.failure(AppError.Generic("Network unavailable"))
        }

        logger?.info(TAG, "Silent reconnect ($reason) for profile $profileId")
        val pwd = sessionPassword?.copyOf()
            ?: if (profile.rememberPassword) secretStore.getPassword(profile.id)?.copyOf() else null
        val anonymous = sessionAnonymous || (
            profile.authMethod == AuthMethod.SIMPLE &&
                profile.bindIdentity.isBlank() &&
                (pwd == null || pwd.isEmpty())
            )
        try {
            connectLocked(
                profile = profile,
                password = pwd,
                anonymous = anonymous,
                allowPlaintextSimpleBind = sessionAllowPlaintext,
                allowInsecureTrust = sessionAllowInsecureTrust,
                preserveCredentialsOnFailure = true,
            )
        } finally {
            pwd?.fill('\u0000')
        }
    }

    private fun startKeepAliveLocked() {
        stopKeepAliveLocked()
        keepAliveJob = scope.launch {
            while (coroutineContext.isActive) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                maintainConnection()
            }
        }
    }

    private fun stopKeepAliveLocked() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private suspend fun maintainConnection() {
        if (!_networkAvailable.value) return
        val status = _status.value
        if (status !is ConnectionStatus.Connected) return
        if (status.networkLost) {
            silentReconnect("keep-alive-while-network-lost-cleared")
            return
        }

        val client = session?.client
        if (client == null) {
            silentReconnect("keep-alive-missing-session")
            return
        }

        val alive = client.isConnected() && client.ping().isSuccess
        if (!alive) {
            logger?.info(TAG, "LDAP keep-alive failed — rebinding")
            silentReconnect("keep-alive-failed")
        }
    }

    private fun clearSessionPasswordLocked() {
        sessionPassword?.fill('\u0000')
        sessionPassword = null
    }

    companion object {
        private const val TAG = "SessionManager"
        /** Faster than typical AD/firewall idle drops (often 3–15 minutes). */
        const val KEEP_ALIVE_INTERVAL_MS: Long = 60_000L
    }
}
