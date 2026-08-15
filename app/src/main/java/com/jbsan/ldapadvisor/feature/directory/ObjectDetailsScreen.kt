package com.jbsan.ldapadvisor.feature.directory

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.core.ad.DirectoryObjectKind
import com.jbsan.ldapadvisor.core.util.DnUtils
import com.jbsan.ldapadvisor.data.ldap.LdapModificationSpec
import com.jbsan.ldapadvisor.ui.ComposeModifier
import com.jbsan.ldapadvisor.ui.components.OuTreePickerDialog
import com.jbsan.ldapadvisor.ui.components.SessionBanner

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObjectDetailsScreen(
    viewModel: ObjectDetailsViewModel,
    dn: String,
    onEditUser: ((String) -> Unit)? = null,
    onCopyUser: ((String) -> Unit)? = null,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.ouPicker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(dn) { viewModel.load(dn) }

    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showModify by remember { mutableStateOf(false) }
    var showCompare by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmBody by remember { mutableStateOf("") }

    Column(
        ComposeModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = iconForKind(ui.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(ComposeModifier.weight(1f)) {
                Text(
                    ui.displayName.ifBlank { stringResource(R.string.nav_object_details) },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = ComposeModifier.semantics { heading() },
                )
                Text(
                    kindLabel(ui.kind),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(ui.dn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!ui.connected) {
            SessionBanner(
                text = stringResource(R.string.create_need_connection),
                isWarning = true,
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }
        if (ui.readOnly) {
            SessionBanner(
                text = stringResource(R.string.object_readonly_hint),
                isWarning = true,
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }
        ui.error?.let {
            SessionBanner(text = it, isWarning = true, modifier = ComposeModifier.fillMaxWidth())
        }
        ui.message?.let {
            SessionBanner(
                text = stringResource(R.string.user_detail_saved),
                isWarning = false,
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ui.overview,
                onClick = { viewModel.setOverview(true) },
                label = { Text(stringResource(R.string.object_overview)) },
            )
            FilterChip(
                selected = !ui.overview,
                onClick = { viewModel.setOverview(false) },
                label = { Text(stringResource(R.string.object_raw)) },
            )
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("dn", ui.dn))
            }) { Text(stringResource(R.string.action_copy_dn)) }
            TextButton(onClick = { viewModel.toggleFavorite() }) {
                Text(
                    if (ui.favorite) stringResource(R.string.action_unfavorite)
                    else stringResource(R.string.action_favorite),
                )
            }
        }

        if (ui.kind == DirectoryObjectKind.USER && onEditUser != null) {
            Button(
                onClick = { onEditUser(ui.dn) },
                modifier = ComposeModifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.object_edit_user_full))
            }
        }
        if (ui.kind == DirectoryObjectKind.USER && onCopyUser != null && !ui.readOnly) {
            OutlinedButton(
                onClick = { onCopyUser(ui.dn) },
                modifier = ComposeModifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_copy_user))
            }
        }

        if (!ui.readOnly) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { showRename = true }) { Text(stringResource(R.string.action_rename)) }
                OutlinedButton(onClick = { viewModel.openMovePicker() }) {
                    Text(stringResource(R.string.action_move))
                }
                OutlinedButton(onClick = { showModify = true }) { Text(stringResource(R.string.action_modify_attr)) }
                OutlinedButton(onClick = { showDelete = true }) { Text(stringResource(R.string.action_delete)) }
            }
        }
        OutlinedButton(onClick = { showCompare = true }) { Text(stringResource(R.string.action_compare)) }

        ui.compareResult?.let {
            Text(
                when (it) {
                    "matched" -> stringResource(R.string.compare_matched)
                    else -> stringResource(R.string.compare_not_matched)
                },
            )
        }

        if (ui.overview) {
            OverviewEditor(ui = ui, viewModel = viewModel)
        } else {
            val entry = ui.entry ?: return
            Text(
                stringResource(R.string.object_raw_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.attributes.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (name, values) ->
                Text(name, style = MaterialTheme.typography.titleSmall)
                values.forEach { bytes ->
                    val asText = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
                    val printable = asText != null && asText.all { ch -> ch.code >= 32 || ch == '\n' || ch == '\t' }
                    if (printable) {
                        Text(asText!!)
                    } else {
                        Text(stringResource(R.string.object_binary_size, bytes.size))
                        Text(stringResource(R.string.object_hex_preview) + ": " + viewModel.hexPreview(bytes))
                        Text(stringResource(R.string.object_base64) + ": " + viewModel.base64(bytes).take(120))
                    }
                }
            }
        }
    }

    if (showDelete) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = {
                Column {
                    Text(stringResource(R.string.admin_delete_confirm, ui.dn))
                    OutlinedTextField(
                        typed,
                        { typed = it },
                        label = { Text(DnUtils.objectNameFromDn(ui.dn)) },
                        modifier = ComposeModifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBody = "Target: ${ui.dn}\nOperation: DELETE"
                    pendingConfirm = {
                        viewModel.deleteObject(typed)
                        showDelete = false
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showRename) {
        var newRdn by remember { mutableStateOf(ui.dn.substringBefore(',')) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                OutlinedTextField(
                    newRdn,
                    { newRdn = it },
                    label = { Text(stringResource(R.string.object_new_rdn)) },
                    modifier = ComposeModifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBody = "Target: ${ui.dn}\nOperation: RENAME → $newRdn"
                    pendingConfirm = {
                        viewModel.rename(newRdn)
                        showRename = false
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    val movePickHint = stringResource(R.string.object_move_pick_hint)
    OuTreePickerDialog(
        state = picker,
        title = stringResource(R.string.action_move),
        onDismiss = { viewModel.ouPicker.dismiss() },
        onToggle = { viewModel.ouPicker.toggle(it) },
        onSelect = { viewModel.ouPicker.select(it) },
        onConfirm = { selected ->
            viewModel.ouPicker.dismiss()
            confirmBody = "Target: ${ui.dn}\nOperation: MOVE → $selected\n$movePickHint"
            pendingConfirm = { viewModel.move(selected) }
        },
    )
    if (showModify) {
        var attr by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        var oldValue by remember { mutableStateOf("") }
        var op by remember { mutableStateOf(LdapModificationSpec.Type.REPLACE) }
        AlertDialog(
            onDismissRequest = { showModify = false },
            title = { Text(stringResource(R.string.action_modify_attr)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(attr, { attr = it }, label = { Text(stringResource(R.string.object_attr_name)) }, modifier = ComposeModifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            LdapModificationSpec.Type.ADD,
                            LdapModificationSpec.Type.REPLACE,
                            LdapModificationSpec.Type.DELETE,
                        ).forEach { type ->
                            FilterChip(selected = op == type, onClick = { op = type }, label = { Text(type.name) })
                        }
                    }
                    OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.object_attr_value)) }, modifier = ComposeModifier.fillMaxWidth())
                    OutlinedTextField(oldValue, { oldValue = it }, label = { Text(stringResource(R.string.object_attr_old_value)) }, modifier = ComposeModifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val opLabel = "${op.name} $attr" + if (oldValue.isNotBlank()) " old=$oldValue new=$value" else " value=$value"
                    confirmBody = "Target: ${ui.dn}\nOperation: $opLabel"
                    pendingConfirm = {
                        viewModel.modifyAttribute(attr, op, value, oldValue)
                        showModify = false
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showModify = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showCompare) {
        var attr by remember { mutableStateOf("") }
        var assertion by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCompare = false },
            title = { Text(stringResource(R.string.action_compare)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(attr, { attr = it }, label = { Text(stringResource(R.string.object_attr_name)) }, modifier = ComposeModifier.fillMaxWidth())
                    OutlinedTextField(assertion, { assertion = it }, label = { Text(stringResource(R.string.object_compare_assertion)) }, modifier = ComposeModifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.compare(attr, assertion)
                    showCompare = false
                }) { Text(stringResource(R.string.action_run)) }
            },
            dismissButton = { TextButton(onClick = { showCompare = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    pendingConfirm?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text(stringResource(R.string.admin_confirm_title)) },
            text = { Text(confirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    action()
                    pendingConfirm = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingConfirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun OverviewEditor(ui: ObjectDetailsUiState, viewModel: ObjectDetailsViewModel) {
    val editable = !ui.readOnly
    Card(ComposeModifier.fillMaxWidth()) {
        Column(
            ComposeModifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.object_primary_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = ComposeModifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.object_primary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (ui.kind) {
                DirectoryObjectKind.USER, DirectoryObjectKind.CONTACT -> {
                    ReadOnlyLine(stringResource(R.string.create_cn), ui.form.cn)
                    EditField(stringResource(R.string.user_field_given_name), ui.form.givenName, editable) {
                        viewModel.updateForm { f -> f.copy(givenName = it) }
                    }
                    EditField(stringResource(R.string.user_field_surname), ui.form.sn, editable) {
                        viewModel.updateForm { f -> f.copy(sn = it) }
                    }
                    EditField(stringResource(R.string.user_field_initials), ui.form.initials, editable) {
                        viewModel.updateForm { f -> f.copy(initials = it) }
                    }
                    EditField(stringResource(R.string.create_display_name), ui.form.displayName, editable) {
                        viewModel.updateForm { f -> f.copy(displayName = it) }
                    }
                    EditField(stringResource(R.string.user_field_mail), ui.form.mail, editable) {
                        viewModel.updateForm { f -> f.copy(mail = it) }
                    }
                    EditField(stringResource(R.string.user_field_phone), ui.form.telephoneNumber, editable) {
                        viewModel.updateForm { f -> f.copy(telephoneNumber = it) }
                    }
                    EditField(stringResource(R.string.user_field_mobile), ui.form.mobile, editable) {
                        viewModel.updateForm { f -> f.copy(mobile = it) }
                    }
                    EditField(stringResource(R.string.user_field_website), ui.form.wwwHomePage, editable) {
                        viewModel.updateForm { f -> f.copy(wwwHomePage = it) }
                    }
                    EditField(stringResource(R.string.user_field_title), ui.form.title, editable) {
                        viewModel.updateForm { f -> f.copy(title = it) }
                    }
                    EditField(stringResource(R.string.user_field_department), ui.form.department, editable) {
                        viewModel.updateForm { f -> f.copy(department = it) }
                    }
                    EditField(stringResource(R.string.user_field_company), ui.form.company, editable) {
                        viewModel.updateForm { f -> f.copy(company = it) }
                    }
                    EditField(
                        stringResource(R.string.user_field_street),
                        ui.form.streetAddress,
                        editable,
                        multiline = true,
                    ) {
                        viewModel.updateForm { f -> f.copy(streetAddress = it) }
                    }
                    EditField(stringResource(R.string.user_field_city), ui.form.city, editable) {
                        viewModel.updateForm { f -> f.copy(city = it) }
                    }
                    EditField(stringResource(R.string.user_field_state), ui.form.state, editable) {
                        viewModel.updateForm { f -> f.copy(state = it) }
                    }
                    EditField(stringResource(R.string.user_field_postal), ui.form.postalCode, editable) {
                        viewModel.updateForm { f -> f.copy(postalCode = it) }
                    }
                    EditField(stringResource(R.string.user_field_country), ui.form.country, editable) {
                        viewModel.updateForm { f -> f.copy(country = it) }
                    }
                    if (ui.isAd) {
                        EditField(stringResource(R.string.user_field_country_name), ui.form.countryName, editable) {
                            viewModel.updateForm { f -> f.copy(countryName = it) }
                        }
                        EditField(stringResource(R.string.user_field_country_code), ui.form.countryCode, editable) {
                            viewModel.updateForm { f -> f.copy(countryCode = it) }
                        }
                    }
                    EditField(stringResource(R.string.object_field_description), ui.form.description, editable) {
                        viewModel.updateForm { f -> f.copy(description = it) }
                    }
                }
                DirectoryObjectKind.GROUP -> {
                    ReadOnlyLine(stringResource(R.string.create_cn), ui.form.cn)
                    EditField(stringResource(R.string.user_field_mail), ui.form.mail, editable) {
                        viewModel.updateForm { f -> f.copy(mail = it) }
                    }
                    EditField(stringResource(R.string.object_field_description), ui.form.description, editable) {
                        viewModel.updateForm { f -> f.copy(description = it) }
                    }
                }
                DirectoryObjectKind.COMPUTER -> {
                    ReadOnlyLine(stringResource(R.string.create_cn), ui.form.cn)
                    EditField(stringResource(R.string.object_field_dns_hostname), ui.form.dnsHostName, editable) {
                        viewModel.updateForm { f -> f.copy(dnsHostName = it) }
                    }
                    EditField(stringResource(R.string.object_field_description), ui.form.description, editable) {
                        viewModel.updateForm { f -> f.copy(description = it) }
                    }
                }
                DirectoryObjectKind.OU, DirectoryObjectKind.CONTAINER, DirectoryObjectKind.DOMAIN -> {
                    ReadOnlyLine(stringResource(R.string.object_dn), ui.dn)
                    EditField(stringResource(R.string.object_field_description), ui.form.description, editable) {
                        viewModel.updateForm { f -> f.copy(description = it) }
                    }
                }
                DirectoryObjectKind.GENERIC -> {
                    EditField(stringResource(R.string.create_cn), ui.form.cn, editable) {
                        viewModel.updateForm { f -> f.copy(cn = it) }
                    }
                    EditField(stringResource(R.string.object_field_description), ui.form.description, editable) {
                        viewModel.updateForm { f -> f.copy(description = it) }
                    }
                }
            }
            if (editable) {
                Button(
                    onClick = { viewModel.savePrimaryAttributes() },
                    enabled = !ui.saving,
                    modifier = ComposeModifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.object_save_primary))
                }
            }
        }
    }
}

@Composable
private fun EditField(
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

private fun iconForKind(kind: DirectoryObjectKind): ImageVector = when (kind) {
    DirectoryObjectKind.DOMAIN -> Icons.Filled.Business
    DirectoryObjectKind.OU, DirectoryObjectKind.CONTAINER -> Icons.Filled.Folder
    DirectoryObjectKind.USER -> Icons.Filled.Person
    DirectoryObjectKind.GROUP -> Icons.Filled.Groups
    DirectoryObjectKind.COMPUTER -> Icons.Filled.Computer
    DirectoryObjectKind.CONTACT -> Icons.Outlined.ContactPage
    DirectoryObjectKind.GENERIC -> Icons.Outlined.Description
}

@Composable
private fun kindLabel(kind: DirectoryObjectKind): String = stringResource(
    when (kind) {
        DirectoryObjectKind.DOMAIN -> R.string.directory_kind_domain
        DirectoryObjectKind.OU -> R.string.directory_kind_ou
        DirectoryObjectKind.CONTAINER -> R.string.directory_kind_container
        DirectoryObjectKind.USER -> R.string.directory_kind_user
        DirectoryObjectKind.GROUP -> R.string.directory_kind_group
        DirectoryObjectKind.COMPUTER -> R.string.directory_kind_computer
        DirectoryObjectKind.CONTACT -> R.string.directory_kind_contact
        DirectoryObjectKind.GENERIC -> R.string.directory_kind_generic
    },
)
