# Security

## Threat posture

LDAPADvisor is a **local** admin toolkit. It talks only to LDAP/DNS endpoints you configure. There is no analytics, advertising, crash-upload cloud, or vendor backend.

## Credentials

- Passwords / secrets are stored through `SecretStore` → `AndroidKeystoreSecretStore`
- AES/GCM with a key in the **Android Keystore**; IV + ciphertext in private SharedPreferences (`ldapadvisor_secrets`)
- Secrets are not written into Room profile rows as plaintext

## Backup / extraction

- `android:allowBackup="false"`
- `fullBackupContent` / `dataExtractionRules` exclude root, files, databases, shared preferences, and external domains
- Intended to reduce accidental export of encrypted secrets and directory metadata

## TLS

- Prefer LDAPS or StartTLS for binds that carry passwords
- Cleartext LDAP bind requires an explicit confirmation dialog
- Trust modes: SYSTEM, CUSTOM_CA, PINNED, INSECURE_NO_VERIFY
- Hostname verification helpers; typed certificate errors
- Client certificates: unsupported
- `INSECURE_NO_VERIFY` skips cert/hostname checks; credential binds still require explicit confirmation — use only for lab or temporary troubleshooting, not as a lasting policy

## Logging and UI

- `LogSanitizer` redacts sensitive patterns from log lines
- Password reset UI enables `SecureWindow` (FLAG_SECURE) while the dialog is open
- Reports default to sanitization (settings toggle); passwords are never intended to appear in exports

## Network permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`
- `usesCleartextTraffic="true"` exists so plain LDAP (389) can work when explicitly chosen; still discourage for credentials

## Sharing

- Report files are written under app cache and shared with `FileProvider` (`${applicationId}.fileprovider`)
- Paths: `cache/reports/`, `cache/logs/`

## Operational guidance

- Use documentation-only sample domains in issues and tests (`corp.example.com`, RFC 5737 addresses)
- Never commit real credentials, private keys, keystores, or production directory dumps
- See [SECURITY.md](../SECURITY.md) for vulnerability reporting

Related: [PRIVACY.md](../PRIVACY.md), [limitations.md](limitations.md).
