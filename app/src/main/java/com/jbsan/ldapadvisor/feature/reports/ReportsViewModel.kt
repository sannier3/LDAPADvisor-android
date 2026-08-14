package com.jbsan.ldapadvisor.feature.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.data.report.ReportGenerator
import com.jbsan.ldapadvisor.data.repository.HistoryRepository
import com.jbsan.ldapadvisor.data.repository.ProfileRepository
import com.jbsan.ldapadvisor.data.repository.SettingsRepository
import com.jbsan.ldapadvisor.domain.model.DiagnosticRun
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class ReportsUiState(
    val run: DiagnosticRun? = null,
    val sanitizeDefault: Boolean = true,
    val message: String? = null,
    val error: String? = null,
)

class ReportsViewModel(
    private val reportGenerator: ReportGenerator,
    private val historyRepository: HistoryRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _ui.asStateFlow()

    fun load() = viewModelScope.launch {
        val settings = settingsRepository.settings.first()
        val entity = historyRepository.observeDiagnosticRuns().first().firstOrNull()
        val run = entity?.let { historyRepository.getDiagnosticRun(it.id) }
        _ui.value = ReportsUiState(run = run, sanitizeDefault = settings.reportSanitizationDefault)
    }

    fun share(context: Context, format: String, sanitized: Boolean) = viewModelScope.launch {
        val run = _ui.value.run
        if (run == null) {
            _ui.value = _ui.value.copy(error = "no_run")
            return@launch
        }
        val profile = run.profileId?.let { profileRepository.getById(it) }
        val content = when (format) {
            "html" -> reportGenerator.generateHtml(run, profile?.name, profile?.domain, profile?.host, sanitized)
            "json" -> reportGenerator.generateJson(run, profile?.name, profile?.domain, profile?.host, sanitized)
            else -> reportGenerator.generateTxt(run, profile?.name, profile?.domain, profile?.host, sanitized)
        }
        if (content.contains("password=", ignoreCase = true) && content.contains("password=[REDACTED]", ignoreCase = false).not()) {
            // Hard guarantee: never ship obvious password assignments.
            val scrubbed = Regex("(?i)password\\s*[:=]\\s*\\S+").replace(content, "password=[REDACTED]")
            writeAndShare(context, scrubbed, format)
        } else {
            writeAndShare(context, content, format)
        }
        _ui.value = _ui.value.copy(message = "generated")
    }

    private fun writeAndShare(context: Context, content: String, format: String) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "ldapadvisor-report.${format}")
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when (format) {
                "html" -> "text/html"
                "json" -> "application/json"
                else -> "text/plain"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
