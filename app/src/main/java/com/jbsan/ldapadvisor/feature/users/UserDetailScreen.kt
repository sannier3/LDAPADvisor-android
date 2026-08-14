package com.jbsan.ldapadvisor.feature.users

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.core.security.SecureWindow
import com.jbsan.ldapadvisor.ui.ComposeModifier
import com.jbsan.ldapadvisor.ui.components.EmptyState
import com.jbsan.ldapadvisor.ui.components.SessionBanner

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserDetailScreen(
    viewModel: UserDetailViewModel,
    dn: String,
    onCopyUser: ((String) -> Unit)? = null,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(dn) { viewModel.load(dn) }

    if (ui.loading && ui.form.dn.isBlank()) {
        Column(
            ComposeModifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading), ComposeModifier.padding(top = 12.dp))
        }
        return
    }

    if (ui.form.dn.isBlank() && ui.error != null) {
        EmptyState(
            title = stringResource(R.string.error_generic),
            body = ui.error ?: "",
            modifier = ComposeModifier.fillMaxSize(),
        )
        return
    }

    Column(
        ComposeModifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = ui.form.displayName.ifBlank { ui.form.samAccountName.ifBlank { ui.form.dn } },
            style = MaterialTheme.typography.headlineSmall,
            modifier = ComposeModifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.user_detail_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (ui.readOnly) {
            SessionBanner(
                text = stringResource(R.string.user_detail_readonly_hint),
                isWarning = true,
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }
        ui.error?.let {
            SessionBanner(
                text = when (it) {
                    "primary_remove_blocked" -> stringResource(R.string.user_primary_remove_blocked)
                    "read_only" -> stringResource(R.string.user_detail_readonly_hint)
                    else -> it
                },
                isWarning = true,
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }
        ui.message?.let { code ->
            SessionBanner(
                text = when (code) {
                    "pwd" -> stringResource(R.string.user_password_reset_success)
                    else -> stringResource(R.string.user_detail_saved)
                },
                isWarning = false,
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }

        // Identity summary
        SectionCard(title = stringResource(R.string.user_section_identity)) {
            ReadOnlyLine(stringResource(R.string.object_dn), ui.form.dn)
            ReadOnlyLine(stringResource(R.string.create_sam), ui.form.samAccountName)
            ReadOnlyLine(stringResource(R.string.create_upn), ui.form.userPrincipalName)
            ui.form.sid?.let { ReadOnlyLine("SID", it) }
            ui.form.guid?.let { ReadOnlyLine("GUID", it) }
            Text(
                if (ui.form.enabled) stringResource(R.string.user_enabled) else stringResource(R.string.user_disabled),
                style = MaterialTheme.typography.titleMedium,
                color = if (ui.form.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        // Editable identity / contact
        SectionCard(title = stringResource(R.string.user_section_contact)) {
            Field(stringResource(R.string.user_field_given_name), ui.form.givenName, !ui.readOnly) {
                viewModel.update { f -> f.copy(givenName = it) }
            }
            Field(stringResource(R.string.user_field_surname), ui.form.sn, !ui.readOnly) {
                viewModel.update { f -> f.copy(sn = it) }
            }
            Field(stringResource(R.string.create_display_name), ui.form.displayName, !ui.readOnly) {
                viewModel.update { f -> f.copy(displayName = it) }
            }
            Field(stringResource(R.string.user_field_mail), ui.form.mail, !ui.readOnly) {
                viewModel.update { f -> f.copy(mail = it) }
            }
            Field(stringResource(R.string.user_field_phone), ui.form.telephoneNumber, !ui.readOnly) {
                viewModel.update { f -> f.copy(telephoneNumber = it) }
            }
            Field(stringResource(R.string.user_field_mobile), ui.form.mobile, !ui.readOnly) {
                viewModel.update { f -> f.copy(mobile = it) }
            }
            Field(stringResource(R.string.user_field_website), ui.form.wwwHomePage, !ui.readOnly) {
                viewModel.update { f -> f.copy(wwwHomePage = it) }
            }
            Field(stringResource(R.string.user_field_title), ui.form.title, !ui.readOnly) {
                viewModel.update { f -> f.copy(title = it) }
            }
            Field(stringResource(R.string.user_field_department), ui.form.department, !ui.readOnly) {
                viewModel.update { f -> f.copy(department = it) }
            }
            Field(stringResource(R.string.user_field_company), ui.form.company, !ui.readOnly) {
                viewModel.update { f -> f.copy(company = it) }
            }
            Field(
                stringResource(R.string.user_field_street),
                ui.form.streetAddress,
                !ui.readOnly,
                multiline = true,
            ) {
                viewModel.update { f -> f.copy(streetAddress = it) }
            }
            Field(stringResource(R.string.user_field_city), ui.form.city, !ui.readOnly) {
                viewModel.update { f -> f.copy(city = it) }
            }
            Field(stringResource(R.string.user_field_state), ui.form.state, !ui.readOnly) {
                viewModel.update { f -> f.copy(state = it) }
            }
            Field(stringResource(R.string.user_field_postal), ui.form.postalCode, !ui.readOnly) {
                viewModel.update { f -> f.copy(postalCode = it) }
            }
            Field(stringResource(R.string.user_field_country), ui.form.country, !ui.readOnly) {
                viewModel.update { f -> f.copy(country = it) }
            }
            if (!ui.readOnly) {
                Button(
                    onClick = { viewModel.saveIdentityAndContact() },
                    enabled = !ui.saving,
                    modifier = ComposeModifier.fillMaxWidth().semantics {
                        contentDescription = context.getString(R.string.user_action_save_contact)
                    },
                ) {
                    Text(stringResource(R.string.user_action_save_contact))
                }
            }
        }

        // Password flags
        if (ui.isAd) {
            SectionCard(title = stringResource(R.string.user_section_password_policy)) {
                Text(
                    stringResource(R.string.user_section_password_policy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlagSwitch(
                    label = stringResource(R.string.user_flag_must_change),
                    checked = ui.form.mustChangePassword,
                    enabled = !ui.readOnly,
                    onChecked = { checked ->
                        viewModel.update { f ->
                            f.copy(
                                mustChangePassword = checked,
                                passwordNeverExpires = if (checked) false else f.passwordNeverExpires,
                            )
                        }
                    },
                )
                FlagSwitch(
                    label = stringResource(R.string.user_flag_never_expires),
                    checked = ui.form.passwordNeverExpires,
                    enabled = !ui.readOnly,
                    onChecked = { checked ->
                        viewModel.update { f ->
                            f.copy(
                                passwordNeverExpires = checked,
                                mustChangePassword = if (checked) false else f.mustChangePassword,
                            )
                        }
                    },
                )
                if (!ui.readOnly) {
                    Button(
                        onClick = { viewModel.savePasswordFlags() },
                        enabled = !ui.saving,
                        modifier = ComposeModifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.user_action_save_password_flags))
                    }
                }
            }
        }

        // Groups — effective membership (primary + memberOf)
        if (ui.isAd) {
            SectionCard(title = stringResource(R.string.user_section_groups)) {
                Text(
                    stringResource(R.string.user_membership_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(ComposeModifier.padding(vertical = 8.dp))
                if (ui.form.memberships.isEmpty()) {
                    Text(stringResource(R.string.empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ui.form.memberships.forEach { group ->
                        val isPrimaryGroup = group.isPrimary ||
                            (group.dn.isNotBlank() &&
                                group.dn.equals(ui.form.primaryGroupDn, ignoreCase = true))
                        Column(ComposeModifier.padding(vertical = 6.dp)) {
                            Text(group.name, style = MaterialTheme.typography.bodyLarge)
                            if (isPrimaryGroup) {
                                Text(
                                    stringResource(R.string.user_group_badge_primary),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (group.typeLabel.isNotBlank()) {
                                Text(
                                    stringResource(R.string.user_group_type, group.typeLabel),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (group.dn.isNotBlank()) {
                                Text(
                                    group.dn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isPrimaryGroup && !group.listedInMemberOf) {
                                Text(
                                    stringResource(R.string.user_primary_not_in_memberof),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!ui.readOnly && !isPrimaryGroup && group.dn.isNotBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { viewModel.setPrimaryGroup(group.dn) }) {
                                        Text(stringResource(R.string.user_action_set_primary_group))
                                    }
                                    TextButton(onClick = { viewModel.removeFromGroup(group.dn) }) {
                                        Text(stringResource(R.string.action_remove))
                                    }
                                }
                            }
                            if (!ui.readOnly && isPrimaryGroup) {
                                Text(
                                    stringResource(R.string.user_primary_remove_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (!ui.readOnly) {
                    OutlinedTextField(
                        value = ui.addGroupDn,
                        onValueChange = { viewModel.setAddGroupDn(it) },
                        label = { Text(stringResource(R.string.user_add_group_dn)) },
                        modifier = ComposeModifier.fillMaxWidth(),
                        supportingText = { Text(stringResource(R.string.user_add_group_hint)) },
                    )
                    OutlinedButton(
                        onClick = { viewModel.addToGroup() },
                        enabled = ui.addGroupDn.isNotBlank() && !ui.saving,
                        modifier = ComposeModifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.group_add_member))
                    }
                }
            }
        }

        // Account actions
        var confirmAction by remember { mutableStateOf<String?>(null) }
        if (ui.isAd && !ui.readOnly) {
            SectionCard(title = stringResource(R.string.user_section_actions)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { confirmAction = "unlock" }) {
                        Text(stringResource(R.string.action_unlock))
                    }
                    OutlinedButton(onClick = { confirmAction = "enable" }) {
                        Text(stringResource(R.string.action_enable))
                    }
                    OutlinedButton(onClick = { confirmAction = "disable" }) {
                        Text(stringResource(R.string.action_disable))
                    }
                    OutlinedButton(
                        onClick = { viewModel.showReset(true) },
                        enabled = ui.tlsActive,
                    ) {
                        Text(stringResource(R.string.action_reset_password))
                    }
                    if (onCopyUser != null) {
                        OutlinedButton(onClick = { onCopyUser(ui.form.dn) }) {
                            Text(stringResource(R.string.action_copy_user))
                        }
                    }
                }
                if (!ui.tlsActive) {
                    Text(
                        stringResource(R.string.user_reset_requires_tls),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        confirmAction?.let { action ->
            AlertDialog(
                onDismissRequest = { confirmAction = null },
                title = { Text(stringResource(R.string.admin_confirm_title)) },
                text = {
                    Text(
                        when (action) {
                            "unlock" -> stringResource(R.string.user_unlock_confirm, ui.form.dn)
                            "enable" -> stringResource(R.string.user_enable_confirm, ui.form.dn)
                            else -> stringResource(R.string.user_disable_confirm, ui.form.dn)
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        when (action) {
                            "unlock" -> viewModel.unlock()
                            "enable" -> viewModel.setDisabled(false)
                            "disable" -> viewModel.setDisabled(true)
                        }
                        confirmAction = null
                    }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmAction = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (ui.saving) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(ComposeModifier.padding(end = 8.dp))
                Text(stringResource(R.string.loading))
            }
        }
    }

    if (ui.showResetPassword) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        DisposableEffect(Unit) {
            (context as? Activity)?.let { SecureWindow.enable(it) }
            onDispose { (context as? Activity)?.let { SecureWindow.disable(it) } }
        }
        AlertDialog(
            onDismissRequest = {
                if (!ui.passwordResetBusy) viewModel.dismissResetPassword()
            },
            title = { Text(stringResource(R.string.user_reset_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.user_reset_password_body))
                    OutlinedTextField(
                        p1,
                        { p1 = it },
                        label = { Text(stringResource(R.string.user_new_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !ui.passwordResetBusy,
                        singleLine = true,
                        modifier = ComposeModifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        p2,
                        { p2 = it },
                        label = { Text(stringResource(R.string.user_confirm_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !ui.passwordResetBusy,
                        singleLine = true,
                        modifier = ComposeModifier.fillMaxWidth(),
                    )
                    if (p1.isNotEmpty() && p1 != p2) {
                        Text(
                            stringResource(R.string.user_password_mismatch),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    ui.passwordResetError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error)
                    }
                    if (ui.passwordResetBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.user_password_reset_in_progress))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (p1 == p2 && p1.isNotEmpty() && !ui.passwordResetBusy) {
                            viewModel.resetPassword(p1.toCharArray())
                        }
                    },
                    enabled = p1.isNotEmpty() && p1 == p2 && !ui.passwordResetBusy,
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissResetPassword() },
                    enabled = !ui.passwordResetBusy,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(ComposeModifier.fillMaxWidth()) {
        Column(
            ComposeModifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = ComposeModifier.semantics { heading() },
            )
            content()
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    editable: Boolean,
    multiline: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = editable,
        modifier = ComposeModifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
    )
}

@Composable
private fun ReadOnlyLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FlagSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        ComposeModifier.fillMaxWidth().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, ComposeModifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
        )
    }
}
