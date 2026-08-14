package com.jbsan.ldapadvisor.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.util.FingerprintUtils
import com.jbsan.ldapadvisor.data.diagnostics.DiagnosticEngine
import com.jbsan.ldapadvisor.data.diagnostics.DiagnosticProgress
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.data.repository.HistoryRepository
import com.jbsan.ldapadvisor.data.repository.ProfileRepository
import com.jbsan.ldapadvisor.data.repository.SettingsRepository
import com.jbsan.ldapadvisor.domain.model.ConnectionStatus
import com.jbsan.ldapadvisor.domain.model.DiagnosticRun
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import com.jbsan.ldapadvisor.domain.model.TrustMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val running: Boolean = false,
    val completed: Int = 0,
    val currentTitle: String = "",
    val results: List<DiagnosticTestResult> = emptyList(),
    val run: DiagnosticRun? = null,
    val error: String? = null,
    val message: String? = null,
    val tlsFingerprint: String? = null,
)

class DiagnosticsViewModel(
    private val diagnosticEngine: DiagnosticEngine,
    private val sessionManager: SessionManager,
    private val profileRepository: ProfileRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { loadLatest() }
    }

    fun loadLatest() = viewModelScope.launch {
        if (_ui.value.running) return@launch
        val latest = historyRepository.getLatestDiagnosticRun() ?: return@launch
        _ui.value = _ui.value.copy(
            run = latest,
            results = latest.tests,
            tlsFingerprint = extractTlsFingerprint(latest.tests),
            error = null,
        )
    }

    fun runFull() {
        job?.cancel()
        job = viewModelScope.launch {
            val status = sessionManager.status.value
            val profileId = (status as? ConnectionStatus.Connected)?.profileId
                ?: profileRepository.getAll().firstOrNull()?.id
            if (profileId == null) {
                _ui.value = _ui.value.copy(error = "no_profile")
                return@launch
            }
            val profile = profileRepository.getById(profileId) ?: return@launch
            val settings = settingsRepository.settings.first()
            _ui.value = DiagnosticsUiState(running = true, results = emptyList())
            diagnosticEngine.runFullDiagnostic(
                profile = profile,
                concurrency = settings.diagnosticConcurrency,
                tcpTimeoutMs = settings.diagnosticTcpTimeoutMs,
            ).collect { progress ->
                when (progress) {
                    is DiagnosticProgress.Started -> Unit
                    is DiagnosticProgress.TestCompleted -> {
                        _ui.value = _ui.value.copy(
                            completed = progress.completed,
                            currentTitle = progress.result.title,
                            results = _ui.value.results + progress.result,
                        )
                    }
                    is DiagnosticProgress.Finished -> {
                        historyRepository.saveDiagnosticRun(progress.run)
                        val fp = extractTlsFingerprint(progress.run.tests)
                        _ui.value = _ui.value.copy(
                            running = false,
                            run = progress.run,
                            results = progress.run.tests,
                            tlsFingerprint = fp,
                            message = "run_complete",
                        )
                    }
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _ui.value = _ui.value.copy(running = false)
    }

    fun applyPinToActiveProfile() = viewModelScope.launch {
        val fingerprint = _ui.value.tlsFingerprint
            ?: extractTlsFingerprint(_ui.value.results)
            ?: run {
                _ui.value = _ui.value.copy(error = "No TLS fingerprint in results")
                return@launch
            }
        val normalized = FingerprintUtils.normalizeSha256Fingerprint(fingerprint)
            ?: run {
                _ui.value = _ui.value.copy(error = "Invalid fingerprint")
                return@launch
            }
        val profileId = (sessionManager.status.value as? ConnectionStatus.Connected)?.profileId
            ?: profileRepository.getAll().firstOrNull()?.id
        if (profileId == null) {
            _ui.value = _ui.value.copy(error = "no_profile")
            return@launch
        }
        val profile = profileRepository.getById(profileId) ?: return@launch
        profileRepository.save(
            profile.copy(
                trustMode = TrustMode.PINNED,
                pinnedFingerprint = normalized,
            ),
        ).fold(
            onSuccess = {
                _ui.value = _ui.value.copy(
                    message = "pin_applied",
                    tlsFingerprint = normalized,
                    error = null,
                )
            },
            onFailure = { _ui.value = _ui.value.copy(error = it.message) },
        )
    }

    companion object {
        private val FINGERPRINT_REGEX =
            Regex("fingerprint=([0-9A-Fa-f:]+)", RegexOption.IGNORE_CASE)

        fun extractTlsFingerprint(tests: List<DiagnosticTestResult>): String? {
            val tls = tests.firstOrNull { it.id == "tls-handshake" } ?: return null
            FINGERPRINT_REGEX.find(tls.technicalDetails.orEmpty())?.groupValues?.getOrNull(1)?.let { return it }
            val evidence = tls.evidence.firstOrNull() ?: return null
            return Regex("\\(([0-9A-Fa-f:]+)\\)").find(evidence)?.groupValues?.getOrNull(1)
        }
    }
}
