# LDAPADvisor Android — Implementation Status

Living document. Status values: `IMPLEMENTED` | `LIMITED` | `UNSUPPORTED`

## Current implementation checkpoint

- **Version:** `0.1.0` (`versionCode` 1)
- **Last completed phase:** Embedded Kerberos (Apache Kerby) + SASL GSS-SPNEGO/GSSAPI bind, persistent primary navigation, AD user profile edit, copy user, insecure TLS trust with confirm
- **Build target:** `minSdk` 24, `compileSdk` / `targetSdk` **37**
- **i18n:** English (`values`) + French (`values-fr`)
- **Room:** version **4** with migrations `1→2`, `2→3` (Kerberos fields), `3→4` (trust mode rename)

## Feature table

| Feature | Status | Tests | Notes |
|---|---|---|---|
| Project foundation / Compose navigation | IMPLEMENTED | androidTest smoke | `AppNavHost` + primary tabs |
| Theme System / Light / Dark | IMPLEMENTED | | DataStore-backed `ThemeMode` |
| i18n EN / FR | IMPLEMENTED | | `values` + `values-fr` |
| AppContainer DI | IMPLEMENTED | | `NetworkMonitor`, repositories, diagnostic services |
| Room persistence | IMPLEMENTED | | Profiles, CAs, trusted certs, diagnostic runs, report meta, favorites, search history |
| DataStore settings | IMPLEMENTED | | Theme, timeouts, read-only, sanitization, concurrency, retention, save search history |
| SecretStore (Keystore AES-GCM) | IMPLEMENTED | | |
| Connection profiles CRUD | IMPLEMENTED | | |
| Network awareness | IMPLEMENTED | | `NetworkMonitor` + session `networkLost` banner |
| LDAP / LDAPS / StartTLS | IMPLEMENTED | Partial unit | |
| Simple bind | IMPLEMENTED | | |
| Kerberos auth / SASL GSS bind | IMPLEMENTED | Unit (GSS/SPNEGO codec) | Embedded Apache Kerby + GSS-SPNEGO/GSSAPI; not OS Kerberos |
| RootDSE / Schema | IMPLEMENTED | | |
| Raw LDAP technician mode | IMPLEMENTED | | Search / BASE read / compare; mutations stay on Object Details |
| AD DNS discovery (SRV) | IMPLEMENTED | | Per-DC optional **Test TCP** after discover |
| Directory browser | IMPLEMENTED | | Tree + object details |
| Object details admin | IMPLEMENTED | | Delete / rename / move / modify attr / compare / favorite |
| Advanced LDAP search | IMPLEMENTED | | Presets + history + TXT export (FileProvider, no passwords) |
| Paged search / range retrieval | IMPLEMENTED | | |
| LDAP mutations (add/modify/delete/modifyDN/compare) | IMPLEMENTED | | Gated by read-only |
| AD users browse / inspect / actions | IMPLEMENTED | Unit (UAC, SID, GUID, FileTime, RID) | Unlock / enable / disable / unicodePwd reset |
| AD user profile edit | IMPLEMENTED | Unit (RID extract) | Name, contact, address, title, groups, primary group, must-change / never-expire |
| Primary nav always visible | IMPLEMENTED | | Bottom bar/rail stays after Dashboard quick actions; `navigatePrimary` pops secondary stack |
| Generic Password Modify (RFC 3062) | IMPLEMENTED | Unit (SecureChannelRequired) | Non-AD when RootDSE advertises OID; TLS required |
| AD groups browse / membership | IMPLEMENTED | Unit (group type) | Add/remove with confirm + nested load |
| AD computers browse | IMPLEMENTED | | Detail, copy hostname, jump to computer diagnostic |
| Organizational Units browse | IMPLEMENTED | | Create OU entry point |
| Create user / group / OU UI | IMPLEMENTED | Unit (DN helpers) | Real `LdapClient.add` + AD password/UAC sequence |
| Account unlock / enable / disable | IMPLEMENTED | Unit (UAC) | |
| Password reset (`unicodePwd`) | IMPLEMENTED | Unit (encoder + secure channel) | Requires secure channel |
| Custom CA trust (engine) | IMPLEMENTED | | |
| Custom CA file picker UI | IMPLEMENTED | Unit (private-key reject) | SAF import via `GetContent`; reject PEM private keys |
| Certificate pinning / trust modes | IMPLEMENTED | | Apply pin from TLS diagnostic fingerprint |
| Client TLS certificates | UNSUPPORTED | | |
| DNS / TCP / TLS / GC diagnostics | IMPLEMENTED | | Score: avg SUCCESS=100, WARNING=60, ERROR=0 |
| User diagnostic screen | IMPLEMENTED | | Honest wording; never claims password valid |
| Computer diagnostic screen | IMPLEMENTED | | LDAP + DNS A/AAAA/PTR + TCP 445/135/5985/5986 |
| FSMO / trusts / password policy read | IMPLEMENTED | Unit | |
| Kerberos auth / GSSAPI bind | IMPLEMENTED | Unit (token codec) | Kerby client + SASL; mutual-auth continuation LIMITED |
| Favorites + search history | IMPLEMENTED | Unit (filter guard) | Room v2 |
| Log export (sanitized) | IMPLEMENTED | | FileProvider `logs/` + warning dialog |
| Licenses screen | IMPLEMENTED | | Assets excerpt of third-party notices |
| AdvisorEngine | IMPLEMENTED | Unit | password never expires + incomplete TLS chain |
| Reports HTML / JSON / TXT | IMPLEMENTED | Unit | |
| FileProvider | IMPLEMENTED | | Reports + logs + search exports |
| Firebase / analytics / ads / cloud | UNSUPPORTED | | Intentionally absent |

## SDK note

Local Android SDK platforms installed: `android-37.0` (and related). Target objective was API 36; project keeps `compileSdk` / `targetSdk` **37** because SDK 36 is not installed.
