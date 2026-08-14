# Limitations

Honest constraints for operators evaluating `0.1.0`.

## Authentication

- **Kerberos authentication:** implemented via embedded Apache Kerby (not Android OS Kerberos). Requires reachable KDC (TCP/UDP 88), correct realm/SPN, and compatible enctypes (AES preferred).
- **GSSAPI / SASL GSS-SPNEGO bind:** implemented using app-built tokens + UnboundID `GenericSASLBindRequest`. Mutual-auth multi-round continuation is still limited.
- Only anonymous and simple bind are available

## Object provisioning UI

- Create user / create group / create OU screens are **limited message placeholders**
- LDAP `add` exists in the engine, but there is no production-ready provisioning UI

## Custom CA UX

- TLS CUSTOM_CA mode and Room CA storage exist
- Profile editor only exposes a **Custom CA ID** field — no certificate file picker / PEM import UI yet

## Directory coverage

- Not a full replacement for RSAT / ADUC / enterprise consoles
- Nested group expansion and large memberships may be truncated in UI (engine supports range retrieval / nested search)
- Schema screen lists names; it is not a full schema browser/editor
- Multi-forest / complex trust graphs are summarized from LDAP trust attributes, not a full trust path analyzer

## Diagnostics

- Kerberos checks are locator/port oriented, not ticket/auth success
- Concurrency and timeouts are configurable but not a continuous monitoring product
- Score is a simple weighted heuristic over SUCCESS/WARNING/ERROR tests

## Platform / packaging

- Early development; not intended for production AD environments without careful review
- `compileSdk` / `targetSdk` 37 locally (SDK 36 unavailable on the documented build machine)
- Cleartext traffic permitted at the app level to allow intentional LDAP:389 use
- No WorkManager background diagnostic scheduling in the current dependency set

## Privacy / sharing risk

- Unsanitized report export can include hostnames, DNs, and other directory evidence — use sanitization for external sharing
- Device compromise can still expose Keystore-protected material under advanced attack scenarios

See [implementation-status.md](implementation-status.md) for the full feature matrix.
