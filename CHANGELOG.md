# Changelog

All notable changes to this project will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [0.1.2] — 2026-08-15

Generic LDAP workflows are usable alongside Active Directory, user copy respects Samba/POSIX when present, and the app ships with the LDAPADvisor brand assets.

### Added

- Generic LDAP search presets and shared directory base-DN resolution (`LdapSearchPresets`, `DirectoryBaseDns`).
- OU tree picker for create/copy destination and object move (no free-typed parent DN).
- LDAP user copy as `inetOrgPerson` (uid/CN RDN matching the source); optional Password Modify when supported.
- Conditional Samba extras on copy (`sambaSamAccount`, fresh `sambaSID`, path attrs only if present on source).
- Conditional RFC 2307 `posixAccount` / `shadowAccount` on copy (new `uidNumber`; never copy password/shadow hashes).
- Optional inetOrgPerson contact extras on copy when present (`homePhone`, `pager`, `employeeNumber`, `o`, …).
- Distinct AD country fields `c` / `co` / `countryCode` in user detail, object details, and copy.
- App branding: adaptive launcher icon, in-app logo mark / wordmark (About, dashboard, empty profiles), README wordmark.
- Password set/reset allowed over Kerberos SASL bind (in addition to LDAPS / StartTLS).

### Changed

- Users / Groups / directory browse enabled for generic LDAP sessions (AD-only gates removed where appropriate).
- Dashboard hides forest/domain/DC/GC and computer search when not Active Directory.
- Create group remains AD-only; create OU works for generic LDAP.
- CN=Users and other well-known containers classify as expandable containers in the explorer and OU picker.
- Primary contact attributes enriched on user detail and object details (address, company, initials, website, description).
- `displayName` edits no longer sync `cn` / initials.

### Fixed

- Generic LDAP sessions no longer blocked from user/group search and directory root resolution.

## [0.1.1] — 2026-08-14

Kerberos SASL bind against Active Directory is now usable end-to-end, with faster connect and a session keep-alive fix after screen lock.

### Fixed

- Complete GSSAPI SASL security-layer negotiation after mutual AP-REP (RFC 1964 / RC4-HMAC Wrap checksum, little-endian usage salt).
- Prefer AP-REP subkey then ticket session key when unwrapping the server Wrap offer.
- Silent LDAP reconnect after keep-alive failure no longer cancels itself mid-connect (dashboard stuck on “Connecting…” after unlock).
- Skip public DNS SRV/A discovery when a Kerberos service principal is already configured (avoids multi-second REFUSED delays on phones using public resolvers).

### Changed

- A/AAAA lookups prefer the Android/system resolver (VPN / WireGuard DNS) instead of MiniDNS public resolvers.
- Bound SRV discovery timeouts when SPN auto-discovery is still needed.
- Guard against duplicate connect attempts after insecure-trust confirmation.

### Added

- Unit tests for RFC 1964 Wrap token round-trip (`GssWrapTokenRfc1964Test`).

## [0.1.0] — 2026-08-14

First public application snapshot after the Android Studio scaffold. Early development — not a production AD release.

### Added

- LDAP / LDAPS / StartTLS connectivity with connection profiles (Room + Keystore-backed secrets).
- Active Directory DNS SRV discovery, RootDSE inspection, directory browser, and advanced search.
- AD users, groups, computers, and OU workflows (browse, selected admin actions, copy user).
- Diagnostics (DNS, TCP, TLS, GC, user/computer), advisor findings, and HTML/JSON/TXT reports.
- Embedded Kerberos (Apache Kerby) SASL GSS-SPNEGO/GSSAPI bind support.
- Settings, favorites, search history, EN/FR i18n, Material 3 light/dark themes.
- Unit and smoke tests, security/privacy docs, Dependabot, third-party notices.

### Changed

- Stop tracking Android Studio `.idea/` in the public repository (local IDE config stays ignored).

### Security

- Explicit insecure TLS trust mode (`INSECURE_NO_VERIFY`) with confirmation; certificate validation remains the default path.
