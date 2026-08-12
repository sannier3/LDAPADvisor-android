# Security Policy

## Supported versions

LDAPADvisor Android is in early development. There is no stable release yet. Security fixes will target the `main` branch.

## Potential security concerns

Reports may involve issues such as:

- credential exposure
- LDAP credential leakage
- TLS certificate validation
- insecure LDAP connections
- Android Keystore misuse
- exported Android components
- intent exposure
- sensitive information in logs
- sensitive information in diagnostic reports
- authentication bypass

## Reporting a vulnerability

Do **not** open a public issue that includes real credentials, private certificates, production Active Directory data, or a working exploit against a live environment.

Private vulnerability reporting channels will be documented here once maintainers publish a preferred contact method.

Until then:

1. Avoid publishing sensitive details publicly.
2. Sanitize all examples (use `corp.example.com` and documentation IP ranges).
3. Describe impact, affected components, and reproduction steps at a high level when contacting maintainers through a private channel once available.

## Safe handling expectations

Contributors and users should assume that directory credentials and internal infrastructure details are highly sensitive. Never commit secrets, signing keys, or production directory exports to this repository.
