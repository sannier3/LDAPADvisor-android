# Roadmap

This roadmap describes intended development phases. It does **not** include release
dates or delivery guarantees.

## Phase 0 — Project foundation

- Repository hygiene (Git, docs, CI)
- Stable Android project baseline
- Contribution and security guidelines

## Phase 1 — Connection profiles

- Local connection profile model
- Profile create / edit / delete
- Secure handling of saved credentials (design first)

## Phase 2 — LDAP / LDAPS / StartTLS

- Basic bind and search operations
- LDAPS support
- StartTLS support
- Clear TLS failure diagnostics

## Phase 3 — Active Directory discovery

- DNS SRV discovery for domain controllers
- Site / locator related checks where feasible on Android
- RootDSE inspection

## Phase 4 — LDAP Browser

- Tree / entry browsing
- Attribute inspection
- Advanced LDAP search

## Phase 5 — AD objects

- Users
- Groups
- Computers
- Organizational Units

## Phase 6 — Diagnostics

- DNS diagnostics
- TCP connectivity checks
- TLS certificate diagnostics
- Global Catalog diagnostics
- Kerberos-related diagnostics (where practical)

## Phase 7 — Administration

- Account unlock
- Account enable / disable
- Password reset over secure LDAP
- Group membership management

## Phase 8 — Reports

- Structured diagnostic reports
- Exportable technical summaries (sanitized by default)

## Phase 9 — Hardening and public beta

- Security review
- UX polish
- Documentation for operators
- Public beta readiness
