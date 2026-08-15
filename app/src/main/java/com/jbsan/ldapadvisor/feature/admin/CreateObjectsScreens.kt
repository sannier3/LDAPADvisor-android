package com.jbsan.ldapadvisor.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.jbsan.ldapadvisor.ui.ComposeModifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.core.ad.GroupTypeDecoder
import com.jbsan.ldapadvisor.core.security.SecureWindow
import com.jbsan.ldapadvisor.ui.components.OuTreePickerDialog
import com.jbsan.ldapadvisor.ui.components.ParentContainerField
import android.app.Activity
import androidx.compose.ui.platform.LocalContext

@Composable
fun CreateUserScreen(viewModel: CreateObjectsViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.ouPicker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.refreshCaps()
        (context as? Activity)?.let { SecureWindow.enable(it) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { (context as? Activity)?.let { SecureWindow.disable(it) } }
    }
    Column(
        ComposeModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nav_create_user), style = MaterialTheme.typography.headlineSmall)
        CapabilityBanner(ui)
        ParentContainerField(
            label = stringResource(R.string.create_parent_dn),
            selectedDn = ui.userForm.parentDn,
            onBrowse = {
                viewModel.openParentPicker(
                    CreateObjectsViewModel.ParentTarget.USER,
                    ui.userForm.parentDn,
                )
            },
        )
        OutlinedTextField(
            ui.userForm.cn,
            { viewModel.updateUser { f -> f.copy(cn = it) } },
            label = { Text(stringResource(R.string.create_cn)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.sAMAccountName,
            { viewModel.updateUser { f -> f.copy(sAMAccountName = it) } },
            label = { Text(stringResource(R.string.create_sam)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.userPrincipalName,
            { viewModel.updateUser { f -> f.copy(userPrincipalName = it) } },
            label = { Text(stringResource(R.string.create_upn)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.displayName,
            { viewModel.updateUser { f -> f.copy(displayName = it) } },
            label = { Text(stringResource(R.string.create_display_name)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.initialPassword,
            { viewModel.updateUser { f -> f.copy(initialPassword = it) } },
            label = { Text(stringResource(R.string.create_initial_password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = ComposeModifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ui.userForm.enableAfterPassword,
                onCheckedChange = { viewModel.updateUser { f -> f.copy(enableAfterPassword = it) } },
            )
            Text(stringResource(R.string.create_enable_after_password))
        }
        Text(stringResource(R.string.create_user_disabled_note), style = MaterialTheme.typography.bodySmall)
        if (ui.busy) CircularProgressIndicator()
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.success?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(
            onClick = { viewModel.createUser() },
            enabled = ui.connected && ui.isAd && !ui.readOnly && !ui.busy,
            modifier = ComposeModifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_create))
        }
    }
    OuTreePickerDialog(
        state = picker,
        onDismiss = { viewModel.ouPicker.dismiss() },
        onToggle = { viewModel.ouPicker.toggle(it) },
        onSelect = { viewModel.ouPicker.select(it) },
        onConfirm = { viewModel.confirmParentPicker(it) },
    )
}

@Composable
fun CopyUserScreen(
    viewModel: CreateObjectsViewModel,
    sourceDn: String,
    onOpenCreated: (String) -> Unit = {},
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.ouPicker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(sourceDn) {
        viewModel.loadUserForCopy(sourceDn)
        (context as? Activity)?.let { SecureWindow.enable(it) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { (context as? Activity)?.let { SecureWindow.disable(it) } }
    }
    Column(
        ComposeModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nav_copy_user), style = MaterialTheme.typography.headlineSmall)
        CapabilityBanner(ui, requireAd = false)
        Text(
            stringResource(R.string.copy_user_source, sourceDn),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!ui.isAd) {
            Text(
                stringResource(R.string.copy_user_ldap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ui.loadingSource) {
            CircularProgressIndicator()
            return@Column
        }
        ParentContainerField(
            label = stringResource(R.string.copy_user_destination_ou),
            selectedDn = ui.userForm.parentDn,
            hint = stringResource(R.string.copy_user_destination_ou_hint),
            onBrowse = {
                viewModel.openParentPicker(
                    CreateObjectsViewModel.ParentTarget.USER,
                    ui.userForm.parentDn,
                )
            },
        )
        OutlinedTextField(
            ui.userForm.cn,
            { viewModel.updateUser { f -> f.copy(cn = it) } },
            label = { Text(stringResource(R.string.create_cn)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        if (ui.isAd) {
            OutlinedTextField(
                ui.userForm.sAMAccountName,
                { viewModel.updateUser { f -> f.copy(sAMAccountName = it) } },
                label = { Text(stringResource(R.string.create_sam)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
            OutlinedTextField(
                ui.userForm.userPrincipalName,
                { viewModel.updateUser { f -> f.copy(userPrincipalName = it) } },
                label = { Text(stringResource(R.string.create_upn)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                ui.userForm.uid,
                { viewModel.updateUser { f -> f.copy(uid = it) } },
                label = { Text(stringResource(R.string.create_uid)) },
                supportingText = { Text(stringResource(R.string.copy_user_uid_hint)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            ui.userForm.displayName,
            { viewModel.updateUser { f -> f.copy(displayName = it) } },
            label = { Text(stringResource(R.string.create_display_name)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.givenName,
            { viewModel.updateUser { f -> f.copy(givenName = it) } },
            label = { Text(stringResource(R.string.user_field_given_name)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.sn,
            { viewModel.updateUser { f -> f.copy(sn = it) } },
            label = { Text(stringResource(R.string.user_field_surname)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.initials,
            { viewModel.updateUser { f -> f.copy(initials = it) } },
            label = { Text(stringResource(R.string.user_field_initials)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.mail,
            { viewModel.updateUser { f -> f.copy(mail = it) } },
            label = { Text(stringResource(R.string.user_field_mail)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.telephoneNumber,
            { viewModel.updateUser { f -> f.copy(telephoneNumber = it) } },
            label = { Text(stringResource(R.string.user_field_phone)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.mobile,
            { viewModel.updateUser { f -> f.copy(mobile = it) } },
            label = { Text(stringResource(R.string.user_field_mobile)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.wwwHomePage,
            { viewModel.updateUser { f -> f.copy(wwwHomePage = it) } },
            label = { Text(stringResource(R.string.user_field_website)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.title,
            { viewModel.updateUser { f -> f.copy(title = it) } },
            label = { Text(stringResource(R.string.user_field_title)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.department,
            { viewModel.updateUser { f -> f.copy(department = it) } },
            label = { Text(stringResource(R.string.user_field_department)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.company,
            { viewModel.updateUser { f -> f.copy(company = it) } },
            label = { Text(stringResource(R.string.user_field_company)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.streetAddress,
            { viewModel.updateUser { f -> f.copy(streetAddress = it) } },
            label = { Text(stringResource(R.string.user_field_street)) },
            modifier = ComposeModifier.fillMaxWidth(),
            minLines = 2,
        )
        OutlinedTextField(
            ui.userForm.city,
            { viewModel.updateUser { f -> f.copy(city = it) } },
            label = { Text(stringResource(R.string.user_field_city)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.state,
            { viewModel.updateUser { f -> f.copy(state = it) } },
            label = { Text(stringResource(R.string.user_field_state)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.postalCode,
            { viewModel.updateUser { f -> f.copy(postalCode = it) } },
            label = { Text(stringResource(R.string.user_field_postal)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.country,
            { viewModel.updateUser { f -> f.copy(country = it) } },
            label = { Text(stringResource(R.string.user_field_country)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.description,
            { viewModel.updateUser { f -> f.copy(description = it) } },
            label = { Text(stringResource(R.string.create_description)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.userForm.initialPassword,
            { viewModel.updateUser { f -> f.copy(initialPassword = it) } },
            label = {
                Text(
                    stringResource(
                        if (ui.isAd) R.string.create_initial_password
                        else R.string.copy_user_ldap_password_optional,
                    ),
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = ComposeModifier.fillMaxWidth(),
        )
        if (ui.isAd) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ui.userForm.enableAfterPassword,
                    onCheckedChange = { viewModel.updateUser { f -> f.copy(enableAfterPassword = it) } },
                )
                Text(stringResource(R.string.create_enable_after_password))
            }
        }
        if (ui.userForm.memberOf.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ui.userForm.copyGroups,
                    onCheckedChange = { viewModel.updateUser { f -> f.copy(copyGroups = it) } },
                )
                Text(stringResource(R.string.copy_user_copy_groups, ui.userForm.memberOf.size))
            }
            if (ui.userForm.copyGroups) {
                ui.userForm.memberOf.take(12).forEach { g ->
                    Text(g, style = MaterialTheme.typography.bodySmall)
                }
                if (ui.userForm.memberOf.size > 12) {
                    Text("… +${ui.userForm.memberOf.size - 12}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (ui.isAd && ui.userForm.primaryGroupDn.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ui.userForm.copyPrimaryGroup,
                    onCheckedChange = { viewModel.updateUser { f -> f.copy(copyPrimaryGroup = it) } },
                )
                Text(
                    stringResource(
                        R.string.copy_user_copy_primary,
                        ui.userForm.primaryGroupLabel.ifBlank { ui.userForm.primaryGroupDn.ifBlank { "—" } },
                    ),
                )
            }
        }
        if (ui.busy) CircularProgressIndicator()
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.success?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(
            onClick = { viewModel.copyUser() },
            enabled = ui.connected && !ui.readOnly && !ui.busy && !ui.loadingSource,
            modifier = ComposeModifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_copy_user))
        }
        ui.createdDn?.let { created ->
            OutlinedButton(
                onClick = { onOpenCreated(created) },
                modifier = ComposeModifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.copy_user_open_created))
            }
        }
    }
    OuTreePickerDialog(
        state = picker,
        onDismiss = { viewModel.ouPicker.dismiss() },
        onToggle = { viewModel.ouPicker.toggle(it) },
        onSelect = { viewModel.ouPicker.select(it) },
        onConfirm = { viewModel.confirmParentPicker(it) },
    )
}

@Composable
fun CreateGroupScreen(viewModel: CreateObjectsViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.ouPicker.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshCaps() }
    Column(
        ComposeModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nav_create_group), style = MaterialTheme.typography.headlineSmall)
        CapabilityBanner(ui, requireAd = true)
        ParentContainerField(
            label = stringResource(R.string.create_parent_dn),
            selectedDn = ui.groupForm.parentDn,
            onBrowse = {
                viewModel.openParentPicker(
                    CreateObjectsViewModel.ParentTarget.GROUP,
                    ui.groupForm.parentDn,
                )
            },
        )
        OutlinedTextField(
            ui.groupForm.cn,
            { viewModel.updateGroup { f -> f.copy(cn = it) } },
            label = { Text(stringResource(R.string.create_cn)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.groupForm.sAMAccountName,
            { viewModel.updateGroup { f -> f.copy(sAMAccountName = it) } },
            label = { Text(stringResource(R.string.create_sam)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.groupForm.description,
            { viewModel.updateGroup { f -> f.copy(description = it) } },
            label = { Text(stringResource(R.string.create_description)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.create_group_scope))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                GroupTypeDecoder.Scope.GLOBAL to stringResource(R.string.create_scope_global),
                GroupTypeDecoder.Scope.DOMAIN_LOCAL to stringResource(R.string.create_scope_domain_local),
                GroupTypeDecoder.Scope.UNIVERSAL to stringResource(R.string.create_scope_universal),
            ).forEach { (scope, label) ->
                FilterChip(
                    selected = ui.groupForm.scope == scope,
                    onClick = { viewModel.updateGroup { f -> f.copy(scope = scope) } },
                    label = { Text(label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ui.groupForm.security,
                onClick = { viewModel.updateGroup { f -> f.copy(security = true) } },
                label = { Text(stringResource(R.string.create_group_security)) },
            )
            FilterChip(
                selected = !ui.groupForm.security,
                onClick = { viewModel.updateGroup { f -> f.copy(security = false) } },
                label = { Text(stringResource(R.string.create_group_distribution)) },
            )
        }
        if (ui.busy) CircularProgressIndicator()
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.success?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(
            onClick = { viewModel.createGroup() },
            enabled = ui.connected && ui.isAd && !ui.readOnly && !ui.busy,
            modifier = ComposeModifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_create))
        }
    }
    OuTreePickerDialog(
        state = picker,
        onDismiss = { viewModel.ouPicker.dismiss() },
        onToggle = { viewModel.ouPicker.toggle(it) },
        onSelect = { viewModel.ouPicker.select(it) },
        onConfirm = { viewModel.confirmParentPicker(it) },
    )
}

@Composable
fun CreateOuScreen(viewModel: CreateObjectsViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.ouPicker.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshCaps() }
    Column(
        ComposeModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nav_create_ou), style = MaterialTheme.typography.headlineSmall)
        CapabilityBanner(ui, requireAd = false)
        ParentContainerField(
            label = stringResource(R.string.create_parent_dn),
            selectedDn = ui.ouForm.parentDn,
            onBrowse = {
                viewModel.openParentPicker(
                    CreateObjectsViewModel.ParentTarget.OU,
                    ui.ouForm.parentDn,
                )
            },
        )
        OutlinedTextField(
            ui.ouForm.ouName,
            { viewModel.updateOu { f -> f.copy(ouName = it) } },
            label = { Text(stringResource(R.string.create_ou_name)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.ouForm.description,
            { viewModel.updateOu { f -> f.copy(description = it) } },
            label = { Text(stringResource(R.string.create_description)) },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        if (ui.busy) CircularProgressIndicator()
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.success?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(
            onClick = { viewModel.createOu() },
            enabled = ui.connected && !ui.readOnly && !ui.busy,
            modifier = ComposeModifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_create))
        }
    }
    OuTreePickerDialog(
        state = picker,
        onDismiss = { viewModel.ouPicker.dismiss() },
        onToggle = { viewModel.ouPicker.toggle(it) },
        onSelect = { viewModel.ouPicker.select(it) },
        onConfirm = { viewModel.confirmParentPicker(it) },
    )
}

@Composable
private fun CapabilityBanner(ui: CreateObjectsUiState, requireAd: Boolean = true) {
    when {
        !ui.connected -> Text(stringResource(R.string.create_need_connection), color = MaterialTheme.colorScheme.error)
        ui.readOnly -> Text(stringResource(R.string.create_need_writable), color = MaterialTheme.colorScheme.error)
        requireAd && !ui.isAd -> Text(stringResource(R.string.ad_only_feature), color = MaterialTheme.colorScheme.error)
        !ui.allowsPasswordChannel -> Text(stringResource(R.string.create_tls_hint), style = MaterialTheme.typography.bodySmall)
    }
}
