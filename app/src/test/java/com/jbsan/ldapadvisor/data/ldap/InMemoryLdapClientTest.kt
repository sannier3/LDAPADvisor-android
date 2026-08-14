package com.jbsan.ldapadvisor.data.ldap

import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.DirectoryType
import com.jbsan.ldapadvisor.domain.model.SecurityMode
import com.jbsan.ldapadvisor.domain.model.TrustMode
import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import com.unboundid.ldap.sdk.Attribute
import com.unboundid.ldap.sdk.Entry
import com.unboundid.ldap.sdk.LDAPConnection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InMemoryLdapClientTest {
    private lateinit var server: InMemoryDirectoryServer
    private var port: Int = 0

    @Before
    fun setUp() {
        val config = InMemoryDirectoryServerConfig("dc=corp,dc=example,dc=com")
        config.schema = null // allow flexible test entries without AD schema
        config.addAdditionalBindCredentials("cn=admin,dc=corp,dc=example,dc=com", "admin-password")
        config.listenerConfigs.clear()
        config.listenerConfigs.add(InMemoryListenerConfig.createLDAPConfig("default"))
        server = InMemoryDirectoryServer(config)
        server.add(
            Entry(
                "dc=corp,dc=example,dc=com",
                Attribute("objectClass", "top", "domain"),
                Attribute("dc", "corp"),
            ),
        )
        server.add(
            Entry(
                "ou=users,dc=corp,dc=example,dc=com",
                Attribute("objectClass", "top", "organizationalUnit"),
                Attribute("ou", "users"),
            ),
        )
        server.add(
            Entry(
                "cn=jdoe,ou=users,dc=corp,dc=example,dc=com",
                Attribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson"),
                Attribute("cn", "jdoe"),
                Attribute("sn", "Doe"),
                Attribute("uid", "jdoe"),
            ),
        )
        server.startListening()
        port = server.listenPort
    }

    @After
    fun tearDown() {
        server.shutDown(true)
    }

    private fun profile() = ConnectionProfile(
        id = "test",
        name = "inmemory",
        directoryType = DirectoryType.GENERIC_LDAP,
        domain = "corp.example.com",
        host = "127.0.0.1",
        port = port,
        securityMode = SecurityMode.LDAP,
        bindIdentity = "cn=admin,dc=corp,dc=example,dc=com",
        baseDn = "dc=corp,dc=example,dc=com",
        trustMode = TrustMode.SYSTEM,
        readOnly = false,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun bindSearchAddModifyDelete() = runBlocking {
        val connection = LDAPConnection("127.0.0.1", port)
        connection.bind("cn=admin,dc=corp,dc=example,dc=com", "admin-password")
        val client = LdapClient(connection, profile(), tlsActive = false)

        val search = client.search(
            LdapSearchRequest(
                baseDn = "dc=corp,dc=example,dc=com",
                filter = "(uid=jdoe)",
                scope = SearchScopeMode.SUB,
                attributes = arrayOf("cn", "uid"),
            ),
        ).getOrThrow()
        assertEquals(1, search.size)
        assertEquals("jdoe", search.first().firstString("uid"))

        client.add(
            "cn=asmith,ou=users,dc=corp,dc=example,dc=com",
            mapOf(
                "objectClass" to listOf("top", "person", "organizationalPerson", "inetOrgPerson"),
                "cn" to listOf("asmith"),
                "sn" to listOf("Smith"),
                "uid" to listOf("asmith"),
            ),
        ).getOrThrow()

        client.modify(
            "cn=asmith,ou=users,dc=corp,dc=example,dc=com",
            listOf(
                LdapModificationSpec(
                    attribute = "description",
                    type = LdapModificationSpec.Type.REPLACE,
                    values = listOf("demo".toByteArray()),
                ),
            ),
        ).getOrThrow()

        val compare = client.compare(
            "cn=asmith,ou=users,dc=corp,dc=example,dc=com",
            "uid",
            "asmith",
        ).getOrThrow()
        assertTrue(compare)

        client.delete("cn=asmith,ou=users,dc=corp,dc=example,dc=com").getOrThrow()
        client.disconnect()
    }

    @Test
    fun plaintextBindRequiresConfirmation() = runBlocking {
        val connection = LDAPConnection("127.0.0.1", port)
        val client = LdapClient(connection, profile(), tlsActive = false)
        val outcome = client.bindSimple(
            "cn=admin,dc=corp,dc=example,dc=com",
            "admin-password".toCharArray(),
            allowPlaintextConfirmation = false,
        )
        assertTrue(outcome is BindOutcome.RequiresPlaintextConfirmation)
        connection.close()
    }

    @Test
    fun insecureTrustBindRequiresConfirmation() = runBlocking {
        val connection = LDAPConnection("127.0.0.1", port)
        val client = LdapClient(
            connection,
            profile().copy(
                securityMode = SecurityMode.LDAPS,
                trustMode = TrustMode.INSECURE_NO_VERIFY,
            ),
            tlsActive = true,
        )
        val outcome = client.bindSimple(
            "cn=admin,dc=corp,dc=example,dc=com",
            "admin-password".toCharArray(),
            allowPlaintextConfirmation = true,
            allowInsecureTrustConfirmation = false,
        )
        assertTrue(outcome is BindOutcome.RequiresInsecureTrustConfirmation)
        connection.close()
    }

    @Test
    fun readOnlyBlocksMutations() = runBlocking {
        val connection = LDAPConnection("127.0.0.1", port)
        connection.bind("cn=admin,dc=corp,dc=example,dc=com", "admin-password")
        val client = LdapClient(connection, profile().copy(readOnly = true), tlsActive = false)
        val result = client.delete("cn=jdoe,ou=users,dc=corp,dc=example,dc=com")
        assertTrue(result.isFailure)
        connection.close()
    }

    @Test
    fun passwordResetRequiresSecureChannel() = runBlocking {
        val connection = LDAPConnection("127.0.0.1", port)
        connection.bind("cn=admin,dc=corp,dc=example,dc=com", "admin-password")
        val client = LdapClient(connection, profile(), tlsActive = false)
        val result = client.resetAdPassword(
            "cn=jdoe,ou=users,dc=corp,dc=example,dc=com",
            "NewPassword1!".toCharArray(),
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.SecureChannelRequired)
        connection.close()
    }

    @Test
    fun passwordModifyRequiresSecureChannelWithoutTls() = runBlocking {
        val connection = LDAPConnection("127.0.0.1", port)
        connection.bind("cn=admin,dc=corp,dc=example,dc=com", "admin-password")
        val client = LdapClient(connection, profile(), tlsActive = false)
        val result = client.changePasswordPasswordModify(
            userIdentity = "cn=jdoe,ou=users,dc=corp,dc=example,dc=com",
            oldPassword = null,
            newPassword = "NewPassword1!".toCharArray(),
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.SecureChannelRequired)
        connection.close()
    }
}
