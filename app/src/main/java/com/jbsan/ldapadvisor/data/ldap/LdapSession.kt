package com.jbsan.ldapadvisor.data.ldap

import com.jbsan.ldapadvisor.domain.model.DirectoryCapabilities
import com.jbsan.ldapadvisor.domain.model.SecurityMode

class LdapSession internal constructor(
    val client: LdapClient,
    val rootDse: RootDseInfo?,
    val capabilities: DirectoryCapabilities,
) {
    val profileId: String get() = client.profileId
    val host: String get() = client.host
    val port: Int get() = client.port
    val securityMode: SecurityMode get() = client.securityMode
    val tlsActive: Boolean get() = client.isTlsActive
    val kerberosBound: Boolean get() = client.isKerberosBound
    val allowsPasswordChannel: Boolean get() = client.allowsPasswordChannel
    val boundAs: String? get() = client.boundIdentity()
    val readOnly: Boolean get() = client.readOnly

    fun isConnected(): Boolean = client.isConnected()

    fun close() = client.disconnect()

    fun info(): ActiveSessionInfo = client.sessionInfo(rootDse, capabilities)
}
