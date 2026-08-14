package com.jbsan.ldapadvisor.feature.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbsan.ldapadvisor.core.ad.DirectoryObjectClassifier
import com.jbsan.ldapadvisor.core.ad.DirectoryObjectKind
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DirectoryNode(
    val dn: String,
    val displayName: String,
    val kind: DirectoryObjectKind,
    val objectClasses: List<String>,
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val children: List<DirectoryNode> = emptyList(),
) {
    val expandable: Boolean get() = kind.isExpandable()
}

data class DirectoryUiState(
    val rootDn: String = "",
    val roots: List<DirectoryNode> = emptyList(),
    val error: String? = null,
    val connected: Boolean = false,
    val loading: Boolean = false,
)

class DirectoryViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(DirectoryUiState())
    val uiState: StateFlow<DirectoryUiState> = _ui.asStateFlow()

    fun refresh() = viewModelScope.launch {
        val session = sessionManager.currentSession()
        if (session == null || !session.isConnected()) {
            _ui.value = DirectoryUiState(connected = false)
            return@launch
        }
        _ui.value = _ui.value.copy(loading = true, error = null, connected = true)
        val rootDn = session.capabilities.defaultNamingContext
            ?.takeIf { it.isNotBlank() }
            ?: session.rootDse?.defaultNamingContext.orEmpty()
        if (rootDn.isBlank()) {
            _ui.value = DirectoryUiState(connected = true, error = "No base DN", loading = false)
            return@launch
        }
        val entry = session.client.search(
            LdapSearchRequest(rootDn, "(objectClass=*)", SearchScopeMode.BASE, BROWSE_ATTRS),
        ).getOrNull()?.firstOrNull()
        val root = entry.toNode(fallbackDn = rootDn)
        _ui.value = DirectoryUiState(
            rootDn = rootDn,
            roots = listOf(root),
            connected = true,
            loading = false,
        )
        // Auto-expand domain root so the explorer is immediately useful.
        toggle(rootDn)
    }

    fun toggle(dn: String) = viewModelScope.launch {
        val current = findNode(_ui.value.roots, dn) ?: return@launch
        if (!current.expandable) return@launch
        if (current.expanded) {
            _ui.value = _ui.value.copy(
                roots = updateNode(_ui.value.roots, dn) { it.copy(expanded = false, loading = false) },
            )
            return@launch
        }
        val session = sessionManager.currentSession() ?: return@launch
        _ui.value = _ui.value.copy(roots = updateNode(_ui.value.roots, dn) { it.copy(loading = true) })
        val childrenResult = session.client.search(
            LdapSearchRequest(
                baseDn = dn,
                filter = "(objectClass=*)",
                scope = SearchScopeMode.ONE,
                attributes = BROWSE_ATTRS,
                pageSize = 500,
                sizeLimit = 500,
            ),
        )
        childrenResult.fold(
            onSuccess = { entries ->
                val children = entries
                    .filter { it.dn != dn }
                    .map { it.toNode() }
                    .sortedWith { a, b ->
                        DirectoryObjectClassifier.compareExplorerOrder(
                            a.displayName, a.kind, b.displayName, b.kind,
                        )
                    }
                _ui.value = _ui.value.copy(
                    roots = updateNode(_ui.value.roots, dn) {
                        it.copy(expanded = true, loading = false, children = children)
                    },
                    error = null,
                )
            },
            onFailure = { err ->
                _ui.value = _ui.value.copy(
                    roots = updateNode(_ui.value.roots, dn) { it.copy(loading = false) },
                    error = err.message,
                )
            },
        )
    }

    private fun LdapEntryData?.toNode(fallbackDn: String = ""): DirectoryNode {
        val dn = this?.dn?.takeIf { it.isNotBlank() } ?: fallbackDn
        val classes = this?.stringValues("objectClass").orEmpty()
        val kind = DirectoryObjectClassifier.classify(classes)
        val display = DirectoryObjectClassifier.displayName(
            dn = dn,
            objectClasses = classes,
            name = this?.firstString("name"),
            cn = this?.firstString("cn"),
            ou = this?.firstString("ou"),
            dc = this?.firstString("dc"),
            displayName = this?.firstString("displayName"),
            samAccountName = this?.firstString("sAMAccountName"),
        )
        return DirectoryNode(
            dn = dn,
            displayName = display,
            kind = kind,
            objectClasses = classes,
        )
    }

    private fun findNode(nodes: List<DirectoryNode>, dn: String): DirectoryNode? {
        for (node in nodes) {
            if (node.dn.equals(dn, ignoreCase = true)) return node
            findNode(node.children, dn)?.let { return it }
        }
        return null
    }

    private fun updateNode(
        nodes: List<DirectoryNode>,
        dn: String,
        transform: (DirectoryNode) -> DirectoryNode,
    ): List<DirectoryNode> =
        nodes.map { node ->
            when {
                node.dn.equals(dn, ignoreCase = true) -> transform(node)
                node.children.isNotEmpty() -> node.copy(children = updateNode(node.children, dn, transform))
                else -> node
            }
        }

    companion object {
        private val BROWSE_ATTRS = arrayOf(
            "objectClass", "name", "ou", "cn", "dc", "displayName", "sAMAccountName", "description",
        )
    }
}
