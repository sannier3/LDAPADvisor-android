# Privacy

**Effective for:** LDAPADvisor Android `0.1.0`

## Summary

LDAPADvisor does **not** include analytics, advertising, telemetry, crash-upload SaaS, or any vendor cloud backend. Directory work stays on your device except for network traffic you initiate to LDAP/DNS servers you configure.

## Data the app may store locally

| Data | Storage | Notes |
|---|---|---|
| Connection profiles | Room database | Hosts, ports, bind identity, TLS settings |
| Saved passwords / secrets | Android Keystore AES-GCM + private SharedPreferences | Not stored as plaintext in Room |
| App settings | DataStore | Theme, timeouts, read-only default, report sanitization, retention |
| Custom CA / trusted cert metadata | Room | When configured |
| Diagnostic runs / report metadata | Room | Evidence may include hostnames, DNs, and directory attributes |
| Shared report files | App cache (temporary) | Shared via FileProvider |

## Data shared off-device

- **Only** when you connect to configured LDAP/DNS servers, or when **you** export/share a report through the Android share sheet
- Reports never intentionally include bind passwords; enable sanitization when sharing externally

## Permissions

- `INTERNET` — LDAP/DNS connectivity
- `ACCESS_NETWORK_STATE` — connectivity awareness

## Backup

- `allowBackup` is disabled
- Backup / device-transfer rules exclude app databases, shared preferences, files, and related domains to reduce accidental secret or directory metadata export

## Children’s privacy / accounts

The app does not create online accounts with the project authors and does not target children as a social service. It is an IT administration tool.

## Changes

Privacy behavior may evolve with the app. Review this file and in-app Privacy text when upgrading.
