# Architecture (draft)

This document describes **intended** architectural principles for LDAPADvisor Android.
The final design will evolve as features are implemented. Do not treat this as a frozen
specification.

## Current state

The repository currently contains a lightweight single-module Android application
generated with Jetpack Compose and Material 3. Domain packages for LDAP, discovery,
and diagnostics are **not** introduced yet.

## Guiding principles

- **Single-activity** Android application
- **Jetpack Compose** UI
- **MVVM** presentation pattern
- **Repositories** as the boundary for data access
- **Specialized services** for LDAP, DNS, TLS, and diagnostics (when introduced)
- **Kotlin coroutines** for asynchronous work
- **StateFlow** (or equivalent unidirectional state holders) for UI state
- Clear separation between **UI**, **domain**, and **data** layers
- Network and directory I/O **off the main thread**
- **Secure storage** for future secrets (for example Android Keystore-backed storage)
- **No LDAP/Active Directory data sent to third-party services**

## Layering (planned)

```text
UI (Compose) → ViewModels → Domain use cases → Repositories → Data sources / services
```

## Non-goals (for now)

- Multi-module over-engineering before feature needs exist
- Cloud backends or telemetry
- Premature introduction of LDAP libraries without a concrete use case

## Privacy and security posture

Future implementations must assume credentials and directory content are sensitive.
Logging, crash reports, and exports must be designed to avoid leaking secrets or
production infrastructure details by default.
