package com.jbsan.ldapadvisor.data.database

import com.jbsan.ldapadvisor.data.database.entity.ProfileEntity
import com.jbsan.ldapadvisor.domain.model.AuthMethod
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.DirectoryType
import com.jbsan.ldapadvisor.domain.model.SecurityMode
import com.jbsan.ldapadvisor.domain.model.parseTrustMode

object ProfileMapper {
    fun toDomain(entity: ProfileEntity): ConnectionProfile =
        ConnectionProfile(
            id = entity.id,
            name = entity.name,
            directoryType = DirectoryType.valueOf(entity.directoryType),
            domain = entity.domain,
            host = entity.host,
            port = entity.port,
            securityMode = SecurityMode.valueOf(entity.securityMode),
            authMethod = runCatching { AuthMethod.valueOf(entity.authMethod) }
                .getOrDefault(AuthMethod.SIMPLE),
            bindIdentity = entity.bindIdentity,
            baseDn = entity.baseDn,
            connectTimeoutMs = entity.connectTimeoutMs,
            readTimeoutMs = entity.readTimeoutMs,
            followReferrals = entity.followReferrals,
            trustMode = parseTrustMode(entity.trustMode),
            customCaId = entity.customCaId,
            pinnedFingerprint = entity.pinnedFingerprint,
            rememberPassword = entity.rememberPassword,
            readOnly = entity.readOnly,
            kerberosRealm = entity.kerberosRealm,
            kerberosKdcHost = entity.kerberosKdcHost,
            kerberosKdcPort = entity.kerberosKdcPort,
            kerberosServicePrincipal = entity.kerberosServicePrincipal,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastSuccessfulConnectionAt = entity.lastSuccessfulConnectionAt,
        )

    fun toEntity(profile: ConnectionProfile): ProfileEntity =
        ProfileEntity(
            id = profile.id,
            name = profile.name,
            directoryType = profile.directoryType.name,
            domain = profile.domain,
            host = profile.host,
            port = profile.port,
            securityMode = profile.securityMode.name,
            authMethod = profile.authMethod.name,
            bindIdentity = profile.bindIdentity,
            baseDn = profile.baseDn,
            connectTimeoutMs = profile.connectTimeoutMs,
            readTimeoutMs = profile.readTimeoutMs,
            followReferrals = profile.followReferrals,
            trustMode = profile.trustMode.name,
            customCaId = profile.customCaId,
            pinnedFingerprint = profile.pinnedFingerprint,
            rememberPassword = profile.rememberPassword,
            readOnly = profile.readOnly,
            kerberosRealm = profile.kerberosRealm,
            kerberosKdcHost = profile.kerberosKdcHost,
            kerberosKdcPort = profile.kerberosKdcPort,
            kerberosServicePrincipal = profile.kerberosServicePrincipal,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt,
            lastSuccessfulConnectionAt = profile.lastSuccessfulConnectionAt,
        )
}
