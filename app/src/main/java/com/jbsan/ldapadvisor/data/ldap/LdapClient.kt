package com.jbsan.ldapadvisor.data.ldap

import com.jbsan.ldapadvisor.core.ad.UnicodePwdEncoder
import com.jbsan.ldapadvisor.core.logging.AppLogger
import com.jbsan.ldapadvisor.domain.model.AppError
import com.jbsan.ldapadvisor.domain.model.ConnectionProfile
import com.jbsan.ldapadvisor.domain.model.DirectoryCapabilities
import com.jbsan.ldapadvisor.domain.model.SecurityMode
import com.jbsan.ldapadvisor.domain.model.TrustMode
import com.unboundid.asn1.ASN1OctetString
import com.unboundid.ldap.sdk.ANONYMOUSBindRequest
import com.unboundid.ldap.sdk.AddRequest
import com.unboundid.ldap.sdk.Attribute
import com.unboundid.ldap.sdk.CompareRequest
import com.unboundid.ldap.sdk.DeleteRequest
import com.unboundid.ldap.sdk.Entry
import com.unboundid.ldap.sdk.Filter
import com.unboundid.ldap.sdk.GenericSASLBindRequest
import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.Modification
import com.unboundid.ldap.sdk.ModificationType
import com.unboundid.ldap.sdk.ModifyDNRequest
import com.unboundid.ldap.sdk.ModifyRequest
import com.jbsan.ldapadvisor.data.kerberos.KerberosMutualAuthState
import com.unboundid.ldap.sdk.ResultCode
import com.unboundid.ldap.sdk.RootDSE
import com.unboundid.ldap.sdk.SASLBindInProgressException
import com.unboundid.ldap.sdk.SearchRequest
import com.unboundid.ldap.sdk.SearchResult
import com.unboundid.ldap.sdk.SearchScope
import com.unboundid.ldap.sdk.SimpleBindRequest
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl
import com.unboundid.ldap.sdk.extensions.PasswordModifyExtendedRequest
import com.unboundid.ldap.sdk.schema.Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LdapClient(
    private val connection: LDAPConnection,
    private val profile: ConnectionProfile,
    private val tlsActive: Boolean,
    private val logger: AppLogger? = null,
    private var boundAs: String? = null,
) {
    val readOnly: Boolean get() = profile.readOnly
    val isTlsActive: Boolean get() = tlsActive
    val securityMode: SecurityMode get() = profile.securityMode
    val host: String get() = profile.host
    val port: Int get() = profile.port
    val profileId: String get() = profile.id

    fun isConnected(): Boolean = connection.isConnected

    /**
     * Lightweight liveness check used by session keep-alive.
     * Reads RootDSE with a short operation so idle firewalls/AD timeouts do not drop the bind.
     */
    suspend fun ping(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!connection.isConnected) {
                return@withContext Result.failure(AppError.NotConnected())
            }
            connection.rootDSE
                ?: return@withContext Result.failure(AppError.LdapUnavailable("Keep-alive RootDSE unavailable"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(LdapErrorMapper.map(e))
        }
    }

    fun boundIdentity(): String? = boundAs

    suspend fun bindAnonymous(): BindOutcome = withContext(Dispatchers.IO) {
        try {
            connection.bind(ANONYMOUSBindRequest())
            boundAs = null
            BindOutcome.Success(null)
        } catch (e: Exception) {
            BindOutcome.Failure(LdapErrorMapper.map(e))
        }
    }

    /**
     * Simple bind. Plaintext LDAP requires [allowPlaintextConfirmation]=true.
     * [TrustMode.INSECURE_NO_VERIFY] requires [allowInsecureTrustConfirmation]=true for credentials.
     */
    suspend fun bindSimple(
        bindDn: String,
        password: CharArray,
        allowPlaintextConfirmation: Boolean = false,
        allowInsecureTrustConfirmation: Boolean = false,
    ): BindOutcome = withContext(Dispatchers.IO) {
        try {
            if (profile.trustMode == TrustMode.INSECURE_NO_VERIFY && !allowInsecureTrustConfirmation) {
                return@withContext BindOutcome.RequiresInsecureTrustConfirmation(
                    "TLS certificate verification is disabled (INSECURE_NO_VERIFY). Confirm explicitly to send credentials.",
                )
            }
            val secure = tlsActive || profile.securityMode == SecurityMode.LDAPS
            if (!secure && password.isNotEmpty()) {
                if (!allowPlaintextConfirmation) {
                    return@withContext BindOutcome.RequiresPlaintextConfirmation(
                        "Simple bind would send credentials in plaintext over LDAP. Confirm explicitly to continue.",
                    )
                }
            }
            val request = SimpleBindRequest(bindDn, String(password))
            connection.bind(request)
            boundAs = bindDn
            BindOutcome.Success(bindDn)
        } catch (e: Exception) {
            BindOutcome.Failure(LdapErrorMapper.map(e))
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Kerberos SASL bind using pre-built GSS-SPNEGO / GSSAPI InitialContextTokens.
     * Prefers GSS-SPNEGO (Active Directory), falls back to GSSAPI.
     * Handles mutual-auth SASL continuation (AP-REP verify + empty second bind).
     */
    suspend fun bindKerberos(
        principalLabel: String,
        spnegoToken: ByteArray,
        gssapiToken: ByteArray,
        mutualAuth: KerberosMutualAuthState? = null,
        allowInsecureTrustConfirmation: Boolean = false,
    ): BindOutcome = withContext(Dispatchers.IO) {
        try {
            if (profile.trustMode == TrustMode.INSECURE_NO_VERIFY && !allowInsecureTrustConfirmation) {
                return@withContext BindOutcome.RequiresInsecureTrustConfirmation(
                    "TLS certificate verification is disabled (INSECURE_NO_VERIFY). Confirm explicitly to send credentials.",
                )
            }
            val attempts = listOf(
                // GSSAPI is the path that reaches mutual AP-REP on AD; try it first.
                "GSSAPI" to gssapiToken,
                "GSS-SPNEGO" to spnegoToken,
            )
            var lastError: AppError? = null
            for ((mech, token) in attempts) {
                try {
                    logger?.debug("LdapClient", "SASL bind attempt mech=$mech tokenBytes=${token.size}")
                    val result = connection.bind(
                        GenericSASLBindRequest(
                            "",
                            mech,
                            ASN1OctetString(token),
                        ),
                    )
                    if (result.resultCode == ResultCode.SUCCESS) {
                        boundAs = principalLabel
                        logger?.debug("LdapClient", "SASL bind success mech=$mech as=$principalLabel")
                        return@withContext BindOutcome.Success(principalLabel)
                    }
                    lastError = AppError.KerberosFailure(
                        message = result.diagnosticMessage
                            ?: "Kerberos SASL bind failed ($mech): ${result.resultCode}",
                        technicalDetails = result.toString(),
                    )
                    logger?.debug(
                        "LdapClient",
                        "SASL bind rejected mech=$mech code=${result.resultCode} diag=${result.diagnosticMessage}",
                    )
                } catch (e: SASLBindInProgressException) {
                    val outcome = completeKerberosSaslContinuation(
                        mech = mech,
                        principalLabel = principalLabel,
                        firstException = e,
                        mutualAuth = mutualAuth,
                    )
                    when (outcome) {
                        is BindOutcome.Success -> return@withContext outcome
                        is BindOutcome.Failure -> {
                            // Connection is mid-SASL; do not try another mech on the same socket.
                            return@withContext outcome
                        }
                        else -> return@withContext BindOutcome.Failure(
                            AppError.KerberosFailure("Kerberos SASL continuation failed ($mech)"),
                        )
                    }
                } catch (e: Exception) {
                    lastError = when (val mapped = LdapErrorMapper.map(e)) {
                        is AppError.LdapInvalidCredentials -> AppError.KerberosFailure(
                            message = mapSaslRejectMessage(mech, mapped),
                            technicalDetails = mapped.technicalDetails ?: mapped.message,
                        )
                        else -> mapped
                    }
                    logger?.debug("LdapClient", "SASL bind exception mech=$mech: ${e.message}")
                }
            }
            BindOutcome.Failure(
                lastError ?: AppError.KerberosFailure("Kerberos SASL bind failed"),
            )
        } finally {
            spnegoToken.fill(0)
            gssapiToken.fill(0)
            mutualAuth?.clear()
        }
    }

    /**
     * RFC 4752 continuation after initial AP-REQ:
     * 1) verify AP-REP
     * 2) empty bind → server wrap offer (4 bytes)
     * 3) wrap "no security layer" response → final SUCCESS
     */
    private fun completeKerberosSaslContinuation(
        mech: String,
        principalLabel: String,
        firstException: SASLBindInProgressException,
        mutualAuth: KerberosMutualAuthState?,
    ): BindOutcome {
        val serverCreds = firstException.serverSASLCredentials?.value
        logger?.debug(
            "LdapClient",
            "SASL_BIND_IN_PROGRESS mech=$mech serverTokenBytes=${serverCreds?.size ?: 0}",
        )
        if (mutualAuth == null) {
            return BindOutcome.Failure(
                AppError.KerberosFailure(
                    message = "Kerberos SASL mutual authentication continuation required ($mech)",
                    technicalDetails = firstException.diagnosticMessage,
                ),
            )
        }
        if (serverCreds == null || serverCreds.isEmpty()) {
            return BindOutcome.Failure(
                AppError.KerberosFailure(
                    message = "Kerberos SASL mutual auth: empty server credentials ($mech)",
                    technicalDetails = firstException.diagnosticMessage,
                ),
            )
        }
        try {
            mutualAuth.verifyServerSaslCredentials(serverCreds)
            logger?.debug("LdapClient", "AP-REP verified mech=$mech — empty second SASL bind")
        } catch (verifyEx: Exception) {
            logger?.debug("LdapClient", "AP-REP verify failed mech=$mech: ${verifyEx.message}")
            return BindOutcome.Failure(
                AppError.KerberosFailure(
                    message = "Kerberos mutual auth AP-REP verification failed ($mech)",
                    technicalDetails = verifyEx.message ?: verifyEx.toString(),
                ),
            )
        }

        val secondCreds: ByteArray? = try {
            val second = connection.bind(
                GenericSASLBindRequest("", mech, ASN1OctetString(ByteArray(0))),
            )
            if (second.resultCode == ResultCode.SUCCESS) {
                boundAs = principalLabel
                logger?.debug("LdapClient", "SASL bind success after empty second bind mech=$mech")
                return BindOutcome.Success(principalLabel)
            }
            null
        } catch (e2: SASLBindInProgressException) {
            e2.serverSASLCredentials?.value
        } catch (e2: Exception) {
            return BindOutcome.Failure(
                AppError.KerberosFailure(
                    message = "Kerberos SASL second bind failed ($mech)",
                    technicalDetails = e2.message ?: e2.toString(),
                ),
            )
        }

        if (secondCreds == null || secondCreds.isEmpty()) {
            return BindOutcome.Failure(
                AppError.KerberosFailure(
                    message = "Kerberos SASL security-layer offer missing after mutual auth ($mech)",
                ),
            )
        }
        logger?.debug(
            "LdapClient",
            "SASL security-layer offer mech=$mech wrapBytes=${secondCreds.size} " +
                "keys=${mutualAuth.wrapKeyTypeLabel()}",
        )

        val wrapResponse = try {
            mutualAuth.buildSaslNoSecurityLayerResponse(secondCreds)
        } catch (wrapEx: Exception) {
            logger?.debug("LdapClient", "SASL wrap response failed mech=$mech: ${wrapEx.message}")
            return BindOutcome.Failure(
                AppError.KerberosFailure(
                    message = "Kerberos SASL security-layer negotiation failed ($mech)",
                    technicalDetails = wrapEx.message ?: wrapEx.toString(),
                ),
            )
        }
        logger?.debug(
            "LdapClient",
            "SASL wrap response ready mech=$mech tokenBytes=${wrapResponse.size}",
        )

        return try {
            val finalResult = connection.bind(
                GenericSASLBindRequest("", mech, ASN1OctetString(wrapResponse)),
            )
            if (finalResult.resultCode == ResultCode.SUCCESS) {
                boundAs = principalLabel
                logger?.debug(
                    "LdapClient",
                    "SASL bind success after security-layer nego mech=$mech as=$principalLabel",
                )
                BindOutcome.Success(principalLabel)
            } else {
                BindOutcome.Failure(
                    AppError.KerberosFailure(
                        message = finalResult.diagnosticMessage
                            ?: "Kerberos SASL final bind failed ($mech): ${finalResult.resultCode}",
                        technicalDetails = finalResult.toString(),
                    ),
                )
            }
        } catch (e3: Exception) {
            val mapped = LdapErrorMapper.map(e3)
            BindOutcome.Failure(
                when (mapped) {
                    is AppError.LdapInvalidCredentials -> AppError.KerberosFailure(
                        message = mapSaslRejectMessage(mech, mapped),
                        technicalDetails = mapped.technicalDetails ?: mapped.message,
                    )
                    else -> AppError.KerberosFailure(
                        message = "Kerberos SASL final bind failed ($mech)",
                        technicalDetails = e3.message ?: mapped.toString(),
                    )
                },
            )
        }
    }

    private fun mapSaslRejectMessage(mech: String, mapped: AppError.LdapInvalidCredentials): String {
        val details = (mapped.technicalDetails ?: mapped.message).orEmpty()
        val data = Regex("""data\s+([0-9a-fA-F]+)""", RegexOption.IGNORE_CASE)
            .find(details)?.groupValues?.getOrNull(1)?.lowercase()
        val hint = when (data) {
            "57" -> " (data 57: often wrong APOptions/SPN for this DC — prefer ldap/<fqdn> matching the LDAP host)"
            "52e" -> " (data 52e: logon failure / ticket not accepted by this DC)"
            "80090346", "775" -> " (channel binding / LDAP signing policy may block this client)"
            else -> if (data != null) " (data $data)" else ""
        }
        return "Kerberos SASL bind rejected ($mech)$hint"
    }

    suspend fun readRootDse(): Result<RootDseInfo> = withContext(Dispatchers.IO) {
        try {
            val root = connection.rootDSE ?: return@withContext Result.failure(
                AppError.LdapUnavailable("RootDSE unavailable"),
            )
            Result.success(mapRootDse(root))
        } catch (e: Exception) {
            Result.failure(LdapErrorMapper.map(e))
        }
    }

    suspend fun getSchema(): Result<Schema> = withContext(Dispatchers.IO) {
        try {
            val schema = connection.schema
                ?: return@withContext Result.failure(AppError.Generic("Schema not available from server"))
            Result.success(schema)
        } catch (e: Exception) {
            Result.failure(LdapErrorMapper.map(e))
        }
    }

    suspend fun search(request: LdapSearchRequest): Result<List<LdapEntryData>> = withContext(Dispatchers.IO) {
        try {
            Filter.create(request.filter)
            val scope = when (request.scope) {
                SearchScopeMode.BASE -> SearchScope.BASE
                SearchScopeMode.ONE -> SearchScope.ONE
                SearchScopeMode.SUB -> SearchScope.SUB
            }
            if (request.pageSize != null && request.pageSize > 0) {
                return@withContext pagedSearch(request, scope)
            }
            val sr = SearchRequest(
                request.baseDn,
                scope,
                request.filter,
                *(request.attributes ?: arrayOf("*", "+")),
            )
            if (request.sizeLimit > 0) sr.sizeLimit = request.sizeLimit
            if (request.timeLimitSeconds > 0) sr.timeLimitSeconds = request.timeLimitSeconds
            val result = connection.search(sr)
            Result.success(result.searchEntries.map { mapEntry(it) })
        } catch (e: Exception) {
            Result.failure(LdapErrorMapper.map(e))
        }
    }

    private fun pagedSearch(request: LdapSearchRequest, scope: SearchScope): Result<List<LdapEntryData>> {
        val collected = mutableListOf<LdapEntryData>()
        var cookie: ASN1OctetString? = null
        do {
            val sr = SearchRequest(
                request.baseDn,
                scope,
                request.filter,
                *(request.attributes ?: arrayOf("*", "+")),
            )
            if (request.sizeLimit > 0) sr.sizeLimit = request.sizeLimit
            if (request.timeLimitSeconds > 0) sr.timeLimitSeconds = request.timeLimitSeconds
            sr.addControl(
                if (cookie == null) {
                    SimplePagedResultsControl(request.pageSize!!)
                } else {
                    SimplePagedResultsControl(request.pageSize!!, cookie)
                },
            )
            val result: SearchResult = connection.search(sr)
            collected += result.searchEntries.map { mapEntry(it) }
            val control = SimplePagedResultsControl.get(result)
            cookie = if (control != null && control.moreResultsToReturn()) control.cookie else null
        } while (cookie != null && cookie.value.isNotEmpty())
        return Result.success(collected)
    }

    suspend fun compare(dn: String, attribute: String, assertionValue: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val result = connection.compare(CompareRequest(dn, attribute, assertionValue))
                Result.success(result.compareMatched())
            } catch (e: Exception) {
                Result.failure(LdapErrorMapper.map(e))
            }
        }

    suspend fun add(dn: String, attributes: Map<String, List<String>>): Result<Unit> =
        withContext(Dispatchers.IO) {
            enforceWritable().getOrElse { return@withContext Result.failure(it) }
            try {
                val attrs = attributes.map { (k, values) -> Attribute(k, values) }
                connection.add(AddRequest(dn, attrs))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(LdapErrorMapper.map(e))
            }
        }

    suspend fun modify(dn: String, modifications: List<LdapModificationSpec>): Result<Unit> =
        withContext(Dispatchers.IO) {
            enforceWritable().getOrElse { return@withContext Result.failure(it) }
            try {
                val mods = modifications.map { spec ->
                    val type = when (spec.type) {
                        LdapModificationSpec.Type.ADD -> ModificationType.ADD
                        LdapModificationSpec.Type.DELETE -> ModificationType.DELETE
                        LdapModificationSpec.Type.REPLACE -> ModificationType.REPLACE
                    }
                    if (spec.values.isEmpty()) {
                        Modification(type, spec.attribute)
                    } else {
                        Modification(type, spec.attribute, *spec.values.toTypedArray())
                    }
                }
                connection.modify(ModifyRequest(dn, mods))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(LdapErrorMapper.map(e))
            }
        }

    suspend fun delete(dn: String): Result<Unit> = withContext(Dispatchers.IO) {
        enforceWritable().getOrElse { return@withContext Result.failure(it) }
        try {
            connection.delete(DeleteRequest(dn))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(LdapErrorMapper.map(e))
        }
    }

    suspend fun modifyDn(
        dn: String,
        newRdn: String,
        deleteOldRdn: Boolean = true,
        newSuperior: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        enforceWritable().getOrElse { return@withContext Result.failure(it) }
        try {
            val request = if (newSuperior == null) {
                ModifyDNRequest(dn, newRdn, deleteOldRdn)
            } else {
                ModifyDNRequest(dn, newRdn, deleteOldRdn, newSuperior)
            }
            connection.modifyDN(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(LdapErrorMapper.map(e))
        }
    }

    /**
     * AD unicodePwd password reset — only when LDAPS or StartTLS is active.
     */
    suspend fun resetAdPassword(userDn: String, newPassword: CharArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            enforceWritable().getOrElse { return@withContext Result.failure(it) }
            if (!secureChannelActive()) {
                return@withContext Result.failure(AppError.SecureChannelRequired())
            }
            try {
                val encoded = UnicodePwdEncoder.encode(newPassword)
                connection.modify(
                    ModifyRequest(
                        userDn,
                        Modification(ModificationType.REPLACE, "unicodePwd", encoded),
                    ),
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(LdapErrorMapper.map(e))
            } finally {
                newPassword.fill('\u0000')
            }
        }

    /**
     * RFC 3062 Password Modify extended operation (OID 1.3.6.1.4.1.4203.1.11.1).
     * Requires a secure channel. Never logs password values.
     */
    suspend fun changePasswordPasswordModify(
        userIdentity: String?,
        oldPassword: CharArray?,
        newPassword: CharArray,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        enforceWritable().getOrElse { return@withContext Result.failure(it) }
        if (!secureChannelActive()) {
            return@withContext Result.failure(AppError.SecureChannelRequired())
        }
        try {
            val old = oldPassword?.let { String(it) }
            val newPwd = String(newPassword)
            val request = PasswordModifyExtendedRequest(userIdentity, old, newPwd)
            val result = connection.processExtendedOperation(request)
            if (result.resultCode == ResultCode.SUCCESS) {
                Result.success(Unit)
            } else {
                Result.failure(
                    AppError.Generic(
                        message = "Password Modify failed: ${result.resultCode} ${result.diagnosticMessage.orEmpty()}".trim(),
                        technicalDetails = result.diagnosticMessage,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(LdapErrorMapper.map(e))
        } finally {
            oldPassword?.fill('\u0000')
            newPassword.fill('\u0000')
        }
    }

    private fun secureChannelActive(): Boolean =
        tlsActive || profile.securityMode == SecurityMode.LDAPS

    suspend fun unlockAdUser(userDn: String): Result<Unit> =
        modify(
            userDn,
            listOf(
                LdapModificationSpec(
                    attribute = "lockoutTime",
                    type = LdapModificationSpec.Type.REPLACE,
                    values = listOf("0".toByteArray()),
                ),
            ),
        )

    suspend fun setAdAccountDisabled(userDn: String, disabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            enforceWritable().getOrElse { return@withContext Result.failure(it) }
            try {
                val entry = search(
                    LdapSearchRequest(
                        baseDn = userDn,
                        filter = "(objectClass=*)",
                        scope = SearchScopeMode.BASE,
                        attributes = arrayOf("userAccountControl"),
                    ),
                ).getOrElse { return@withContext Result.failure(it) }.firstOrNull()
                    ?: return@withContext Result.failure(AppError.LdapObjectNotFound())
                val raw = entry.firstString("userAccountControl")?.toIntOrNull()
                    ?: return@withContext Result.failure(AppError.Generic("userAccountControl missing"))
                val next = com.jbsan.ldapadvisor.core.ad.UserAccountControl.setDisabled(raw, disabled)
                modify(
                    userDn,
                    listOf(
                        LdapModificationSpec(
                            attribute = "userAccountControl",
                            type = LdapModificationSpec.Type.REPLACE,
                            values = listOf(next.toString().toByteArray()),
                        ),
                    ),
                )
            } catch (e: Exception) {
                Result.failure(LdapErrorMapper.map(e))
            }
        }

    /**
     * Active Directory range retrieval for multi-valued attributes such as member.
     */
    suspend fun readRangedAttribute(
        dn: String,
        attribute: String = "member",
        pageSize: Int = 1500,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val all = mutableListOf<String>()
            var start = 0
            while (true) {
                val end = start + pageSize - 1
                val rangedName = "$attribute;range=$start-$end"
                val entry = search(
                    LdapSearchRequest(
                        baseDn = dn,
                        filter = "(objectClass=*)",
                        scope = SearchScopeMode.BASE,
                        attributes = arrayOf(rangedName, attribute),
                    ),
                ).getOrElse { return@withContext Result.failure(it) }.firstOrNull()
                    ?: break
                val rangedKey = entry.attributes.keys.firstOrNull {
                    it.startsWith("$attribute;range=", ignoreCase = true)
                }
                val values = when {
                    rangedKey != null -> entry.stringValues(rangedKey)
                    else -> entry.stringValues(attribute)
                }
                if (values.isEmpty()) break
                all += values
                val isLast = rangedKey?.contains("range=", ignoreCase = true) == true &&
                    rangedKey.substringAfterLast('-').equals("*", ignoreCase = true)
                if (rangedKey == null || isLast || values.size < pageSize) break
                start += values.size
            }
            Result.success(all)
        } catch (e: Exception) {
            Result.failure(LdapErrorMapper.map(e))
        }
    }

    suspend fun addGroupMember(groupDn: String, memberDn: String): Result<Unit> =
        modify(
            groupDn,
            listOf(
                LdapModificationSpec(
                    attribute = "member",
                    type = LdapModificationSpec.Type.ADD,
                    values = listOf(memberDn.toByteArray()),
                ),
            ),
        )

    suspend fun removeGroupMember(groupDn: String, memberDn: String): Result<Unit> =
        modify(
            groupDn,
            listOf(
                LdapModificationSpec(
                    attribute = "member",
                    type = LdapModificationSpec.Type.DELETE,
                    values = listOf(memberDn.toByteArray()),
                ),
            ),
        )

    /** AD LDAP_MATCHING_RULE_IN_CHAIN nested membership search. */
    suspend fun searchNestedGroupMembers(groupDn: String, baseDn: String): Result<List<LdapEntryData>> {
        val filter =
            "(memberOf:1.2.840.113556.1.4.1941:=${com.jbsan.ldapadvisor.core.util.LdapFilterEscaper.escapeFilterValue(groupDn)})"
        return search(
            LdapSearchRequest(
                baseDn = baseDn,
                filter = filter,
                scope = SearchScopeMode.SUB,
                attributes = arrayOf("cn", "sAMAccountName", "distinguishedName", "objectClass"),
                pageSize = 200,
            ),
        )
    }

    fun disconnect() {
        try {
            connection.close()
        } catch (e: Exception) {
            logger?.warning("LdapClient", "Error closing LDAP connection", e)
        }
    }

    fun sessionInfo(rootDse: RootDseInfo?, capabilities: DirectoryCapabilities): ActiveSessionInfo =
        ActiveSessionInfo(
            profileId = profile.id,
            host = profile.host,
            port = profile.port,
            securityMode = profile.securityMode,
            tlsActive = tlsActive,
            boundAs = boundAs,
            readOnly = profile.readOnly,
            rootDse = rootDse,
            capabilities = capabilities,
        )

    private fun enforceWritable(): Result<Unit> =
        if (readOnly) Result.failure(AppError.ReadOnlyViolation()) else Result.success(Unit)

    private fun mapEntry(entry: Entry): LdapEntryData {
        val attrs = linkedMapOf<String, List<ByteArray>>()
        for (attr in entry.attributes) {
            attrs[attr.name] = attr.valueByteArrays.toList()
        }
        return LdapEntryData(entry.dn, attrs)
    }

    private fun mapRootDse(root: RootDSE): RootDseInfo {
        val all = linkedMapOf<String, List<String>>()
        root.attributes.forEach { attr ->
            all[attr.name] = attr.values.toList()
        }
        fun first(name: String): String? =
            all.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()

        fun allValues(name: String): List<String> =
            all.entries.firstOrNull { it.key.equals(name, true) }?.value.orEmpty()

        return RootDseInfo(
            attributes = all,
            defaultNamingContext = first("defaultNamingContext"),
            rootDomainNamingContext = first("rootDomainNamingContext"),
            configurationNamingContext = first("configurationNamingContext"),
            schemaNamingContext = first("schemaNamingContext"),
            namingContexts = allValues("namingContexts"),
            dnsHostName = first("dnsHostName"),
            supportedLdapVersions = allValues("supportedLDAPVersion").mapNotNull { it.toIntOrNull() },
            supportedSaslMechanisms = allValues("supportedSASLMechanisms"),
            supportedCapabilities = allValues("supportedCapabilities"),
            supportedControls = allValues("supportedControl"),
            domainControllerFunctionality = first("domainControllerFunctionality"),
            domainFunctionality = first("domainFunctionality"),
            forestFunctionality = first("forestFunctionality"),
            isGlobalCatalogReady = first("isGlobalCatalogReady"),
            currentTime = first("currentTime"),
        )
    }
}
