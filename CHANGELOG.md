# Changelog

All notable changes to this project will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

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
