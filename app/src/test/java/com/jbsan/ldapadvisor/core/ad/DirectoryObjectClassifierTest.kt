package com.jbsan.ldapadvisor.core.ad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryObjectClassifierTest {
    @Test
    fun classifyComputerBeforeUser() {
        assertEquals(
            DirectoryObjectKind.COMPUTER,
            DirectoryObjectClassifier.classify(listOf("top", "person", "organizationalPerson", "user", "computer")),
        )
    }

    @Test
    fun expandableOnlyFolders() {
        assertTrue(DirectoryObjectKind.OU.isExpandable())
        assertTrue(DirectoryObjectKind.CONTAINER.isExpandable())
        assertTrue(DirectoryObjectKind.DOMAIN.isExpandable())
        assertFalse(DirectoryObjectKind.USER.isExpandable())
        assertFalse(DirectoryObjectKind.GROUP.isExpandable())
        assertFalse(DirectoryObjectKind.COMPUTER.isExpandable())
    }

    @Test
    fun explorerOrderPutsOuFirstThenAlpha() {
        val items = listOf(
            "Zed" to DirectoryObjectKind.USER,
            "Sales" to DirectoryObjectKind.OU,
            "Alice" to DirectoryObjectKind.USER,
            "Admins" to DirectoryObjectKind.GROUP,
            "Finance" to DirectoryObjectKind.OU,
        )
        val sorted = items.sortedWith { a, b ->
            DirectoryObjectClassifier.compareExplorerOrder(a.first, a.second, b.first, b.second)
        }.map { it.first }
        assertEquals(listOf("Finance", "Sales", "Admins", "Alice", "Zed"), sorted)
    }

    @Test
    fun displayNamePrefersDisplayNameThenCn() {
        assertEquals(
            "Alice Martin",
            DirectoryObjectClassifier.displayName(
                dn = "CN=amartin,OU=Users,DC=corp,DC=example,DC=com",
                objectClasses = listOf("user"),
                cn = "amartin",
                displayName = "Alice Martin",
            ),
        )
    }
}
