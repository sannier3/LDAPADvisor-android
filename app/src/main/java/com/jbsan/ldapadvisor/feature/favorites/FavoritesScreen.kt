package com.jbsan.ldapadvisor.feature.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.jbsan.ldapadvisor.ui.ComposeModifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.ui.components.EmptyState

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpen: (String) -> Unit,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    Column(ComposeModifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.headlineSmall)
        if (favorites.isEmpty()) {
            EmptyState(stringResource(R.string.favorites_empty), stringResource(R.string.favorites_empty_hint))
        } else {
            LazyColumn(ComposeModifier.weight(1f)) {
                items(favorites, key = { it.id }) { fav ->
                    ListItem(
                        headlineContent = { Text(fav.label) },
                        supportingContent = { Text(fav.dn) },
                        modifier = ComposeModifier.clickable { onOpen(fav.dn) },
                        trailingContent = {
                            TextButton(onClick = { viewModel.remove(fav.id) }) {
                                Text(stringResource(R.string.action_delete))
                            }
                        },
                    )
                }
            }
        }
        Text(stringResource(R.string.search_history_title), style = MaterialTheme.typography.titleMedium)
        if (history.isEmpty()) {
            Text(stringResource(R.string.search_history_empty), style = MaterialTheme.typography.bodySmall)
        } else {
            history.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.filter) },
                    supportingContent = { Text(item.baseDn) },
                )
            }
        }
    }
}
