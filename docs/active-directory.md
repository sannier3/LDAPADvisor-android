# Active Directory

AD-aware features activate when RootDSE / capabilities indicate Active Directory (for example naming contexts and AD-specific attributes).

## Discovery

`AdDiscoveryService` (MiniDNS) resolves:

- `_ldap._tcp.dc._msdcs.<domain>`
- `_ldap._tcp.<domain>`
- `_gc._tcp.<domain>`
- `_kerberos._tcp.<domain>` / `_kerberos._udp.<domain>`

Results include hostname, port, priority/weight, and optional A/AAAA addresses. Profile create/edit can run discovery to populate DC candidates.

## Object screens

| Area | Status | Capabilities |
|---|---|---|
| Users | Implemented | Search, SID/GUID/UAC decode, unlock, enable/disable, `unicodePwd` reset (secure channel) |
| Groups | Implemented | Search, group type decode, members (partial list), add/remove member, nested members |
| Computers | Implemented | Search, OS / SPN inspection |
| OUs | Implemented | Browse via OU search preset |
| Create user / group / OU | **Limited** | Navigation routes show a limited-message screen only |

## Administration constraints

- Password reset encodes UTF-16LE quoted passwords for `unicodePwd` and requires LDAPS or StartTLS.
- Unlock clears `lockoutTime`.
- Enable/disable toggles `ADS_UF_ACCOUNTDISABLE` in `userAccountControl`.
- Mutations require a non-read-only session.

## Decoders / helpers

Unit-tested helpers under `core/ad`:

- `UserAccountControl`
- `SidDecoder` / `GuidDecoder`
- `FileTimeConverter`
- `GroupTypeDecoder`
- `FunctionalLevelDecoder`
- `TrustDecoders`
- `UnicodePwdEncoder`
- `AdSearchPresets`

## Unsupported AD auth

Kerberos ticket acquisition and SASL GSS-SPNEGO/GSSAPI bind are implemented with an **embedded Apache Kerby client** (not OS Kerberos). Configure Auth method = KERBEROS on the profile: principal, realm, optional KDC host/port and LDAP SPN (`ldap/<dc-fqdn>` by default).
