package com.jbsan.ldapadvisor.feature.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.jbsan.ldapadvisor.ui.ComposeModifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbsan.ldapadvisor.BuildConfig
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.core.security.SecureWindow
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.domain.model.ThemeMode
import com.jbsan.ldapadvisor.feature.advisor.AdvisorViewModel
import com.jbsan.ldapadvisor.feature.computers.ComputersViewModel
import com.jbsan.ldapadvisor.feature.connection.RootDseViewModel
import com.jbsan.ldapadvisor.feature.connection.SchemaViewModel
import com.jbsan.ldapadvisor.feature.diagnostics.DiagnosticsViewModel
import com.jbsan.ldapadvisor.feature.groups.GroupsViewModel
import com.jbsan.ldapadvisor.feature.reports.ReportsViewModel
import com.jbsan.ldapadvisor.feature.search.SearchViewModel
import com.jbsan.ldapadvisor.feature.settings.SettingsViewModel
import com.jbsan.ldapadvisor.feature.users.UsersViewModel
import com.jbsan.ldapadvisor.ui.components.AppBrandImage
import com.jbsan.ldapadvisor.ui.components.EmptyState
import com.jbsan.ldapadvisor.ui.components.StatusChip

@Composable
fun SearchScreen(viewModel: SearchViewModel, onOpen: (String) -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.prepare() }
    Column(
        ComposeModifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = ui.baseDn,
            onValueChange = { v -> viewModel.update { it.copy(baseDn = v) } },
            label = { Text(stringResource(R.string.search_base_dn)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.filter,
            onValueChange = { v -> viewModel.update { it.copy(filter = v) } },
            label = { Text(stringResource(R.string.search_filter)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SearchScopeMode.BASE, SearchScopeMode.ONE, SearchScopeMode.SUB).forEach { scope ->
                FilterChip(
                    selected = ui.scope == scope,
                    onClick = { viewModel.update { it.copy(scope = scope) } },
                    label = { Text(scope.name) },
                )
            }
        }
        if (ui.isAd) {
            Text(stringResource(R.string.search_presets), style = MaterialTheme.typography.labelLarge)
            Column {
                viewModel.presets().forEach { (key, filter) ->
                    TextButton(onClick = { viewModel.applyPreset(filter) }) { Text(key) }
                }
            }
        }
        OutlinedTextField(
            value = ui.attributes,
            onValueChange = { v -> viewModel.update { it.copy(attributes = v) } },
            label = { Text(stringResource(R.string.search_attributes)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.pageSize,
            onValueChange = { v -> viewModel.update { it.copy(pageSize = v) } },
            label = { Text(stringResource(R.string.search_page_size)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.search() }) { Text(stringResource(R.string.action_search)) }
            OutlinedButton(
                onClick = { viewModel.exportResults(context, sanitize = true) },
                enabled = ui.results.isNotEmpty(),
            ) { Text(stringResource(R.string.action_export_results)) }
        }
        if (history.isNotEmpty()) {
            Text(stringResource(R.string.search_history_title), style = MaterialTheme.typography.labelLarge)
            history.take(5).forEach { item ->
                TextButton(onClick = { viewModel.applyHistory(item) }) { Text(item.filter.take(80)) }
            }
        }
        if (ui.loading) CircularProgressIndicator()
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(ComposeModifier.weight(1f)) {
            items(ui.results, key = { it.dn }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.dn.substringBefore(',')) },
                    supportingContent = { Text(entry.dn) },
                    modifier = ComposeModifier.clickable { onOpen(entry.dn) },
                )
            }
        }
    }
}

@Composable
fun UsersScreen(
    viewModel: UsersViewModel,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit = {},
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshCaps() }
    Column(
        ComposeModifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ui.isAd) {
            Button(onClick = onCreate) { Text(stringResource(R.string.nav_create_user)) }
        }
        OutlinedTextField(
            value = ui.query,
            onValueChange = { viewModel.setQuery(it) },
            label = {
                Text(
                    stringResource(
                        if (ui.isAd) R.string.users_query_hint else R.string.users_query_hint_ldap,
                    ),
                )
            },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.search() }) { Text(stringResource(R.string.action_search)) }
        if (ui.loading) CircularProgressIndicator()
        Text(
            stringResource(R.string.dashboard_action_user_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(ComposeModifier.weight(1f)) {
            items(ui.results, key = { it.dn }) { e ->
                ListItem(
                    headlineContent = {
                        Text(
                            e.firstString("displayName")
                                ?: e.firstString("sAMAccountName")
                                ?: e.firstString("uid")
                                ?: e.firstString("cn")
                                ?: e.dn,
                        )
                    },
                    supportingContent = {
                        Text(e.firstString("userPrincipalName") ?: e.firstString("mail") ?: e.dn)
                    },
                    trailingContent = {
                        Text(
                            stringResource(R.string.action_open),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = ComposeModifier.clickable { onOpen(e.dn) },
                )
            }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun GroupsScreen(viewModel: GroupsViewModel, onCreate: () -> Unit = {}) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshCaps() }
    Column(ComposeModifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ui.isAd) {
            Button(onClick = onCreate) { Text(stringResource(R.string.nav_create_group)) }
        }
        OutlinedTextField(ui.query, { viewModel.setQuery(it) }, label = { Text(stringResource(R.string.groups_title)) }, modifier = ComposeModifier.fillMaxWidth())
        Button(onClick = { viewModel.search() }) { Text(stringResource(R.string.action_search)) }
        if (ui.loading) CircularProgressIndicator()
        LazyColumn(ComposeModifier.weight(1f)) {
            items(ui.results, key = { it.dn }) { e ->
                ListItem(
                    headlineContent = { Text(e.firstString("cn") ?: e.dn) },
                    supportingContent = {
                        Text(
                            if (ui.isAd) viewModel.groupTypeLabel(e)
                            else (e.firstString("description") ?: e.dn),
                        )
                    },
                    modifier = ComposeModifier.clickable { viewModel.open(e.dn) },
                )
            }
        }
        ui.selected?.let { selectedGroup ->
            var pendingMember by remember { mutableStateOf<Pair<String, String>?>(null) }
            Text(selectedGroup.dn)
            Text(stringResource(R.string.group_members))
            ui.members.take(50).forEach { m ->
                ListItem(
                    headlineContent = { Text(m) },
                    trailingContent = {
                        if (!ui.readOnly && ui.isAd) {
                            TextButton(onClick = { pendingMember = "remove" to m }) { Text(stringResource(R.string.action_remove)) }
                        }
                    },
                )
            }
            if (!ui.readOnly && ui.isAd) {
                var memberDn by remember { mutableStateOf("") }
                OutlinedTextField(memberDn, { memberDn = it }, label = { Text(stringResource(R.string.group_add_member)) }, modifier = ComposeModifier.fillMaxWidth())
                Button(onClick = { if (memberDn.isNotBlank()) pendingMember = "add" to memberDn }) { Text(stringResource(R.string.action_add)) }
            }
            if (ui.isAd) {
                OutlinedButton(onClick = { viewModel.loadNested() }) { Text(stringResource(R.string.group_nested)) }
                ui.nested.forEach { n -> Text(n.dn, style = MaterialTheme.typography.bodySmall) }
            }
            pendingMember?.let { (op, member) ->
                AlertDialog(
                    onDismissRequest = { pendingMember = null },
                    title = { Text(stringResource(R.string.admin_confirm_title)) },
                    text = {
                        Text(
                            if (op == "add") stringResource(R.string.group_add_member_confirm, member, selectedGroup.dn)
                            else stringResource(R.string.group_remove_member_confirm, member, selectedGroup.dn),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (op == "add") viewModel.addMember(member) else viewModel.removeMember(member)
                            pendingMember = null
                        }) { Text(stringResource(R.string.action_confirm)) }
                    },
                    dismissButton = { TextButton(onClick = { pendingMember = null }) { Text(stringResource(R.string.action_cancel)) } },
                )
            }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun ComputersScreen(
    viewModel: ComputersViewModel,
    onOpenDetails: (String) -> Unit = {},
    onComputerDiagnostic: (String) -> Unit = {},
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshCaps() }
    Column(
        ComposeModifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!ui.isAd) Text(stringResource(R.string.ad_only_feature))
        OutlinedTextField(ui.query, { viewModel.setQuery(it) }, label = { Text(stringResource(R.string.computers_title)) }, modifier = ComposeModifier.fillMaxWidth())
        Button(onClick = { viewModel.search() }) { Text(stringResource(R.string.action_search)) }
        LazyColumn(ComposeModifier.weight(1f)) {
            items(ui.results, key = { it.dn }) { e ->
                ListItem(
                    headlineContent = { Text(e.firstString("name") ?: e.dn) },
                    supportingContent = { Text(e.firstString("dNSHostName") ?: e.firstString("operatingSystem") ?: "") },
                    modifier = ComposeModifier.clickable { viewModel.open(e.dn); onOpenDetails(e.dn) },
                )
            }
        }
        ui.selected?.let {
            val host = it.firstString("dNSHostName") ?: it.firstString("name").orEmpty()
            Text(it.dn)
            Text("OS: ${it.firstString("operatingSystem")}")
            Text("SPN: ${it.stringValues("servicePrincipalName").joinToString()}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("hostname", host))
                }) { Text(stringResource(R.string.action_copy_hostname)) }
                OutlinedButton(onClick = { onComputerDiagnostic(host) }) { Text(stringResource(R.string.nav_computer_diagnostic)) }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onAdvisor: () -> Unit,
    onReports: () -> Unit,
    onUserDiag: () -> Unit = {},
    onComputerDiag: () -> Unit = {},
    onExportLogs: () -> Unit = {},
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showExportWarn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.loadLatest() }
    // Single LazyColumn so actions + results share one scroll surface.
    // A Column of fixed chrome + nested LazyColumn(weight) often left 0 height for results on phones.
    LazyColumn(
        ComposeModifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.runFull() }, enabled = !ui.running) {
                    Text(stringResource(R.string.diagnostics_run))
                }
                if (ui.running) {
                    OutlinedButton(onClick = { viewModel.cancel() }) {
                        Text(stringResource(R.string.action_stop))
                    }
                }
            }
        }
        if (ui.running) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.diagnostics_progress, ui.completed))
                    Text(stringResource(R.string.diagnostics_current, ui.currentTitle))
                }
            }
        }
        ui.run?.summary?.let { summary ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    summary.score?.let { Text(stringResource(R.string.diagnostics_score, it)) }
                    Text(
                        stringResource(
                            R.string.diagnostics_summary_counts,
                            summary.successCount,
                            summary.warningCount,
                            summary.errorCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        ui.tlsFingerprint?.let { fp ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.diagnostics_tls_fingerprint, fp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("sha256", fp))
                        }) { Text(stringResource(R.string.action_copy_fingerprint)) }
                        OutlinedButton(onClick = { viewModel.applyPinToActiveProfile() }) {
                            Text(stringResource(R.string.action_apply_pin))
                        }
                    }
                }
            }
        }
        ui.message?.let { msg ->
            item {
                Text(
                    if (msg == "run_complete") stringResource(R.string.diagnostics_run_complete)
                    else msg,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ui.error?.let { err ->
            item {
                Text(
                    if (err == "no_profile") stringResource(R.string.diagnostics_no_profile)
                    else err,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAdvisor) { Text(stringResource(R.string.nav_advisor)) }
                OutlinedButton(onClick = onReports) { Text(stringResource(R.string.nav_reports)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUserDiag) { Text(stringResource(R.string.nav_user_diagnostic)) }
                OutlinedButton(onClick = onComputerDiag) { Text(stringResource(R.string.nav_computer_diagnostic)) }
                OutlinedButton(onClick = { showExportWarn = true }) {
                    Text(stringResource(R.string.action_export_logs))
                }
            }
        }
        item {
            Text(
                stringResource(R.string.diagnostics_results_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (ui.results.isEmpty() && !ui.running) {
            item {
                Text(
                    stringResource(R.string.diagnostics_results_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(ui.results, key = { it.id + it.startedAt }) { r ->
                ListItem(
                    headlineContent = { Text(r.title) },
                    supportingContent = {
                        Column {
                            Text(r.summary)
                            r.probableCause?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    leadingContent = { StatusChip(r.status) },
                )
            }
        }
    }
    if (showExportWarn) {
        AlertDialog(
            onDismissRequest = { showExportWarn = false },
            title = { Text(stringResource(R.string.export_logs_title)) },
            text = { Text(stringResource(R.string.export_logs_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showExportWarn = false
                    onExportLogs()
                }) { Text(stringResource(R.string.action_share)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarn = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
fun AdvisorScreen(viewModel: AdvisorViewModel) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadLatest() }
    if (run == null || run!!.findings.isEmpty()) {
        EmptyState(stringResource(R.string.advisor_title), stringResource(R.string.advisor_empty), ComposeModifier.fillMaxSize())
        return
    }
    LazyColumn(ComposeModifier.fillMaxSize().padding(16.dp)) {
        items(run!!.findings, key = { it.id }) { f ->
            ListItem(
                headlineContent = { Text("${f.severity} · ${f.title}") },
                supportingContent = { Text(f.description) },
            )
        }
    }
}

@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sanitized by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(ui.sanitizeDefault) { sanitized = ui.sanitizeDefault }
    val run = ui.run
    if (run == null) {
        EmptyState(
            stringResource(R.string.reports_title),
            stringResource(R.string.reports_no_run),
            ComposeModifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        ComposeModifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.reports_title), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            run.summary.score?.let { Text(stringResource(R.string.diagnostics_score, it)) }
            Text(
                stringResource(
                    R.string.diagnostics_summary_counts,
                    run.summary.successCount,
                    run.summary.warningCount,
                    run.summary.errorCount,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reports_sanitized))
                Switch(checked = sanitized, onCheckedChange = { sanitized = it })
            }
        }
        item {
            Button(
                onClick = { viewModel.share(context, "html", sanitized) },
                modifier = ComposeModifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.reports_format_html)) }
        }
        item {
            Button(
                onClick = { viewModel.share(context, "json", sanitized) },
                modifier = ComposeModifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.reports_format_json)) }
        }
        item {
            Button(
                onClick = { viewModel.share(context, "txt", sanitized) },
                modifier = ComposeModifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.reports_format_txt)) }
        }
        ui.message?.let { msg ->
            item {
                Text(
                    if (msg == "generated") stringResource(R.string.reports_generated) else msg,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ui.error?.let { err ->
            item {
                Text(
                    if (err == "no_run") stringResource(R.string.reports_no_run) else err,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.diagnostics_results_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(run.tests, key = { it.id + it.startedAt }) { r ->
            ListItem(
                headlineContent = { Text(r.title) },
                supportingContent = {
                    Column {
                        Text(r.summary)
                        r.probableCause?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                leadingContent = { StatusChip(r.status) },
            )
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit = {},
    onUsers: () -> Unit = {},
    onGroups: () -> Unit = {},
    onComputers: () -> Unit = {},
    onOus: () -> Unit = {},
    onSearch: () -> Unit = {},
    onRawLdap: () -> Unit = {},
    onRootDse: () -> Unit = {},
    onSchema: () -> Unit = {},
    onHistory: () -> Unit = {},
    onFavorites: () -> Unit = {},
    onUserDiag: () -> Unit = {},
    onComputerDiag: () -> Unit = {},
    onExportLogs: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showExportWarn by remember { mutableStateOf(false) }
    Column(
        ComposeModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = ComposeModifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.settings_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsSection(stringResource(R.string.settings_section_appearance)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                                },
                            )
                        },
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_section_safety)) {
            SettingsSwitchRow(stringResource(R.string.settings_read_only_default), settings.readOnlyByDefault, viewModel::setReadOnlyDefault)
            SettingsSwitchRow(stringResource(R.string.settings_report_sanitize), settings.reportSanitizationDefault, viewModel::setSanitize)
            SettingsSwitchRow(stringResource(R.string.settings_save_search_history), settings.saveSearchHistory, viewModel::setSaveSearchHistory)
        }

        SettingsSection(stringResource(R.string.settings_section_developer)) {
            SettingsSwitchRow(
                stringResource(R.string.settings_debug_logging),
                settings.debugLoggingEnabled,
                viewModel::setDebugLogging,
            )
            Text(
                stringResource(R.string.settings_debug_logging_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(stringResource(R.string.settings_section_timeouts)) {
            OutlinedTextField(
                value = settings.defaultConnectTimeoutMs.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setConnectTimeout) },
                label = { Text(stringResource(R.string.settings_connect_timeout)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.defaultReadTimeoutMs.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setReadTimeout) },
                label = { Text(stringResource(R.string.settings_read_timeout)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.diagnosticConcurrency.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setConcurrency) },
                label = { Text(stringResource(R.string.settings_diag_concurrency)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.historyRetentionDays.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setRetention) },
                label = { Text(stringResource(R.string.settings_history_retention)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }

        SettingsSection(stringResource(R.string.settings_section_admin)) {
            Text(
                stringResource(R.string.settings_section_admin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsNavButton(stringResource(R.string.nav_users), onUsers)
            SettingsNavButton(stringResource(R.string.nav_groups), onGroups)
            SettingsNavButton(stringResource(R.string.nav_computers), onComputers)
            SettingsNavButton(stringResource(R.string.nav_ous), onOus)
            SettingsNavButton(stringResource(R.string.nav_search), onSearch)
            SettingsNavButton(stringResource(R.string.nav_raw_ldap), onRawLdap)
            SettingsNavButton(stringResource(R.string.nav_rootdse), onRootDse)
            SettingsNavButton(stringResource(R.string.nav_schema), onSchema)
            SettingsNavButton(stringResource(R.string.nav_history), onHistory)
            SettingsNavButton(stringResource(R.string.nav_favorites), onFavorites)
            SettingsNavButton(stringResource(R.string.nav_user_diagnostic), onUserDiag)
            SettingsNavButton(stringResource(R.string.nav_computer_diagnostic), onComputerDiag)
            SettingsNavButton(stringResource(R.string.action_export_logs)) { showExportWarn = true }
        }

        if (showExportWarn) {
            AlertDialog(
                onDismissRequest = { showExportWarn = false },
                title = { Text(stringResource(R.string.export_logs_title)) },
                text = { Text(stringResource(R.string.export_logs_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        showExportWarn = false
                        onExportLogs()
                    }) { Text(stringResource(R.string.action_share)) }
                },
                dismissButton = { TextButton(onClick = { showExportWarn = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }

        SettingsSection(stringResource(R.string.settings_section_about)) {
            SettingsNavButton(stringResource(R.string.settings_about), onAbout)
            SettingsNavButton(stringResource(R.string.settings_privacy), onPrivacy)
            SettingsNavButton(stringResource(R.string.settings_licenses), onLicenses)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = ComposeModifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = ComposeModifier.fillMaxWidth().semantics {
            contentDescription = label
        },
    ) {
        Text(label, ComposeModifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsNavButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = ComposeModifier.fillMaxWidth().semantics { contentDescription = label },
    ) {
        Text(label)
    }
}

@Composable
fun AboutScreen(onLicenses: () -> Unit = {}) {
    Column(
        ComposeModifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBrandImage(
            drawableRes = R.drawable.logo_wordmark,
            modifier = ComposeModifier.fillMaxWidth(0.85f),
            height = 160.dp,
        )
        Text(
            stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        Text(stringResource(R.string.about_license))
        Text(stringResource(R.string.about_repo))
        OutlinedButton(onClick = onLicenses) { Text(stringResource(R.string.settings_licenses)) }
    }
}

@Composable
fun LicensesScreen() {
    val context = LocalContext.current
    val text = remember {
        runCatching {
            context.assets.open("third_party_notices.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("Third-party notices unavailable.")
    }
    Column(
        ComposeModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.licenses_title), style = MaterialTheme.typography.headlineSmall)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun RawLdapScreen(viewModel: com.jbsan.ldapadvisor.feature.raw.RawLdapViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.prepare() }
    Column(
        ComposeModifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.raw_ldap_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.raw_ldap_read_only_note), style = MaterialTheme.typography.bodySmall)
        if (!ui.connected) {
            Text(stringResource(R.string.banner_session_disconnected), color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            ui.baseDn,
            { v -> viewModel.update { it.copy(baseDn = v) } },
            label = { Text(stringResource(R.string.search_base_dn)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.filter,
            { v -> viewModel.update { it.copy(filter = v) } },
            label = { Text(stringResource(R.string.search_filter)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SearchScopeMode.BASE, SearchScopeMode.ONE, SearchScopeMode.SUB).forEach { scope ->
                FilterChip(
                    selected = ui.scope == scope,
                    onClick = { viewModel.update { it.copy(scope = scope) } },
                    label = { Text(scope.name) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.search() }) { Text(stringResource(R.string.action_search)) }
            OutlinedButton(onClick = { viewModel.readBase() }) { Text(stringResource(R.string.raw_ldap_read_base)) }
        }
        OutlinedTextField(
            ui.compareDn,
            { v -> viewModel.update { it.copy(compareDn = v) } },
            label = { Text(stringResource(R.string.raw_ldap_compare_dn)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.compareAttribute,
            { v -> viewModel.update { it.copy(compareAttribute = v) } },
            label = { Text(stringResource(R.string.raw_ldap_compare_attr)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.compareValue,
            { v -> viewModel.update { it.copy(compareValue = v) } },
            label = { Text(stringResource(R.string.raw_ldap_compare_value)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { viewModel.compare() }) { Text(stringResource(R.string.action_compare)) }
        ui.compareResult?.let { Text(stringResource(R.string.raw_ldap_compare_result, it)) }
        if (ui.loading) CircularProgressIndicator()
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.baseEntry?.let { entry ->
            Text(stringResource(R.string.raw_ldap_base_entry), style = MaterialTheme.typography.titleSmall)
            Text(entry.dn)
            entry.attributes.entries.take(40).forEach { (k, v) ->
                Text("$k: ${v.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyColumn(ComposeModifier.weight(1f)) {
            items(ui.results, key = { it.dn }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.dn.substringBefore(',')) },
                    supportingContent = { Text(entry.dn) },
                )
            }
        }
    }
}

@Composable
fun PrivacyScreen() {
    Column(ComposeModifier.padding(24.dp)) {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.privacy_body), ComposeModifier.padding(top = 12.dp))
    }
}

@Composable
fun RootDseScreen(viewModel: RootDseViewModel) {
    val attrs by viewModel.attrs.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    Column(ComposeModifier.fillMaxSize().padding(16.dp)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn {
            items(attrs.entries.toList(), key = { it.key }) { (k, v) ->
                ListItem(headlineContent = { Text(k) }, supportingContent = { Text(v.joinToString()) })
            }
        }
    }
}

@Composable
fun SchemaScreen(viewModel: SchemaViewModel) {
    val oc by viewModel.objectClasses.collectAsStateWithLifecycle()
    val at by viewModel.attributeTypes.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    Column(ComposeModifier.fillMaxSize().padding(16.dp)) {
        error?.let { Text(it) }
        Text(stringResource(R.string.schema_object_classes), style = MaterialTheme.typography.titleMedium)
        LazyColumn(ComposeModifier.weight(1f)) { items(oc) { Text(it) } }
        Text(stringResource(R.string.schema_attribute_types), style = MaterialTheme.typography.titleMedium)
        LazyColumn(ComposeModifier.weight(1f)) { items(at) { Text(it) } }
    }
}

@Composable
fun ConnectionScreen(
    viewModel: com.jbsan.ldapadvisor.feature.connection.ConnectionViewModel,
    profilesViewModel: com.jbsan.ldapadvisor.feature.profiles.ProfilesViewModel,
    onProfiles: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    val needPlaintext by viewModel.needPlaintext.collectAsStateWithLifecycle()
    val needInsecureTrust by viewModel.needInsecureTrust.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Column(
        ComposeModifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nav_connection), style = MaterialTheme.typography.headlineSmall)
        when (val s = status) {
            is com.jbsan.ldapadvisor.domain.model.ConnectionStatus.Connected -> {
                Text(stringResource(R.string.banner_session_connected, s.host, s.port, s.securityMode.name))
                if (!s.tlsActive && !s.kerberosBound) {
                    Text(stringResource(R.string.banner_plaintext), color = MaterialTheme.colorScheme.error)
                } else if (s.kerberosBound && !s.tlsActive) {
                    Text(
                        stringResource(R.string.banner_kerberos_bound),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (s.insecureTrust) {
                    Text(stringResource(R.string.banner_insecure_trust), color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { viewModel.disconnect() }) { Text(stringResource(R.string.action_disconnect)) }
            }
            else -> Text(stringResource(R.string.banner_session_disconnected))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (profiles.isEmpty()) {
            EmptyState(stringResource(R.string.profiles_empty), stringResource(R.string.profiles_empty_hint))
            OutlinedButton(onClick = onProfiles) { Text(stringResource(R.string.nav_profiles)) }
        } else {
            LazyColumn {
                items(profiles, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${p.host}:${p.port}") },
                        trailingContent = {
                            TextButton(onClick = { viewModel.connect(p.id) }) {
                                Text(stringResource(R.string.action_connect))
                            }
                        },
                    )
                }
            }
        }
        if (needPlaintext) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.profile_plaintext_title)) },
                text = { Text(stringResource(R.string.profile_plaintext_body)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmPlaintext() }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {}) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
        if (needInsecureTrust) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissInsecureTrust() },
                title = { Text(stringResource(R.string.profile_insecure_trust_title)) },
                text = { Text(stringResource(R.string.profile_insecure_trust_body)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmInsecureTrust() }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissInsecureTrust() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
fun OrganizationalUnitsScreen(
    viewModel: SearchViewModel,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit = {},
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.prepare()
        val ouFilter = if (viewModel.uiState.value.isAd) {
            com.jbsan.ldapadvisor.core.ad.AdSearchPresets.OUS
        } else {
            com.jbsan.ldapadvisor.core.ldap.LdapSearchPresets.ALL_OUS
        }
        viewModel.applyPreset(ouFilter)
        viewModel.search()
    }
    Column(
        ComposeModifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ui.isAd) {
            Button(onClick = onCreate) { Text(stringResource(R.string.nav_create_ou)) }
        }
        Button(onClick = {
            viewModel.applyPreset(
                if (ui.isAd) {
                    com.jbsan.ldapadvisor.core.ad.AdSearchPresets.OUS
                } else {
                    com.jbsan.ldapadvisor.core.ldap.LdapSearchPresets.ALL_OUS
                },
            )
            viewModel.search()
        }) { Text(stringResource(R.string.action_search)) }
        if (ui.loading) CircularProgressIndicator()
        LazyColumn(ComposeModifier.weight(1f)) {
            items(ui.results, key = { it.dn }) { e ->
                ListItem(
                    headlineContent = { Text(e.firstString("ou") ?: e.firstString("name") ?: e.dn) },
                    supportingContent = { Text(e.dn) },
                    modifier = ComposeModifier.clickable { onOpen(e.dn) },
                )
            }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun HistoryScreen(viewModel: AdvisorViewModel, onAdvisor: () -> Unit) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadLatest() }
    Column(
        ComposeModifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.headlineSmall)
        if (run == null) {
            Text(stringResource(R.string.reports_no_run))
        } else {
            Text(stringResource(R.string.diagnostics_score, run!!.summary.score ?: 0))
            Text(stringResource(R.string.diagnostics_progress, run!!.tests.size))
            OutlinedButton(onClick = onAdvisor) { Text(stringResource(R.string.nav_advisor)) }
        }
    }
}
