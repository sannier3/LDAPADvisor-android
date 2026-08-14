package com.jbsan.ldapadvisor.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.jbsan.ldapadvisor.ui.ComposeModifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.ui.components.StatusChip

@Composable
fun UserDiagnosticScreen(viewModel: UserDiagnosticViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        ComposeModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nav_user_diagnostic), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            ui.query,
            { viewModel.setQuery(it) },
            label = { Text(stringResource(R.string.user_diag_query)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.run() }, enabled = !ui.loading) {
            Text(stringResource(R.string.action_run))
        }
        if (ui.loading) CircularProgressIndicator()
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.entry?.let { entry ->
            Text(entry.dn, style = MaterialTheme.typography.titleSmall)
            Text(ui.enabledLabel)
            Text(ui.lockedLabel)
            Text(ui.passwordStatusLabel)
            Text(ui.passwordExpiryLabel)
            if (ui.uacFlags.isNotBlank()) Text("UAC: ${ui.uacFlags}")
            if (ui.computedFlags.isNotBlank()) Text("Computed: ${ui.computedFlags}")
            ui.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun ComputerDiagnosticScreen(
    viewModel: ComputerDiagnosticViewModel,
    initialQuery: String = "",
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && ui.query != initialQuery) {
            viewModel.runForHostname(initialQuery)
        }
    }
    Column(
        ComposeModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nav_computer_diagnostic), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            ui.query,
            { viewModel.setQuery(it) },
            label = { Text(stringResource(R.string.computer_diag_query)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.run() }, enabled = !ui.loading) {
            Text(stringResource(R.string.action_run))
        }
        if (ui.loading) CircularProgressIndicator()
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (ui.entry != null) {
            Text(ui.hostname, style = MaterialTheme.typography.titleSmall)
            Text(ui.osLabel)
            Text(ui.uacLabel)
            Text(stringResource(R.string.computer_diag_spn), style = MaterialTheme.typography.titleSmall)
            ui.spns.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.computer_diag_dns), style = MaterialTheme.typography.titleSmall)
            Text("A: ${ui.dnsA.joinToString().ifBlank { "—" }}")
            Text("AAAA: ${ui.dnsAAAA.joinToString().ifBlank { "—" }}")
            Text("PTR: ${ui.dnsPtr.joinToString().ifBlank { "—" }}")
            Text(stringResource(R.string.computer_diag_tcp), style = MaterialTheme.typography.titleSmall)
            ui.tcpResults.forEach { r ->
                ListItem(
                    headlineContent = { Text(r.title) },
                    supportingContent = { Text(r.summary) },
                    leadingContent = { StatusChip(r.status) },
                )
            }
            ui.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
