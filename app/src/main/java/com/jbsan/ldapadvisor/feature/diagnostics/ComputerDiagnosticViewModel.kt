package com.jbsan.ldapadvisor.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.AdSearchPresets
import com.jbsan.ldapadvisor.core.ad.FileTimeConverter
import com.jbsan.ldapadvisor.core.ad.UserAccountControl
import com.jbsan.ldapadvisor.core.util.LdapFilterEscaper
import com.jbsan.ldapadvisor.data.diagnostics.TcpDiagnosticService
import com.jbsan.ldapadvisor.data.diagnostics.TcpPortProbe
import com.jbsan.ldapadvisor.data.dns.DnsResolver
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.domain.model.DiagnosticTestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComputerDiagnosticUiState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val entry: LdapEntryData? = null,
    val hostname: String = "",
    val osLabel: String = "",
    val uacLabel: String = "",
    val spns: List<String> = emptyList(),
    val dnsA: List<String> = emptyList(),
    val dnsAAAA: List<String> = emptyList(),
    val dnsPtr: List<String> = emptyList(),
    val tcpResults: List<DiagnosticTestResult> = emptyList(),
    val notes: List<String> = emptyList(),
)

class ComputerDiagnosticViewModel(
    private val sessionManager: SessionManager,
    private val dnsResolver: DnsResolver,
    private val tcpDiagnosticService: TcpDiagnosticService,
) : ViewModel() {
    private val _ui = MutableStateFlow(ComputerDiagnosticUiState())
    val uiState: StateFlow<ComputerDiagnosticUiState> = _ui.asStateFlow()

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
    }

    fun runForHostname(hostname: String) {
        _ui.value = _ui.value.copy(query = hostname)
        run()
    }

    fun run() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null || !session.isConnected()) {
            _ui.value = _ui.value.copy(error = "Not connected")
            return@launch
        }
        if (!session.capabilities.isActiveDirectory) {
            _ui.value = _ui.value.copy(error = "Active Directory session required")
            return@launch
        }
        val q = _ui.value.query.trim()
        if (q.isBlank()) {
            _ui.value = _ui.value.copy(error = "Enter computer name or DNS hostname")
            return@launch
        }
        _ui.value = _ui.value.copy(loading = true, error = null)
        val base = session.capabilities.defaultNamingContext.orEmpty()
        val filter = when {
            q.contains('.') ->
                "(&(objectCategory=computer)(|(dNSHostName=${LdapFilterEscaper.escapeFilterValue(q)})(cn=${LdapFilterEscaper.escapeFilterValue(q.substringBefore('.'))})))"
            else -> AdSearchPresets.computerQuery(q)
        }
        val entry = session.client.search(
            LdapSearchRequest(
                base,
                filter,
                SearchScopeMode.SUB,
                arrayOf(
                    "name", "cn", "dNSHostName", "operatingSystem", "operatingSystemVersion",
                    "operatingSystemServicePack", "lastLogonTimestamp", "pwdLastSet",
                    "servicePrincipalName", "userAccountControl", "distinguishedName",
                ),
                pageSize = 20,
                sizeLimit = 20,
            ),
        ).getOrElse {
            _ui.value = _ui.value.copy(loading = false, error = it.message)
            return@launch
        }.firstOrNull()

        if (entry == null) {
            _ui.value = _ui.value.copy(loading = false, error = "No computer found")
            return@launch
        }

        val hostname = entry.firstString("dNSHostName")
            ?: entry.firstString("name")
            ?: q
        val uac = UserAccountControl.decode(entry.firstString("userAccountControl"))
        val a = dnsResolver.resolveA(hostname).getOrDefault(emptyList())
        val aaaa = dnsResolver.resolveAAAA(hostname).getOrDefault(emptyList())
        val ptr = mutableListOf<String>()
        (a + aaaa).forEach { ip ->
            ptr += dnsResolver.resolvePtr(ip).getOrDefault(emptyList())
        }
        val tcp = tcpDiagnosticService.probeHost(
            host = hostname,
            ports = listOf(
                TcpPortProbe(445, "SMB"),
                TcpPortProbe(135, "RPC Endpoint Mapper"),
                TcpPortProbe(5985, "WinRM HTTP"),
                TcpPortProbe(5986, "WinRM HTTPS"),
            ),
        )
        _ui.value = ComputerDiagnosticUiState(
            query = q,
            loading = false,
            entry = entry,
            hostname = hostname,
            osLabel = listOfNotNull(
                entry.firstString("operatingSystem"),
                entry.firstString("operatingSystemVersion"),
                entry.firstString("operatingSystemServicePack"),
            ).joinToString(" "),
            uacLabel = when {
                uac == null -> "userAccountControl unavailable"
                uac.enabled -> "Computer account enabled"
                else -> "Computer account disabled"
            },
            spns = entry.stringValues("servicePrincipalName"),
            dnsA = a,
            dnsAAAA = aaaa,
            dnsPtr = ptr.distinct(),
            tcpResults = tcp,
            notes = listOf(
                "TCP reachable means the port accepted a TCP connect — not that SMB/WinRM is healthy.",
                "lastLogonTimestamp: ${fileTime(entry.firstString("lastLogonTimestamp"))}",
                "pwdLastSet: ${fileTime(entry.firstString("pwdLastSet"))}",
            ),
        )
    }

    private fun fileTime(raw: String?): String = when (val v = FileTimeConverter.parse(raw)) {
        is FileTimeConverter.FileTimeValue.InstantValue -> v.instant.toString()
        FileTimeConverter.FileTimeValue.Never -> "Never"
        FileTimeConverter.FileTimeValue.Zero -> "0"
        else -> raw ?: "—"
    }
}
