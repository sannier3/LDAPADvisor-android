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
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withContext
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
    /** Detached from [keepAliveJob] so [connectLocked] cancel does not abort reconnect. */
    private var reconnectJob: Job? = null
    /** Last successful Connected snapshot for restoring networkLost after aborted reconnect. */
    private var lastConnected: ConnectionStatus.Connected? = null

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
                    enqueueSilentReconnect("network-restored")
                } else if (current is ConnectionStatus.Connected && session == null) {
                    enqueueSilentReconnect("network-restored-no-socket")
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
    ): Result<LdapSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            connectLocked(
                profile = profile,
                password = password,
                anonymous = anonymous,
                allowPlaintextSimpleBind = allowPlaintextSimpleBind,
                allowInsecureTrust = allowInsecureTrust,
                preserveCredentialsOnFailure = false,
            )
        }
    }

    private suspend fun connectLocked(
        profile: ConnectionProfile,
        password: CharArray? = null,
        anonymous: Boolean = false,
        allowPlaintextSimpleBind: Boolean = false,
        allowInsecureTrust: Boolean = false,
        preserveCredentialsOnFailure: Boolean = false,
    ): Result<LdapSession> {
        // User-driven connect replaces any in-flight silent reconnect.
        if (!preserveCredentialsOnFailure) {
            reconnectJob?.cancel()
            reconnectJob = null
        }
        stopKeepAliveLocked()
        session?.close()
        session = null
        if (!preserveCredentialsOnFailure) {
            // Replace credentials with the new connect attempt.
            clearSessionPasswordLocked()
        }

        _status.value = ConnectionStatus.Connecting
        val started = System.currentTimeMillis()
        logger?.debug(
            TAG,
            "Connect start host=${profile.host}:${profile.port} security=${profile.securityMode} " +
                "auth=${profile.authMethod} trust=${profile.trustMode} domain=${profile.domain} " +
                "bindIdentity=${profile.bindIdentity} " +
                "kdc=${profile.kerberosKdcHost.ifBlank { profile.host }}:" +
                "${profile.kerberosKdcPort.takeIf { it in 1..65535 } ?: 88} " +
                "realm=${profile.kerberosRealm.ifBlank { profile.domain }} " +
                "spn=${profile.kerberosServicePrincipal.ifBlank { "ldap/${profile.host}" }} " +
                "anonymous=$anonymous allowPlaintext=$allowPlaintextSimpleBind " +
                "allowInsecureTrust=$allowInsecureTrust",
        )

        // Retain password copy for keep-alive / network recovery (not written to disk unless rememberPassword).
        val passwordForSession = when {
            anonymous -> null
            password != null && password.isNotEmpty() -> password.copyOf()
            else -> secretStore.getPassword(profile.id)?.copyOf()
        }
        logger?.debug(
            TAG,
            "Credentials ready hasPassword=${passwordForSession != null && passwordForSession.isNotEmpty()} " +
                "fromSecretStore=${password == null || password.isEmpty()}",
        )

        val hasCredentials = !anonymous &&
            passwordForSession != null &&
            passwordForSession.isNotEmpty()
        if (profile.trustMode == TrustMode.INSECURE_NO_VERIFY && hasCredentials && !allowInsecureTrust) {
            passwordForSession?.fill('\u0000')
            val err = AppError.InsecureTrustRequiresConfirmation()
            _status.value = ConnectionStatus.Error(err)
            logger?.debug(TAG, "Blocked: insecure trust requires confirmation")
            return Result.failure(err)
        }

        logger?.debug(TAG, "Opening LDAP transport…")
        val opened = clientFactory.open(profile).getOrElse { err ->
            passwordForSession?.fill('\u0000')
            val mapped = err as? AppError ?: LdapErrorMapper.map(err)
            logger?.error(
                TAG,
                "Transport open failed: ${mapped.message} details=${mapped.technicalDetails}",
                err as? Throwable,
            )
            _status.value = ConnectionStatus.Error(mapped)
            return Result.failure(mapped)
        }
        logger?.debug(TAG, "Transport open OK tlsActive=${opened.tlsActive}")

        val client = opened.client
        val bindOutcome = when {
            anonymous || (
                profile.authMethod == AuthMethod.SIMPLE &&
                    profile.bindIdentity.isBlank() &&
                    (passwordForSession == null || passwordForSession.isEmpty())
                ) -> {
                logger?.debug(TAG, "Bind anonymous")
                client.bindAnonymous()
            }
            profile.authMethod == AuthMethod.KERBEROS -> {
                if (passwordForSession == null || passwordForSession.isEmpty()) {
                    client.disconnect()
                    val err = AppError.Validation("Kerberos password is required")
                    _status.value = ConnectionStatus.Error(err)
                    return Result.failure(err)
                }
                logger?.debug(TAG, "Kerberos: acquiring tickets then SASL bind")
                val bindPwd = passwordForSession.copyOf()
                val tokens = kerberosTicketService.acquireLdapBindTokens(profile, bindPwd).getOrElse { err ->
                    bindPwd.fill('\u0000')
                    passwordForSession.fill('\u0000')
                    client.disconnect()
                    val mapped = err as? AppError ?: AppError.KerberosFailure(err.message ?: "Kerberos failed")
                    logger?.error(
                        TAG,
                        "Kerberos ticket step failed: ${mapped.message} " +
                            "details=${mapped.technicalDetails}",
                        err as? Throwable,
                    )
                    _status.value = ConnectionStatus.Error(mapped)
                    return Result.failure(mapped)
                }
                bindPwd.fill('\u0000')
                logger?.debug(
                    TAG,
                    "Kerberos tickets OK principal=${tokens.principal} spn=${tokens.servicePrincipal}",
                )
                client.bindKerberos(
                    principalLabel = tokens.principal,
                    spnegoToken = tokens.spnegoToken,
                    gssapiToken = tokens.gssapiToken,
                    mutualAuth = tokens.mutualAuth,
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
                    logger?.debug(TAG, "Simple bind identity=${profile.bindIdentity}")
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
                logger?.debug(TAG, "Bind needs plaintext confirmation")
                _status.value = ConnectionStatus.Error(err)
                return Result.failure(err)
            }
            is BindOutcome.RequiresInsecureTrustConfirmation -> {
                client.disconnect()
                passwordForSession?.fill('\u0000')
                val err = AppError.InsecureTrustRequiresConfirmation(bindOutcome.message)
                logger?.debug(TAG, "Bind needs insecure-trust confirmation")
                _status.value = ConnectionStatus.Error(err)
                return Result.failure(err)
            }
            is BindOutcome.Failure -> {
                client.disconnect()
                passwordForSession?.fill('\u0000')
                logger?.error(
                    TAG,
                    "Bind failed: ${bindOutcome.error.message} " +
                        "details=${bindOutcome.error.technicalDetails}",
                )
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
        val connected = ConnectionStatus.Connected(
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
        lastConnected = connected
        _status.value = connected
        logger?.info(TAG, "Connected boundAs=$boundAs tls=${opened.tlsActive} durationMs=$elapsed")
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
        reconnectJob?.cancel()
        reconnectJob = null
        session?.close()
        session = null
        if (clearCredentials) {
            activeProfileId = null
            sessionAnonymous = false
            sessionAllowPlaintext = false
            sessionAllowInsecureTrust = false
            clearSessionPasswordLocked()
            lastConnected = null
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

    private suspend fun silentReconnect(reason: String): Result<LdapSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val profileId = activeProfileId
                ?: (_status.value as? ConnectionStatus.Connected)?.profileId
                ?: return@withLock Result.failure(AppError.NotConnected())
            val profile = profileRepository.getById(profileId)
                ?: return@withLock Result.failure(AppError.Validation("Active profile no longer exists"))

            if (!_networkAvailable.value) {
                markNetworkLostLocked()
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
            // Must not call silentReconnect on this job: connectLocked cancels keepAliveJob.
            enqueueSilentReconnect("keep-alive-while-network-lost-cleared")
            return
        }

        val client = session?.client
        if (client == null) {
            enqueueSilentReconnect("keep-alive-missing-session")
            return
        }

        val alive = client.isConnected() && client.ping().isSuccess
        if (!alive) {
            logger?.info(TAG, "LDAP keep-alive failed — rebinding")
            enqueueSilentReconnect("keep-alive-failed")
        }
    }

    /**
     * Run silent reconnect on a detached job.
     *
     * [connectLocked] cancels [keepAliveJob]; if reconnect ran on that same job, cancellation
     * aborted mid-connect and left [ConnectionStatus.Connecting] stuck on the dashboard.
     */
    private fun enqueueSilentReconnect(reason: String) {
        if (reconnectJob?.isActive == true) {
            logger?.debug(TAG, "Silent reconnect already in progress — skip ($reason)")
            return
        }
        reconnectJob = scope.launch {
            try {
                silentReconnect(reason)
            } catch (e: CancellationException) {
                logger?.debug(TAG, "Silent reconnect cancelled ($reason)")
                markNetworkLostIfConnecting()
                throw e
            }
        }
    }

    private fun markNetworkLostIfConnecting() {
        scope.launch {
            mutex.withLock {
                if (_status.value is ConnectionStatus.Connecting && activeProfileId != null) {
                    markNetworkLostLocked()
                }
            }
        }
    }

    private fun markNetworkLostLocked() {
        session?.close()
        session = null
        val snapshot = lastConnected
        if (snapshot != null) {
            _status.value = snapshot.copy(networkLost = true, tlsActive = false)
            logger?.info(TAG, "Marked networkLost for profile ${snapshot.profileId}")
            return
        }
        val current = _status.value
        if (current is ConnectionStatus.Connected) {
            _status.value = current.copy(networkLost = true)
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
