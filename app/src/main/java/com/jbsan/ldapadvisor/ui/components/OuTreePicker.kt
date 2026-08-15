package com.jbsan.ldapadvisor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.feature.directory.OuPickerNode
import com.jbsan.ldapadvisor.feature.directory.OuTreePickerUiState
import com.jbsan.ldapadvisor.ui.ComposeModifier

@Composable
fun ParentContainerField(
    label: String,
    selectedDn: String,
    onBrowse: () -> Unit,
    hint: String? = null,
) {
    Column(
        ComposeModifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            selectedDn.ifBlank { stringResource(R.string.ou_picker_none) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectedDn.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (!hint.isNullOrBlank()) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val browseLabel = stringResource(R.string.ou_picker_browse)
        OutlinedButton(
            onClick = onBrowse,
            modifier = ComposeModifier.fillMaxWidth().semantics { contentDescription = browseLabel },
        ) {
            Text(browseLabel)
        }
    }
}

@Composable
fun OuTreePickerDialog(
    state: OuTreePickerUiState,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit,
    onConfirm: (String) -> Unit,
    title: String = stringResource(R.string.ou_picker_title),
) {
    if (!state.visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                ComposeModifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.ou_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.loading && state.roots.isEmpty()) {
                    CircularProgressIndicator(ComposeModifier.align(Alignment.CenterHorizontally))
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                LazyColumn(
                    ComposeModifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(flattenVisible(state.roots), key = { it.node.dn }) { row ->
                        OuPickerRow(
                            node = row.node,
                            depth = row.depth,
                            selected = row.node.dn.equals(state.selectedDn, ignoreCase = true),
                            onToggle = { onToggle(row.node.dn) },
                            onSelect = { onSelect(row.node.dn) },
                        )
                    }
                }
                if (state.selectedDn.isNotBlank()) {
                    Text(
                        stringResource(R.string.ou_picker_selected, state.selectedDn),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(state.selectedDn) },
                enabled = state.selectedDn.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun OuPickerRow(
    node: OuPickerNode,
    depth: Int,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        ComposeModifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = (depth * 12).dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            when {
                node.loading -> {
                    CircularProgressIndicator(
                        ComposeModifier.padding(4.dp),
                        strokeWidth = 2.dp,
                    )
                }
                node.expanded -> Icon(Icons.Filled.ExpandLess, contentDescription = null)
                else -> Icon(Icons.Filled.ExpandMore, contentDescription = null)
            }
        }
        Icon(
            if (node.expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(ComposeModifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(node.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                node.dn,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}

private data class FlatRow(val node: OuPickerNode, val depth: Int)

private fun flattenVisible(nodes: List<OuPickerNode>, depth: Int = 0): List<FlatRow> {
    val out = mutableListOf<FlatRow>()
    for (node in nodes) {
        out += FlatRow(node, depth)
        if (node.expanded && node.children.isNotEmpty()) {
            out += flattenVisible(node.children, depth + 1)
        }
    }
    return out
}
