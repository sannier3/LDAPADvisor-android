package com.jbsan.ldapadvisor.feature.profiles

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.core.security.SecureWindow
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.ui.components.EmptyState
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onConnected: () -> Unit = {},
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteId by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(ui.message) {
        if (ui.message == "connected") {
            viewModel.consumeConnectedMessage()
            onConnected()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.nav_profile_create))
            }
        },
    ) { padding ->
        Column(
            modifier = ComposeModifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (ui.connecting) {
                Text(
                    stringResource(R.string.connecting),
                    modifier = ComposeModifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ui.error?.let { err ->
                Text(
                    err,
                    modifier = ComposeModifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (profiles.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.profiles_empty),
                    body = stringResource(R.string.profiles_empty_hint),
                    modifier = ComposeModifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = ComposeModifier.fillMaxSize()) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileRow(
                            profile = profile,
                            connecting = ui.connecting,
                            onEdit = { onEdit(profile.id) },
                            onConnect = { viewModel.connect(profile.id) },
                            onDuplicate = { viewModel.duplicate(profile.id) },
                            onDelete = { deleteId = profile.id },
                        )
                    }
                }
            }
        }
    }

    if (ui.requirePassword) {
        LaunchedEffect(Unit) {
            (context as? Activity)?.let { SecureWindow.enable(it) }
        }
        AlertDialog(
            onDismissRequest = {
                passwordInput = ""
                viewModel.dismissPasswordPrompt()
                (context as? Activity)?.let { SecureWindow.disable(it) }
            },
            title = { Text(stringResource(R.string.profile_connect_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_connect_password_body, ui.pendingBindIdentity))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.profile_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = ComposeModifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pwd = passwordInput
                        passwordInput = ""
                        (context as? Activity)?.let { SecureWindow.disable(it) }
                        viewModel.submitPassword(pwd)
                    },
                    enabled = passwordInput.isNotEmpty() && !ui.connecting,
                ) {
                    Text(stringResource(R.string.action_connect))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        passwordInput = ""
                        viewModel.dismissPasswordPrompt()
                        (context as? Activity)?.let { SecureWindow.disable(it) }
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (ui.requirePlaintextConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPlaintext,
            title = { Text(stringResource(R.string.profile_plaintext_title)) },
            text = { Text(stringResource(R.string.profile_plaintext_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPlaintextConnect) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPlaintext) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (ui.requireInsecureTrustConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissInsecureTrust,
            title = { Text(stringResource(R.string.profile_insecure_trust_title)) },
            text = { Text(stringResource(R.string.profile_insecure_trust_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmInsecureTrustConnect) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissInsecureTrust) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    deleteId?.let { id ->
        val name = profiles.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.profile_delete_confirm_title)) },
            text = { Text(stringResource(R.string.profile_delete_confirm_body, name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(id); deleteId = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: ConnectionProfile,
    connecting: Boolean,
    onEdit: () -> Unit,
    onConnect: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                profile.name,
                modifier = ComposeModifier.clickable(onClick = onEdit),
            )
        },
        supportingContent = {
            Text("${profile.host}:${profile.port} · ${profile.securityMode.name}")
        },
        trailingContent = {
            Row {
                TextButton(
                    onClick = onConnect,
                    enabled = !connecting,
                ) {
                    Text(stringResource(R.string.action_connect))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = onDuplicate) { Text("⧉") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    viewModel: ProfileEditViewModel,
    profileId: String?,
    onSaved: () -> Unit,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val customCas by viewModel.customCas.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(profileId) { viewModel.load(profileId) }
    LaunchedEffect(form.saved) { if (form.saved) onSaved() }

    // FLAG_SECURE while password field is focused/present with content intent — enable for whole edit screen when password editable
    LaunchedEffect(Unit) {
        (context as? Activity)?.let { SecureWindow.enable(it) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { (context as? Activity)?.let { SecureWindow.disable(it) } }
    }

    Column(
        modifier = ComposeModifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (profileId == null) stringResource(R.string.nav_profile_create) else stringResource(R.string.nav_profile_edit),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = form.name,
            onValueChange = { viewModel.update { f -> f.copy(name = it, nameError = null) } },
            label = { Text(stringResource(R.string.profile_name)) },
            isError = form.nameError != null,
            supportingText = form.nameError?.let { { Text(stringResource(R.string.profile_validation_name)) } },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        EnumDropdown(
            label = stringResource(R.string.profile_directory_type),
            options = listOf("AUTO", "ACTIVE_DIRECTORY", "GENERIC_LDAP"),
            selected = form.directoryType,
            onSelected = { viewModel.update { f -> f.copy(directoryType = it) } },
        )
        OutlinedTextField(form.domain, { viewModel.update { f -> f.copy(domain = it) } }, label = { Text(stringResource(R.string.profile_domain)) }, modifier = ComposeModifier.fillMaxWidth())
        OutlinedButton(onClick = { viewModel.discover() }) { Text(stringResource(R.string.action_discover)) }
        form.discovered.forEach { dc ->
            val tcp = form.dcTcpResults[dc.hostname]
            ListItem(
                headlineContent = { Text(dc.hostname) },
                supportingContent = {
                    Text(
                        buildString {
                            append("${dc.port} · p=${dc.priority}")
                            if (tcp != null) append(" · $tcp")
                            if (form.dcTcpTesting == dc.hostname) append(" · …")
                        },
                    )
                },
                trailingContent = {
                    TextButton(onClick = { viewModel.testDcTcp(dc) }) {
                        Text(stringResource(R.string.action_test_tcp))
                    }
                },
                modifier = ComposeModifier.clickable { viewModel.pickDc(dc) },
            )
        }
        OutlinedTextField(
            form.host,
            { viewModel.update { f -> f.copy(host = it, hostError = null) } },
            label = { Text(stringResource(R.string.profile_host)) },
            isError = form.hostError != null,
            supportingText = form.hostError?.let { { Text(stringResource(R.string.profile_validation_host)) } },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        EnumDropdown(
            label = stringResource(R.string.profile_security),
            options = listOf("LDAP", "LDAPS", "START_TLS"),
            selected = form.securityMode,
            onSelected = viewModel::onSecurityChanged,
        )
        OutlinedTextField(
            form.port,
            { viewModel.update { f -> f.copy(port = it, portError = null) } },
            label = { Text(stringResource(R.string.profile_port)) },
            isError = form.portError != null,
            supportingText = form.portError?.let { { Text(stringResource(R.string.profile_validation_port)) } },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        EnumDropdown(
            label = stringResource(R.string.profile_auth_method),
            options = listOf("SIMPLE", "KERBEROS"),
            selected = form.authMethod,
            onSelected = { viewModel.update { f -> f.copy(authMethod = it) } },
        )
        Text(
            stringResource(
                if (form.authMethod == "KERBEROS") R.string.profile_auth_kerberos_hint
                else R.string.profile_auth_simple_hint,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            form.bindIdentity,
            { viewModel.update { f -> f.copy(bindIdentity = it) } },
            label = {
                Text(
                    stringResource(
                        if (form.authMethod == "KERBEROS") R.string.profile_kerberos_principal
                        else R.string.profile_bind_identity,
                    ),
                )
            },
            supportingText = {
                if (form.authMethod == "KERBEROS") {
                    Text(stringResource(R.string.profile_kerberos_principal_hint))
                }
            },
            modifier = ComposeModifier.fillMaxWidth(),
        )
        if (form.authMethod == "KERBEROS") {
            OutlinedTextField(
                form.kerberosRealm,
                { viewModel.update { f -> f.copy(kerberosRealm = it) } },
                label = { Text(stringResource(R.string.profile_kerberos_realm)) },
                supportingText = { Text(stringResource(R.string.profile_kerberos_realm_hint)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
            OutlinedTextField(
                form.kerberosKdcHost,
                { viewModel.update { f -> f.copy(kerberosKdcHost = it) } },
                label = { Text(stringResource(R.string.profile_kerberos_kdc)) },
                supportingText = { Text(stringResource(R.string.profile_kerberos_kdc_hint)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
            OutlinedTextField(
                form.kerberosKdcPort,
                { viewModel.update { f -> f.copy(kerberosKdcPort = it) } },
                label = { Text(stringResource(R.string.profile_kerberos_kdc_port)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
            OutlinedTextField(
                form.kerberosServicePrincipal,
                { viewModel.update { f -> f.copy(kerberosServicePrincipal = it) } },
                label = { Text(stringResource(R.string.profile_kerberos_spn)) },
                supportingText = { Text(stringResource(R.string.profile_kerberos_spn_hint)) },
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            form.password,
            { viewModel.update { f -> f.copy(password = it) } },
            label = { Text(stringResource(R.string.profile_password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = ComposeModifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.profile_remember_password), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(!form.rememberPassword, onClick = { viewModel.update { it.copy(rememberPassword = false) } })
            Text(stringResource(R.string.profile_remember_none))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(form.rememberPassword, onClick = { viewModel.update { it.copy(rememberPassword = true) } })
            Text(stringResource(R.string.profile_remember_encrypted))
        }
        if (form.id != null) {
            TextButton(onClick = { viewModel.forgetPassword() }) { Text(stringResource(R.string.action_forget_password)) }
        }
        OutlinedTextField(form.baseDn, { viewModel.update { f -> f.copy(baseDn = it) } }, label = { Text(stringResource(R.string.profile_base_dn)) }, modifier = ComposeModifier.fillMaxWidth())
        OutlinedTextField(form.connectTimeoutMs, { viewModel.update { f -> f.copy(connectTimeoutMs = it) } }, label = { Text(stringResource(R.string.profile_connect_timeout)) }, modifier = ComposeModifier.fillMaxWidth())
        OutlinedTextField(form.readTimeoutMs, { viewModel.update { f -> f.copy(readTimeoutMs = it) } }, label = { Text(stringResource(R.string.profile_read_timeout)) }, modifier = ComposeModifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(form.followReferrals, { viewModel.update { f -> f.copy(followReferrals = it) } })
            Text(stringResource(R.string.profile_follow_referrals))
        }
        EnumDropdown(
            label = stringResource(R.string.profile_trust_mode),
            options = listOf("SYSTEM", "CUSTOM_CA", "PINNED", "INSECURE_NO_VERIFY"),
            selected = form.trustMode,
            onSelected = { viewModel.update { f -> f.copy(trustMode = it) } },
        )
        if (form.trustMode == "INSECURE_NO_VERIFY" || form.trustMode == "DIAGNOSTIC_ONLY") {
            Text(
                stringResource(R.string.profile_insecure_trust_hint),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(stringResource(R.string.profile_custom_ca_section), style = MaterialTheme.typography.titleMedium)
        CustomCaSection(viewModel = viewModel, form = form, customCas = customCas)
        if (form.trustMode == "CUSTOM_CA" && form.customCaId.isBlank()) {
            Text(stringResource(R.string.profile_custom_ca_required), color = MaterialTheme.colorScheme.error)
        }
        if (form.trustMode == "PINNED") {
            OutlinedTextField(form.pinnedFingerprint, { viewModel.update { f -> f.copy(pinnedFingerprint = it) } }, label = { Text(stringResource(R.string.profile_pinned_fingerprint)) }, modifier = ComposeModifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(form.readOnly, { viewModel.update { f -> f.copy(readOnly = it) } })
            Text(stringResource(R.string.profile_read_only))
        }
        form.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { viewModel.save() }, modifier = ComposeModifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun CustomCaSection(
    viewModel: ProfileEditViewModel,
    form: ProfileFormState,
    customCas: List<com.jbsan.ldapadvisor.data.database.entity.CustomCaEntity>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alias by remember { mutableStateOf("imported-ca") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null) {
                viewModel.update { it.copy(formError = "Unable to read CA file") }
            } else {
                viewModel.importCa(alias.ifBlank { "imported-ca" }, bytes)
            }
        }
    }
    OutlinedTextField(
        alias,
        { alias = it },
        label = { Text(stringResource(R.string.profile_ca_alias)) },
        modifier = ComposeModifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = {
            launcher.launch("*/*")
        },
        modifier = ComposeModifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.action_import_ca))
    }
    if (customCas.isEmpty()) {
        Text(stringResource(R.string.profile_custom_ca_empty), style = MaterialTheme.typography.bodySmall)
    } else {
        customCas.forEach { ca ->
            val selected = form.customCaId == ca.id
            val validity = DateFormat.getDateInstance().format(Date(ca.notBeforeEpochMs)) +
                " – " + DateFormat.getDateInstance().format(Date(ca.notAfterEpochMs))
            ListItem(
                headlineContent = { Text(ca.alias + if (selected) " ✓" else "") },
                supportingContent = {
                    Text("${ca.subject}\n${ca.sha256Fingerprint}\n$validity")
                },
                trailingContent = {
                    Row {
                        TextButton(onClick = { viewModel.selectCa(ca.id) }) {
                            Text(stringResource(R.string.action_select))
                        }
                        TextButton(onClick = { viewModel.deleteCa(ca.id) }) {
                            Text(stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = ComposeModifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
