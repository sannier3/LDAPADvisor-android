# Changelog

All notable changes to this project will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

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
