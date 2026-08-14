package com.jbsan.ldapadvisor.core.ad

/**
 * userAccountControl flag decoding.
 *
 * Prefer msDS-User-Account-Control-Computed for LOCKOUT and PASSWORD_EXPIRED when available.
 */
object UserAccountControl {
    const val SCRIPT = 0x0001
    const val ACCOUNTDISABLE = 0x0002
    const val HOMEDIR_REQUIRED = 0x0008
    const val LOCKOUT = 0x0010
    const val PASSWD_NOTREQD = 0x0020
    const val PASSWD_CANT_CHANGE = 0x0040
    const val ENCRYPTED_TEXT_PWD_ALLOWED = 0x0080
    const val TEMP_DUPLICATE_ACCOUNT = 0x0100
    const val NORMAL_ACCOUNT = 0x0200
    const val INTERDOMAIN_TRUST_ACCOUNT = 0x0800
    const val WORKSTATION_TRUST_ACCOUNT = 0x1000
    const val SERVER_TRUST_ACCOUNT = 0x2000
    const val DONT_EXPIRE_PASSWORD = 0x10000
    const val MNS_LOGON_ACCOUNT = 0x20000
    const val SMARTCARD_REQUIRED = 0x40000
    const val TRUSTED_FOR_DELEGATION = 0x80000
    const val NOT_DELEGATED = 0x100000
    const val USE_DES_KEY_ONLY = 0x200000
    const val DONT_REQUIRE_PREAUTH = 0x400000
    const val PASSWORD_EXPIRED = 0x800000
    const val TRUSTED_TO_AUTH_FOR_DELEGATION = 0x1000000
    const val PARTIAL_SECRETS_ACCOUNT = 0x04000000

    private val FLAG_NAMES = linkedMapOf(
        SCRIPT to "SCRIPT",
        ACCOUNTDISABLE to "ACCOUNTDISABLE",
        HOMEDIR_REQUIRED to "HOMEDIR_REQUIRED",
        LOCKOUT to "LOCKOUT",
        PASSWD_NOTREQD to "PASSWD_NOTREQD",
        PASSWD_CANT_CHANGE to "PASSWD_CANT_CHANGE",
        ENCRYPTED_TEXT_PWD_ALLOWED to "ENCRYPTED_TEXT_PWD_ALLOWED",
        TEMP_DUPLICATE_ACCOUNT to "TEMP_DUPLICATE_ACCOUNT",
        NORMAL_ACCOUNT to "NORMAL_ACCOUNT",
        INTERDOMAIN_TRUST_ACCOUNT to "INTERDOMAIN_TRUST_ACCOUNT",
        WORKSTATION_TRUST_ACCOUNT to "WORKSTATION_TRUST_ACCOUNT",
        SERVER_TRUST_ACCOUNT to "SERVER_TRUST_ACCOUNT",
        DONT_EXPIRE_PASSWORD to "DONT_EXPIRE_PASSWORD",
        MNS_LOGON_ACCOUNT to "MNS_LOGON_ACCOUNT",
        SMARTCARD_REQUIRED to "SMARTCARD_REQUIRED",
        TRUSTED_FOR_DELEGATION to "TRUSTED_FOR_DELEGATION",
        NOT_DELEGATED to "NOT_DELEGATED",
        USE_DES_KEY_ONLY to "USE_DES_KEY_ONLY",
        DONT_REQUIRE_PREAUTH to "DONT_REQUIRE_PREAUTH",
        PASSWORD_EXPIRED to "PASSWORD_EXPIRED",
        TRUSTED_TO_AUTH_FOR_DELEGATION to "TRUSTED_TO_AUTH_FOR_DELEGATION",
        PARTIAL_SECRETS_ACCOUNT to "PARTIAL_SECRETS_ACCOUNT",
    )

    data class Decoded(
        val raw: Int,
        val flags: Set<String>,
        val enabled: Boolean,
        val passwordNeverExpires: Boolean,
        val normalAccount: Boolean,
    )

    fun decode(raw: Int): Decoded {
        val flags = FLAG_NAMES.filter { (bit, _) -> raw and bit != 0 }.values.toSet()
        return Decoded(
            raw = raw,
            flags = flags,
            enabled = raw and ACCOUNTDISABLE == 0,
            passwordNeverExpires = raw and DONT_EXPIRE_PASSWORD != 0,
            normalAccount = raw and NORMAL_ACCOUNT != 0,
        )
    }

    fun decode(raw: String?): Decoded? =
        raw?.trim()?.toIntOrNull()?.let { decode(it) }

    fun withFlag(raw: Int, flag: Int, enabled: Boolean): Int =
        if (enabled) raw or flag else raw and flag.inv()

    fun setDisabled(raw: Int, disabled: Boolean): Int =
        withFlag(raw, ACCOUNTDISABLE, disabled)
}
