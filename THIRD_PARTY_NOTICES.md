# Third-party notices

LDAPADvisor Android redistributes or links against the following third-party components.
License names below reflect upstream declarations for the versions used in this repository.
This file is informational; full license texts are available from the upstream projects and from dependency artifacts.

The LDAPADvisor application itself is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

---

## UnboundID LDAP SDK for Java

- **Artifact:** `com.unboundid:unboundid-ldapsdk` (7.0.3)
- **Upstream:** Ping Identity / UnboundID LDAP SDK
- **License (recommended / primary for modern releases):** Apache License, Version 2.0
- **Also offered upstream (legacy / alternative):** GNU GPLv2; GNU LGPLv2.1; UnboundID LDAP SDK Free Use License
- **Notes:** As of LDAP SDK 5.0.0+, Apache License 2.0 is the recommended license for new use. See https://github.com/pingidentity/ldapsdk and https://docs.ldap.com/ldap-sdk/docs/LICENSE.txt

---

## MiniDNS

- **Artifacts:** `org.minidns:minidns-hla`, `org.minidns:minidns-android21` (1.0.5)
- **Upstream:** https://github.com/MiniDNS/minidns
- **License:** Dual / choice — Apache License, Version 2.0; **or** GNU Lesser General Public License (LGPL) version 2.1 or later; **or** WTFPL (per upstream `LICENCE`)

---

## AndroidX / Jetpack libraries

Including (non-exhaustive): Core KTX, Lifecycle, Activity, Compose UI, Material 3, Navigation, Room, DataStore, and related test libraries as declared in `gradle/libs.versions.toml` / `app/build.gradle.kts`.

- **License:** Apache License, Version 2.0
- **Upstream:** https://developer.android.com/jetpack

---

## Kotlin / kotlinx

- **Kotlin standard library and compiler plugins** (version catalog Kotlin 2.2.10)
- **kotlinx-coroutines-android** / **kotlinx-coroutines-test**
- **kotlinx-serialization-json**

- **License:** Apache License, Version 2.0
- **Upstream:** https://github.com/JetBrains/kotlin , https://github.com/Kotlin/kotlinx.coroutines , https://github.com/Kotlin/kotlinx.serialization

---

## Android tools — core library desugaring

- **Artifact:** `com.android.tools:desugar_jdk_libs` (2.1.5)
- **License:** GNU General Public License, version 2, with the **Classpath Exception** (as published by the Android / OpenJDK desugar project packaging)
- **Upstream:** Android Open Source Project / Android tools desugar libraries

---

## JUnit 4 (tests)

- **Artifact:** `junit:junit` (4.13.2)
- **License:** Eclipse Public License 1.0 (EPL-1.0)
- **Upstream:** https://junit.org/junit4/

---

## AndroidX Test / Espresso / Compose UI Test (androidTest / debug)

- **License:** Apache License, Version 2.0
- **Upstream:** AndroidX Test + Compose test artifacts via Compose BOM

---

## Google KSP

- **Plugin:** `com.google.devtools.ksp`
- **License:** Apache License, Version 2.0
- **Upstream:** https://github.com/google/ksp

---

## Notices

- Versions change over time; consult `gradle/libs.versions.toml` for the pinned set.
- Catalog entries that are **not** currently wired into `app/build.gradle.kts` (for example WorkManager or Security Crypto) are omitted from the runtime notice list above.
- If a dependency’s license text must be shipped verbatim in a release artifact, include the upstream LICENSE files alongside the APK/AAB distribution as required by that license.
