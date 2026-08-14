# Testing

## Commands

### Linux / macOS

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

### Windows

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

## Unit tests (current)

Located under `app/src/test/java/com/jbsan/ldapadvisor/`:

| Area | Examples |
|---|---|
| AD decoders | `UserAccountControlTest`, `SidDecoderTest`, `GuidDecoderTest`, `FileTimeConverterTest`, `GroupTypeDecoderTest`, `TrustAndFunctionalLevelTest`, `UnicodePwdEncoderTest` |
| Advisor | `AdvisorEngineTest` |
| Reports / logging | `ReportSanitizerTest`, `LogSanitizerTest`, `HtmlEscaperTest` |
| LDAP helpers | `LdapFilterEscaperTest`, `InMemoryLdapClientTest` |
| Utils | `FingerprintUtilsTest` |

These are offline unit tests. They do **not** require a live Active Directory.

## What is not covered yet

- Full integration tests against a real DC / LDAP lab
- Compose UI instrumentation coverage for every screen
- End-to-end Kerberos (intentionally unsupported)
- Automated security penetration suites

## Test data policy

Use only documentation examples:

```text
corp.example.com
dc01.corp.example.com
user@corp.example.com
DC=corp,DC=example,DC=com
```

Prefer RFC documentation IP ranges (`192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24`).

Never commit production credentials, dumps, or private certificates into tests.
