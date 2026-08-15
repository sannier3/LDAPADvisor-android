package com.jbsan.ldapadvisor.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.domain.model.ConnectionStatus
import com.jbsan.ldapadvisor.ui.ComposeModifier
import com.jbsan.ldapadvisor.ui.components.AppLogoMark
import com.jbsan.ldapadvisor.ui.components.EmptyState
import com.jbsan.ldapadvisor.ui.components.SessionBanner
import androidx.compose.ui.Alignment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onConnect: () -> Unit,
    onBrowse: () -> Unit,
    onSearchUser: () -> Unit,
    onSearchComputer: () -> Unit,
    onFullDiagnostic: () -> Unit,
    onLdapSearch: () -> Unit,
    onProfiles: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.profiles.isEmpty()) {
        Column(
            ComposeModifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogoMark(size = 96.dp)
            EmptyState(
                title = stringResource(R.string.dashboard_empty_title),
                body = stringResource(R.string.dashboard_empty_body),
            )
            val createProfileLabel = stringResource(R.string.dashboard_cta_create_profile)
            Button(
                onClick = onProfiles,
                modifier = ComposeModifier.fillMaxWidth().semantics {
                    contentDescription = createProfileLabel
                },
            ) {
                Text(createProfileLabel)
            }
        }
        return
    }

    Column(
        ComposeModifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!state.networkAvailable ||
            (state.connectionStatus as? ConnectionStatus.Connected)?.networkLost == true
        ) {
            SessionBanner(
                text = stringResource(R.string.banner_network_lost),
                isWarning = true,
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }
        val status = state.connectionStatus
        when (status) {
            is ConnectionStatus.Connected -> {
                if (!status.networkLost) {
                    SessionBanner(
                        text = stringResource(
                            R.string.banner_session_connected,
                            status.host,
                            status.port,
                            status.securityMode.name,
                        ),
                        isWarning = status.insecureTrust ||
                            (!status.tlsActive && !status.kerberosBound),
                        modifier = ComposeModifier.fillMaxWidth(),
                    )
                }
                if (status.insecureTrust) {
                    SessionBanner(
                        text = stringResource(R.string.banner_insecure_trust),
                        isWarning = true,
                        modifier = ComposeModifier.fillMaxWidth(),
                    )
                }
            }
            is ConnectionStatus.Connecting -> SessionBanner(
                text = stringResource(R.string.connecting),
                isWarning = false,
                modifier = ComposeModifier.fillMaxWidth(),
            )
            is ConnectionStatus.Error -> SessionBanner(
                text = status.error.message ?: stringResource(R.string.error_generic),
                isWarning = true,
                modifier = ComposeModifier.fillMaxWidth(),
            )
            ConnectionStatus.Disconnected -> SessionBanner(
                text = stringResource(R.string.banner_session_disconnected),
                isWarning = true,
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }

        Column(
            ComposeModifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppLogoMark(size = 56.dp)
            Text(
                stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = ComposeModifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.dashboard_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(ComposeModifier.fillMaxWidth()) {
                Column(
                    ComposeModifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.dashboard_section_session),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = ComposeModifier.semantics { heading() },
                    )
                    InfoLine(stringResource(R.string.dashboard_active_profile), state.activeProfile?.name ?: "—")
                    InfoLine(
                        stringResource(R.string.dashboard_connection_status),
                        when (status) {
                            is ConnectionStatus.Connected -> stringResource(R.string.connected)
                            is ConnectionStatus.Connecting -> stringResource(R.string.connecting)
                            is ConnectionStatus.Error -> stringResource(R.string.status_error)
                            ConnectionStatus.Disconnected -> stringResource(R.string.disconnected)
                        },
                    )
                    InfoLine(stringResource(R.string.dashboard_directory_type), 
                        if (state.isAd) stringResource(R.string.directory_type_ad)
                        else if (status is ConnectionStatus.Connected)
                            stringResource(R.string.directory_type_generic)
                        else state.activeProfile?.directoryType?.name ?: "—",
                    )
                    InfoLine(stringResource(R.string.dashboard_domain), state.activeProfile?.domain?.ifBlank { "—" } ?: "—")
                    if (status is ConnectionStatus.Connected) {
                        InfoLine(stringResource(R.string.dashboard_server), "${status.host}:${status.port}")
                        InfoLine(stringResource(R.string.dashboard_security), status.securityMode.name)
                        status.responseTimeMs?.let {
                            InfoLine(stringResource(R.string.dashboard_response_time), stringResource(R.string.dashboard_ms, it))
                        }
                    }
                    InfoLine(stringResource(R.string.dashboard_base_dn), state.baseDn ?: "—")
                    if (status is ConnectionStatus.Connected && state.isAd) {
                        InfoLine(stringResource(R.string.dashboard_domain_level), state.domainLevel ?: "—")
                        InfoLine(stringResource(R.string.dashboard_forest_level), state.forestLevel ?: "—")
                        InfoLine(stringResource(R.string.dashboard_dc_level), state.dcLevel ?: "—")
                        InfoLine(stringResource(R.string.dashboard_gc_ready), state.gcReady ?: "—")
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = ComposeModifier.padding(top = 8.dp),
                    ) {
                        Button(onClick = onConnect) {
                            Icon(Icons.Filled.Link, contentDescription = null)
                            Text(
                                stringResource(R.string.nav_connection),
                                ComposeModifier.padding(start = 8.dp),
                            )
                        }
                        OutlinedButton(onClick = onProfiles) {
                            Text(stringResource(R.string.nav_profiles))
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.dashboard_quick_actions),
                style = MaterialTheme.typography.titleMedium,
                modifier = ComposeModifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.dashboard_quick_actions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ActionRow(
                icon = { Icon(Icons.Filled.AccountTree, contentDescription = null) },
                title = stringResource(R.string.action_browse),
                subtitle = stringResource(R.string.dashboard_action_browse_desc),
                onClick = onBrowse,
            )
            ActionRow(
                icon = { Icon(Icons.Filled.PersonSearch, contentDescription = null) },
                title = stringResource(R.string.dashboard_search_user),
                subtitle = stringResource(R.string.dashboard_action_user_desc),
                onClick = onSearchUser,
            )
            if (state.isAd) {
                ActionRow(
                    icon = { Icon(Icons.Filled.Computer, contentDescription = null) },
                    title = stringResource(R.string.dashboard_search_computer),
                    subtitle = stringResource(R.string.dashboard_action_computer_desc),
                    onClick = onSearchComputer,
                )
            }
            ActionRow(
                icon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null) },
                title = stringResource(R.string.nav_search),
                subtitle = stringResource(R.string.dashboard_action_search_desc),
                onClick = onLdapSearch,
            )
            ActionRow(
                icon = { Icon(Icons.Filled.HealthAndSafety, contentDescription = null) },
                title = stringResource(R.string.action_full_diagnostic),
                subtitle = stringResource(R.string.dashboard_action_diag_desc),
                onClick = onFullDiagnostic,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = ComposeModifier.fillMaxWidth().semantics {
            contentDescription = "$title. $subtitle"
        },
    ) {
        ListItem(
            leadingContent = icon,
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.action_open),
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
