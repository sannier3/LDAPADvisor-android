package com.jbsan.ldapadvisor.feature.directory

import com.jbsan.ldapadvisor.core.ad.DirectoryObjectClassifier
import com.jbsan.ldapadvisor.core.ad.DirectoryObjectKind
import com.jbsan.ldapadvisor.data.ldap.LdapEntryData
import com.jbsan.ldapadvisor.data.ldap.LdapSearchRequest
import com.jbsan.ldapadvisor.data.ldap.SearchScopeMode
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.domain.model.resolveBrowseRootDns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OuPickerNode(
    val dn: String,
    val displayName: String,
    val kind: DirectoryObjectKind,
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val children: List<OuPickerNode> = emptyList(),
)

data class OuTreePickerUiState(
    val visible: Boolean = false,
    val roots: List<OuPickerNode> = emptyList(),
    val selectedDn: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Browse-only tree of containers/OUs from the session base DN / namingContexts.
 * Used to pick a parent DN for create, copy, and move — no free-typed DN.
 */
class OuTreePickerController(
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(OuTreePickerUiState())
    val state: StateFlow<OuTreePickerUiState> = _state.asStateFlow()

    fun open(preselectedDn: String = "") {
        scope.launch {
            val session = sessionManager.currentSession()
            if (session == null || !session.isConnected()) {
                _state.value = OuTreePickerUiState(
                    visible = true,
                    error = "Not connected",
                )
                return@launch
            }
            _state.value = OuTreePickerUiState(
                visible = true,
                selectedDn = preselectedDn.trim(),
                loading = true,
            )
            val rootDns = session.capabilities.resolveBrowseRootDns()
            if (rootDns.isEmpty()) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "No base DN — set Base DN on the profile",
                )
                return@launch
            }
            val roots = mutableListOf<OuPickerNode>()
            for (rootDn in rootDns) {
                val entry = session.client.search(
                    LdapSearchRequest(rootDn, "(objectClass=*)", SearchScopeMode.BASE, BROWSE_ATTRS),
                ).getOrNull()?.firstOrNull()
                roots += entry.toPickerNode(fallbackDn = rootDn)
            }
            _state.value = _state.value.copy(
                roots = roots,
                loading = false,
                error = null,
                selectedDn = _state.value.selectedDn.ifBlank { rootDns.first() },
            )
            // Expand first root so the user sees OUs immediately.
            toggle(rootDns.first())
        }
    }

    fun dismiss() {
        _state.value = OuTreePickerUiState()
    }

    fun select(dn: String) {
        _state.value = _state.value.copy(selectedDn = dn)
    }

    fun toggle(dn: String) {
        scope.launch {
            val current = findNode(_state.value.roots, dn) ?: return@launch
            if (current.expanded) {
                _state.value = _state.value.copy(
                    roots = updateNode(_state.value.roots, dn) {
                        it.copy(expanded = false, loading = false)
                    },
                )
                return@launch
            }
            val session = sessionManager.currentSession() ?: return@launch
            _state.value = _state.value.copy(
                roots = updateNode(_state.value.roots, dn) { it.copy(loading = true) },
            )
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
                        .map { it.toPickerNode() }
                        .filter { it.kind.isExpandable() }
                        .sortedBy { it.displayName.lowercase() }
                    _state.value = _state.value.copy(
                        roots = updateNode(_state.value.roots, dn) {
                            it.copy(expanded = true, loading = false, children = children)
                        },
                        error = null,
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        roots = updateNode(_state.value.roots, dn) { it.copy(loading = false) },
                        error = err.message,
                    )
                },
            )
        }
    }

    private fun LdapEntryData?.toPickerNode(fallbackDn: String = ""): OuPickerNode {
        val dn = this?.dn?.takeIf { it.isNotBlank() } ?: fallbackDn
        val classes = this?.stringValues("objectClass").orEmpty()
        val kind = DirectoryObjectClassifier.classify(classes, dn)
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
        return OuPickerNode(dn = dn, displayName = display, kind = kind)
    }

    private fun findNode(nodes: List<OuPickerNode>, dn: String): OuPickerNode? {
        for (node in nodes) {
            if (node.dn.equals(dn, ignoreCase = true)) return node
            findNode(node.children, dn)?.let { return it }
        }
        return null
    }

    private fun updateNode(
        nodes: List<OuPickerNode>,
        dn: String,
        transform: (OuPickerNode) -> OuPickerNode,
    ): List<OuPickerNode> =
        nodes.map { node ->
            when {
                node.dn.equals(dn, ignoreCase = true) -> transform(node)
                node.children.isNotEmpty() ->
                    node.copy(children = updateNode(node.children, dn, transform))
                else -> node
            }
        }

    companion object {
        private val BROWSE_ATTRS = arrayOf(
            "objectClass", "name", "ou", "cn", "dc", "displayName", "sAMAccountName", "description",
        )
    }
}
