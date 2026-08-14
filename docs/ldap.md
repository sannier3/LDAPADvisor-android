# LDAP

LDAPADvisor uses the **UnboundID LDAP SDK** (`com.unboundid:unboundid-ldapsdk`) through `LdapClient` / `LdapClientFactory` / `SessionManager`.

## Security modes

| Mode | Port (default) | Behavior |
|---|---|---|
| `LDAP` | 389 | Cleartext LDAP; credential bind requires explicit plaintext confirmation |
| `LDAPS` | 636 | TLS from connect |
| `START_TLS` | 389 | StartTLS upgrade before bind when negotiated |

Global Catalog ports `3268` / `3269` are recognized for diagnostics and profile targeting.

## Authentication

| Mechanism | Status |
|---|---|
| Anonymous bind | Implemented |
| Simple bind (DN / password) | Implemented |
| UPN-style identity in profiles | Implemented (sent as simple bind identity) |
| Kerberos | **Implemented** (embedded Apache Kerby client) |
| GSSAPI / SASL GSS-SPNEGO | **Implemented** (token built in-app; UnboundID JAAS GSSAPIBindRequest not used) |

## Operations (engine)

Implemented in `LdapClient`:

- Connect / disconnect, session info
- Bind anonymous / simple
- RootDSE read → `DirectoryCapabilities`
- Schema fetch
- Search (base / one / subtree) with optional paging
- Compare
- Add / modify / delete / modify DN
- AD helpers: `unicodePwd` reset, unlock (`lockoutTime`), UAC enable/disable
- Range retrieval for large multi-valued attributes
- Group member add / remove; nested member search (`LDAP_MATCHING_RULE_IN_CHAIN` style)

Writable operations respect profile / settings **read-only** gating.

## Trust modes (TLS)

Configured per profile via `TrustMode`:

| Mode | Behavior |
|---|---|
| `SYSTEM` | Platform trust store |
| `CUSTOM_CA` | Trust additional CA(s) from Room (`CustomCaEntity`) |
| `PINNED` | Require leaf SHA-256 pin |
| `INSECURE_NO_VERIFY` | Skip certificate and hostname verification (lab / temporary). Credential binds require an explicit confirmation dialog. Prefer CUSTOM_CA or PINNED. |

**Client certificates** are not supported.

Custom CA **import UI** is limited: the profile editor accepts a Custom CA **ID** string; there is no file-picker screen yet. Persistence and TLS loading paths exist when a CA row is already present.

## Error mapping

`LdapErrorMapper` maps SSL / certificate / LDAP exceptions into typed `AppError` values (expired cert, hostname mismatch, invalid credentials, etc.) for UI and diagnostics.
