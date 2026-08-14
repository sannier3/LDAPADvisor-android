# Dependencies

Versions are centralized in `gradle/libs.versions.toml` and applied from `app/build.gradle.kts`.

## Platform / build

| Component | Version (catalog) | Role |
|---|---|---|
| Android Gradle Plugin | 9.3.1 | Application plugin |
| Kotlin | 2.2.10 | Language + Compose / Serialization / KSP plugins |
| KSP | 2.2.10-2.0.2 | Room compiler |
| `compileSdk` / `targetSdk` | 37 | Local SDK 36 not installed |
| `minSdk` | 24 | Android 7.0+ |
| Core library desugaring | `desugar_jdk_libs` 2.1.5 | `java.time` and related APIs on older devices |
| Dependabot | weekly | Gradle + GitHub Actions (`.github/dependabot.yml`) |

## Runtime libraries (implemented)

| Library | Artifact | Version | Purpose |
|---|---|---|---|
| UnboundID LDAP SDK | `com.unboundid:unboundid-ldapsdk` | 7.0.3 | LDAP/LDAPS/StartTLS client |
| MiniDNS HLA | `org.minidns:minidns-hla` | 1.0.5 | DNS lookups (A/AAAA/SRV) |
| MiniDNS Android | `org.minidns:minidns-android21` | 1.0.5 | Android DNS integration |
| AndroidX Core KTX | `androidx.core:core-ktx` | 1.16.0 | Platform helpers / FileProvider |
| Lifecycle | runtime / compose / viewmodel-compose | 2.9.1 | UI state |
| Activity Compose | `androidx.activity:activity-compose` | 1.10.1 | Compose host |
| Compose BOM | `androidx.compose:compose-bom` | 2026.02.01 | Compose UI alignment |
| Material 3 | compose material3 + adaptive | adaptive 1.1.0 | UI |
| Navigation Compose | `androidx.navigation:navigation-compose` | 2.9.0 | Navigation |
| Room | runtime / ktx / compiler | 2.7.1 | Local DB |
| DataStore Preferences | `androidx.datastore:datastore-preferences` | 1.1.7 | Settings |
| Kotlin Coroutines | `kotlinx-coroutines-android` | 1.10.2 | Async I/O |
| Kotlin Serialization JSON | `kotlinx-serialization-json` | 1.8.1 | Report JSON |

## Test libraries

| Library | Version | Purpose |
|---|---|---|
| JUnit 4 | 4.13.2 | Unit tests |
| `kotlinx-coroutines-test` | 1.10.2 | Coroutine tests |
| AndroidX JUnit / Espresso / Compose UI Test | per BOM / catalog | Instrumented / Compose test scaffolding |

## Catalog entries not wired in `app/build.gradle.kts`

These appear in `libs.versions.toml` but are **not** current app dependencies:

- `androidx.work:work-runtime-ktx`
- `androidx.security:security-crypto`

## Explicitly not used

- Firebase, analytics, ads, crash cloud backends
- Third-party LDAP wrappers beyond UnboundID
- Apache Kerby (`kerb-client`) — embedded Kerberos client for AS/TGS + AP-REQ (SASL bind tokens built in-app; `kerb-gssapi` not used because it needs JDK `sun.security.jgss`)

See also [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).
