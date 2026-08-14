# Architecture

This document reflects the **current** single-module implementation (version `0.1.0`). It will evolve; it is not a frozen API contract.

## Current state

LDAPADvisor Android is a native Kotlin app with:

- Jetpack Compose + Material 3 UI
- Manual DI via `AppContainer`
- Room + DataStore persistence
- UnboundID LDAP engine
- MiniDNS AD discovery
- DiagnosticEngine + AdvisorEngine + report export
- Android Keystore–backed `SecretStore`

## Guiding principles

- **Single-activity** Compose application (`MainActivity`)
- **MVVM** with `StateFlow` UI state
- **Repositories** for profiles, settings, history
- Specialized **services** for LDAP, DNS, TLS, diagnostics, reports
- Kotlin **coroutines** / `Dispatchers.IO` for network and directory I/O
- Clear separation: `feature` (UI) → `domain` → `data` / `core`
- **No** LDAP/AD data sent to third-party analytics backends

## Layering

```text
UI (Compose screens / ViewModels)
        ↓
Domain models + AdvisorEngine
        ↓
Repositories / SessionManager
        ↓
LdapClientFactory, DnsResolver, DiagnosticEngine, ReportGenerator, SecretStore, Room, DataStore
```

## Package map (high level)

| Package | Role |
|---|---|
| `app` | `LdapAdvisorApp`, `AppContainer` |
| `feature.*` | Screens + ViewModels (dashboard, profiles, directory, search, users/groups/computers, diagnostics, advisor, reports, settings, connection) |
| `domain.model` / `domain.service` | Domain types, advisor rules |
| `data.ldap` | UnboundID client, session, errors |
| `data.dns` | MiniDNS resolver + AD discovery |
| `data.tls` | Trust modes, SSL factories, TLS diagnostics |
| `data.diagnostics` | DiagnosticEngine and probes |
| `data.report` | Sanitizer + generators |
| `data.database` / `data.datastore` / `data.repository` | Persistence |
| `core.security` | SecretStore, SecureWindow |
| `core.ad` | AD attribute helpers |
| `core.logging` / `core.util` | Sanitized logging, escaping, fingerprints |
| `ui.theme` / `ui.components` | Material theme and shared widgets |

## Non-goals (still)

- Multi-module over-engineering before need
- Cloud backends / telemetry
- Optional Kerberos client stacks beyond the embedded Kerby path already shipped

## Privacy and security posture

Credentials and directory content are treated as sensitive. Logging and exports sanitize by default. Backup and device-transfer extraction are denied. See [security.md](security.md) and [PRIVACY.md](../PRIVACY.md).
