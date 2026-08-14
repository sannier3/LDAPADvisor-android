# LDAPADvisor Android

Native Android toolkit for LDAP and Microsoft Active Directory administration, exploration and diagnostics.

> [!IMPORTANT]
> LDAPADvisor Android is currently in early development and is not yet intended for production Active Directory environments.

## About

LDAPADvisor Android is a native Android application for:

- system administrators
- IT technicians
- network administrators
- Active Directory administrators
- LDAP administrators

Current builds (`0.1.1`) include working LDAP/LDAPS/StartTLS connectivity, embedded Kerberos SASL GSSAPI bind against AD, AD discovery, directory browsing, selected AD administration actions, diagnostics, advisor findings, and report export. Treat results carefully: the project is still early, and some flows remain limited or unsupported (see [docs/implementation-status.md](docs/implementation-status.md) and [docs/limitations.md](docs/limitations.md)).

## Planned Features

- [x] LDAP connection profiles
- [x] LDAP and LDAPS support
- [x] StartTLS support
- [x] Active Directory discovery through DNS SRV
- [x] RootDSE inspection
- [x] LDAP directory browser
- [x] Advanced LDAP search
- [x] Active Directory users
- [x] Active Directory groups
- [x] Active Directory computers
- [x] Organizational Units
- [x] Account unlock
- [x] Account enable/disable
- [x] Password reset over secure LDAP
- [x] Group membership management
- [x] DNS diagnostics
- [x] TCP connectivity diagnostics
- [x] TLS certificate diagnostics
- [x] Global Catalog diagnostics
- [ ] Kerberos-related diagnostics
- [x] Diagnostic reports

Kerberos **authentication** and GSSAPI bind remain **unsupported** on Android in this app. DNS/TCP Kerberos probes exist inside the diagnostics engine, but the planned “Kerberos-related diagnostics” checkbox stays unchecked until that area is considered complete for operators.

Object creation UI (create user/group/OU) is **limited** (message screens only) and is not listed as complete above.

## Platform

- Android 7.0 (API 24) or later
- Native Kotlin application
- `compileSdk` / `targetSdk` 37 (`versionName` `0.1.1`)

## Technology

Currently used in this repository:

- Kotlin
- Jetpack Compose
- Material 3
- Gradle Kotlin DSL
- Android Gradle Plugin
- Gradle Wrapper
- UnboundID LDAP SDK
- MiniDNS (AD DNS SRV discovery)
- AndroidX Room + DataStore
- Android Keystore AES-GCM secret store
- Kotlin Coroutines + Serialization
- Core library desugaring (`java.time`)
- FileProvider report sharing
- Dependabot (weekly Gradle + GitHub Actions)

## Project Status

**Early development** (`0.1.1`)

Core LDAP/AD, diagnostics, advisor, and reporting paths are implemented in-tree. No stable production release is announced. See [docs/implementation-status.md](docs/implementation-status.md).

## Documentation

- [Implementation status](docs/implementation-status.md)
- [Architecture](docs/architecture.md)
- [Dependencies](docs/dependencies.md)
- [LDAP](docs/ldap.md)
- [Active Directory](docs/active-directory.md)
- [Diagnostics](docs/diagnostics.md)
- [Security](docs/security.md)
- [Reports](docs/reports.md)
- [Limitations](docs/limitations.md)
- [Testing](docs/testing.md)
- [Privacy](PRIVACY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
- [Roadmap](docs/roadmap.md)

## Building

### Linux / macOS

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

### Windows

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
```

## Development Environment

- Android Studio (current stable recommended)
- JDK matching the project Gradle toolchain (see `gradle/gradle-daemon-jvm.properties`)
- Android SDK with a platform matching the project's `compileSdk` (37 in this tree; SDK 36 may be absent locally)

## Security

LDAPADvisor handles sensitive operational data, including:

- LDAP credentials
- Active Directory credentials
- directory information
- internal hostnames
- certificates

**Never commit real credentials, private certificates, signing keys or production Active Directory information to the repository.**

See [SECURITY.md](SECURITY.md), [docs/security.md](docs/security.md), and [PRIVACY.md](PRIVACY.md).

## Test Data

Examples in docs, issues, and tests must use reserved documentation domains only:

```text
corp.example.com
dc01.corp.example.com
user@corp.example.com
DC=corp,DC=example,DC=com
```

Prefer RFC documentation IP ranges for sample addresses:

```text
192.0.2.0/24
198.51.100.0/24
203.0.113.0/24
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security Policy

See [SECURITY.md](SECURITY.md).

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
