# LDAPADvisor Android

Native Android toolkit for LDAP and Microsoft Active Directory administration, exploration and diagnostics.

> [!IMPORTANT]
> LDAPADvisor Android is currently in early development and is not yet intended for production Active Directory environments.

## About

LDAPADvisor Android is a future native Android application for:

- system administrators
- IT technicians
- network administrators
- Active Directory administrators
- LDAP administrators

The long-term goal is to enable the following directly from Android devices (roadmap only — not yet available):

- LDAP/LDAPS connectivity
- Active Directory discovery
- LDAP directory browsing
- user management
- group management
- computer inspection
- DNS diagnostics
- LDAP diagnostics
- LDAPS/TLS diagnostics
- Global Catalog diagnostics
- Active Directory diagnostics
- technical reports

These items describe intended capabilities. Do not treat them as shipped features.

## Planned Features

- [ ] LDAP connection profiles
- [ ] LDAP and LDAPS support
- [ ] StartTLS support
- [ ] Active Directory discovery through DNS SRV
- [ ] RootDSE inspection
- [ ] LDAP directory browser
- [ ] Advanced LDAP search
- [ ] Active Directory users
- [ ] Active Directory groups
- [ ] Active Directory computers
- [ ] Organizational Units
- [ ] Account unlock
- [ ] Account enable/disable
- [ ] Password reset over secure LDAP
- [ ] Group membership management
- [ ] DNS diagnostics
- [ ] TCP connectivity diagnostics
- [ ] TLS certificate diagnostics
- [ ] Global Catalog diagnostics
- [ ] Kerberos-related diagnostics
- [ ] Diagnostic reports

## Platform

- Android 7.0 (API 24) or later
- Native Kotlin application

## Technology

Currently used in this repository:

- Kotlin
- Jetpack Compose
- Material 3
- Gradle Kotlin DSL
- Android Gradle Plugin
- Gradle Wrapper

### Planned technical components

The following are **not** included yet and may be adopted later:

- LDAP client libraries
- DNS / SRV discovery helpers
- Secure local storage for credentials
- Structured diagnostics engine
- Report generation

## Project Status

**Early development**

This repository currently provides project foundation, documentation, and CI scaffolding. No stable release is announced.

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
- Android SDK with a platform matching the project's `compileSdk`

## Security

LDAPADvisor may eventually handle sensitive operational data, including:

- LDAP credentials
- Active Directory credentials
- directory information
- internal hostnames
- certificates

**Never commit real credentials, private certificates, signing keys or production Active Directory information to the repository.**

See [SECURITY.md](SECURITY.md) for vulnerability reporting guidance.

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
