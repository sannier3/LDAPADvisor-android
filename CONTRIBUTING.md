# Contributing to LDAPADvisor Android

Thank you for your interest in contributing. This project is in early development. Small, focused changes are preferred.

## Getting started

1. Clone the repository:

```bash
git clone https://github.com/sannier3/LDAPADvisor-android.git
cd LDAPADvisor-android
```

2. Open the project in Android Studio, or build from the command line with the Gradle Wrapper.

3. Create a topic branch from `main`:

```bash
git checkout -b feature/your-change
```

## Branch naming

Use prefixes that describe the intent:

```text
feature/...
fix/...
refactor/...
docs/...
test/...
chore/...
```

Examples:

```text
feature/ldap-browser
feature/ad-discovery
fix/ldaps-timeout
docs/readme-update
```

## Building

### Linux / macOS

```bash
./gradlew assembleDebug
```

### Windows

```powershell
.\gradlew.bat assembleDebug
```

## Testing

### Linux / macOS

```bash
./gradlew test
./gradlew lint
```

### Windows

```powershell
.\gradlew.bat test
.\gradlew.bat lint
```

## Commit style

Prefer [Conventional Commits](https://www.conventionalcommits.org/):

```text
feat:
fix:
docs:
refactor:
test:
build:
ci:
chore:
```

Examples:

```text
feat: add LDAP connection profile model
fix: handle TLS handshake timeout
docs: update Android build instructions
test: add SID conversion tests
```

## Pull request checklist

Before opening a Pull Request, ensure that:

- the project builds successfully
- existing tests still pass
- no secrets, credentials, private keys, or production Active Directory data are included
- the change is focused on one coherent purpose
- documentation is updated when behavior or setup changes

## Security and sensitive data

Never commit:

- LDAP / Active Directory credentials
- private certificates or keystores
- production hostnames, IPs, or directory dumps
- signing keys or CI tokens

Use documentation domains such as `corp.example.com` and RFC documentation IP ranges in examples.

## Code of conduct

Please follow the [Code of Conduct](CODE_OF_CONDUCT.md).
