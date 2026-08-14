package com.jbsan.ldapadvisor.feature.search

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.AdSearchPresets
import com.jbsan.ldapadvisor.core.logging.LogSanitizer
import com.jbsan.ldapadvisor.data.database.entity.SearchHistoryEntity
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.data.repository.FavoritesRepository
import com.jbsan.ldapadvisor.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SearchUiState(
    val baseDn: String = "",
    val filter: String = "(objectClass=*)",
    val scope: SearchScopeMode = SearchScopeMode.SUB,
    val attributes: String = "*",
    val pageSize: String = "200",
    val sizeLimit: String = "0",
    val timeLimit: String = "0",
    val results: List<LdapEntryData> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val isAd: Boolean = false,
)

class SearchViewModel(
    private val sessionManager: SessionManager,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _ui.asStateFlow()

    val history: StateFlow<List<SearchHistoryEntity>> = favoritesRepository.observeSearchHistory(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun prepare() {
        val session = sessionManager.currentSession()
        val base = session?.capabilities?.defaultNamingContext.orEmpty()
        _ui.value = _ui.value.copy(
            baseDn = _ui.value.baseDn.ifBlank { base },
            isAd = session?.capabilities?.isActiveDirectory == true,
        )
    }

    fun update(transform: (SearchUiState) -> SearchUiState) {
        _ui.value = transform(_ui.value)
    }

    fun applyPreset(filter: String) {
        _ui.value = _ui.value.copy(filter = filter)
    }

    fun applyHistory(item: SearchHistoryEntity) {
        _ui.value = _ui.value.copy(filter = item.filter, baseDn = item.baseDn)
    }

    fun search() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null) {
            _ui.value = _ui.value.copy(error = "Not connected")
            return@launch
        }
        _ui.value = _ui.value.copy(loading = true, error = null)
        val f = _ui.value
        val attrs = f.attributes.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
        val result = session.client.search(
            LdapSearchRequest(
                baseDn = f.baseDn,
                filter = f.filter,
                scope = f.scope,
                attributes = attrs.ifEmpty { arrayOf("*") },
                sizeLimit = f.sizeLimit.toIntOrNull() ?: 0,
                timeLimitSeconds = f.timeLimit.toIntOrNull() ?: 0,
                pageSize = f.pageSize.toIntOrNull()?.takeIf { it > 0 },
            ),
        )
        result.fold(
            onSuccess = {
                _ui.value = _ui.value.copy(results = it, loading = false)
                if (settingsRepository.settings.first().saveSearchHistory) {
                    favoritesRepository.saveSearch(f.filter, f.baseDn)
                }
            },
            onFailure = { _ui.value = _ui.value.copy(loading = false, error = it.message) },
        )
    }

    fun exportResults(context: Context, sanitize: Boolean = true) {
        val results = _ui.value.results
        if (results.isEmpty()) {
            _ui.value = _ui.value.copy(error = "No results to export")
            return
        }
        val body = buildString {
            appendLine("LDAPADvisor search export")
            appendLine("baseDn=${_ui.value.baseDn}")
            appendLine("filter=${_ui.value.filter}")
            appendLine("scope=${_ui.value.scope}")
            appendLine("count=${results.size}")
            appendLine("---")
            results.forEach { entry ->
                appendLine("dn: ${entry.dn}")
                entry.attributes.forEach { (name, values) ->
                    if (isSensitiveAttr(name)) return@forEach
                    values.forEach { value ->
                        val line = "$name: $value"
                        appendLine(if (sanitize) LogSanitizer.sanitize(line) else line)
                    }
                }
                appendLine()
            }
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "ldapadvisor-search-$stamp.txt")
        file.writeText(body)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
        _ui.value = _ui.value.copy(message = "exported")
    }

    fun presets(): List<Pair<String, String>> = listOf(
        "all_users" to AdSearchPresets.ALL_USERS,
        "all_groups" to AdSearchPresets.ALL_GROUPS,
        "all_computers" to AdSearchPresets.ALL_COMPUTERS,
        "disabled" to AdSearchPresets.DISABLED_USERS,
        "spn" to AdSearchPresets.USERS_WITH_SPN,
        "pwd_never" to AdSearchPresets.PWD_NEVER_EXPIRES,
        "dcs" to AdSearchPresets.DOMAIN_CONTROLLERS,
        "win_computers" to AdSearchPresets.WINDOWS_COMPUTERS,
        "ous" to AdSearchPresets.OUS,
    )

    private fun isSensitiveAttr(name: String): Boolean {
        val n = name.lowercase(Locale.US)
        return n == "userpassword" ||
            n == "unicodepwd" ||
            n.contains("password") ||
            n == "krbprincipalkey" ||
            n == "ntpwdhash" ||
            n == "lmpwdhash"
    }
}
