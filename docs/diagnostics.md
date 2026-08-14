# Diagnostics

`DiagnosticEngine` orchestrates a full run as a Kotlin `Flow` of progress events, then attaches advisor findings.

## Pipeline

1. **DNS** — `DnsDiagnosticService` / `AdDiscoveryService` (DC, GC, Kerberos SRV, host records)
2. **TCP** — `TcpDiagnosticService` concurrent port probes
3. **TLS** — `TlsDiagnosticService` when LDAPS (or GC TLS) applies; StartTLS noted separately
4. **LDAP** — `LdapDiagnosticService` against the current session (connection, bind identity, RootDSE, base search, paged-results advertisement)
5. **AD extras** (when AD RootDSE available):
   - Functional levels / GC readiness (`PasswordPolicyReader.functionalLevelResults`)
   - Domain password policy attributes
   - FSMO roles (`AdFsmoService`)
   - Trust objects (`AdTrustService`)
   - Kerberos / SASL GSS capability note (embedded Kerby; not OS Kerberos)
6. **Summary score** + **AdvisorEngine.evaluate**

## Score formula

The run score is the **integer average** of scored tests only:

| Status | Points |
|---|---|
| `SUCCESS` | 100 |
| `WARNING` | 60 |
| `ERROR` | 0 |

Statuses **excluded** from the average: `SKIPPED`, `UNSUPPORTED`, `INFO` (and any other non-scored status).

If there are no scored tests, the score is treated as empty / not meaningful (engine returns a neutral empty score path).

## Default TCP probes

53 (DNS TCP), 88 (Kerberos), 135 (RPC), 389 (LDAP), 445 (SMB), 636 (LDAPS), 3268 (GC), 3269 (GC TLS), 5985/5986 (WinRM), 9389 (ADWS).

Profile discovery also offers a per-DC **Test TCP** probe on the LDAP or LDAPS port matching the profile security mode (real sockets via `TcpDiagnosticService.probeHost`).

## TLS pin from diagnostic

When a TLS handshake result includes a SHA-256 fingerprint, Diagnostics can:

1. Copy the fingerprint to the clipboard
2. **Apply pin to active profile** — sets `trustMode=PINNED` and `pinnedFingerprint` via `ProfileRepository`

## UI

- Diagnostics screen: run / cancel, progress, score, result list, TLS pin actions
- Advisor screen: findings from latest run (includes incomplete TLS chain when evidence matches)
- History screen: latest run summary
- Reports: export latest run

## Honest limits

| Item | Status |
|---|---|
| DNS / TCP / TLS / LDAP protocol checks | Implemented |
| GC SRV + ports + RootDSE readiness | Implemented |
| Full Kerberos authentication test | Limited — bind uses Kerby when profile Auth=KERBEROS; diagnostic reports capability INFO |
| GSSAPI bind test | Implemented path available (embedded tokens); not a live KDC probe in diagnostics |
| Deep multi-DC health matrix | Limited (probes target profile host + discovery evidence; not a full enterprise monitoring suite) |

Runs and report metadata persist via Room (`DiagnosticRunDao`, `ReportMetaDao`) with retention controlled in settings.
