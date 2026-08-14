package com.jbsan.ldapadvisor.feature.directory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.core.ad.DirectoryObjectKind
import com.jbsan.ldapadvisor.ui.ComposeModifier
import com.jbsan.ldapadvisor.ui.components.EmptyState
import com.jbsan.ldapadvisor.ui.components.SessionBanner

@Composable
fun DirectoryScreen(
    viewModel: DirectoryViewModel,
    onOpen: (String) -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(ComposeModifier.fillMaxSize()) {
        Row(
            ComposeModifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(ComposeModifier.weight(1f)) {
                Text(
                    stringResource(R.string.directory_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = ComposeModifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.directory_explorer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                )
            }
        }

        if (!ui.connected) {
            EmptyState(
                title = stringResource(R.string.directory_title),
                body = stringResource(R.string.directory_empty),
                modifier = ComposeModifier.fillMaxSize(),
            )
            return
        }

        ui.error?.let {
            SessionBanner(
                text = it,
                isWarning = true,
                modifier = ComposeModifier.fillMaxWidth(),
            )
        }

        if (ui.loading && ui.roots.isEmpty()) {
            Column(
                ComposeModifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return
        }

        LazyColumn(ComposeModifier.fillMaxSize()) {
            items(ui.roots, key = { it.dn }) { node ->
                DirectoryTree(node = node, depth = 0, viewModel = viewModel, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun DirectoryTree(
    node: DirectoryNode,
    depth: Int,
    viewModel: DirectoryViewModel,
    onOpen: (String) -> Unit,
) {
    DirectoryRow(node = node, depth = depth, viewModel = viewModel, onOpen = onOpen)
    if (node.expanded) {
        node.children.forEach { child ->
            DirectoryTree(child, depth + 1, viewModel, onOpen)
        }
    }
}

@Composable
private fun DirectoryRow(
    node: DirectoryNode,
    depth: Int,
    viewModel: DirectoryViewModel,
    onOpen: (String) -> Unit,
) {
    val kindLabel = kindLabel(node.kind)
    ListItem(
        leadingContent = {
            Icon(
                imageVector = iconFor(node),
                contentDescription = kindLabel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = ComposeModifier.size(28.dp),
            )
        },
        headlineContent = {
            Text(
                node.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Column {
                Text(
                    kindLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    node.dn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            when {
                node.loading -> CircularProgressIndicator(ComposeModifier.size(22.dp), strokeWidth = 2.dp)
                node.expandable -> {
                    val expandDesc = if (node.expanded) {
                        stringResource(R.string.directory_collapse)
                    } else {
                        stringResource(R.string.directory_expand)
                    }
                    IconButton(onClick = { viewModel.toggle(node.dn) }) {
                        Icon(
                            imageVector = if (node.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = expandDesc,
                        )
                    }
                }
                else -> {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.action_open),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = ComposeModifier
            .padding(start = (depth * 16).dp)
            .clickable { onOpen(node.dn) },
    )
    HorizontalDivider(ComposeModifier.padding(start = (depth * 16 + 56).dp))
}

private fun iconFor(node: DirectoryNode): ImageVector = when (node.kind) {
    DirectoryObjectKind.DOMAIN -> Icons.Filled.Business
    DirectoryObjectKind.OU, DirectoryObjectKind.CONTAINER ->
        if (node.expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder
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
