package com.jbsan.ldapadvisor.core.security

/**
 * Secure storage for secrets (LDAP passwords). Never store plaintext in Room/DataStore.
 */
interface SecretStore {
    fun saveSecret(key: String, plaintext: ByteArray)
    fun getSecret(key: String): ByteArray?
    fun deleteSecret(key: String)
    fun contains(key: String): Boolean
    fun clearAll()

    fun savePassword(profileId: String, password: CharArray) {
        val bytes = password.concatToString().toByteArray(Charsets.UTF_8)
        try {
            saveSecret(passwordKey(profileId), bytes)
        } finally {
            bytes.fill(0)
            password.fill('\u0000')
        }
    }

    fun getPassword(profileId: String): CharArray? {
        val bytes = getSecret(passwordKey(profileId)) ?: return null
        return try {
            String(bytes, Charsets.UTF_8).toCharArray()
        } finally {
            bytes.fill(0)
        }
    }

    fun deletePassword(profileId: String) = deleteSecret(passwordKey(profileId))

    companion object {
        fun passwordKey(profileId: String): String = "profile-password:$profileId"
    }
}
