# Reports

## Formats

`ReportGenerator` builds three export formats from a `DiagnosticRun`:

| Format | Content |
|---|---|
| HTML | Summary counts, findings, test table, recommendations (HTML-escaped) |
| JSON | Structured run payload via Kotlin Serialization |
| TXT | Plain-text summary for tickets / paste |

## Sanitizer

`ReportSanitizer` (default on share unless the user disables the switch / setting):

- Redacts emails, IPv4/IPv6, DN-like strings
- Redacts domain hints and FQDNs under those domains
- Redacts `password` / `unicodePwd` / `secret` assignment patterns
- Never designed to embed bind passwords

## Delivery

- Files written under the app cache `reports/` directory
- Shared through Android `FileProvider` + system share sheet (`ReportsViewModel.share`)
- Report metadata can be retained in Room (`ReportMetaEntity`) with history retention days from settings

## Inputs

Reports are generated from the latest diagnostic run (tests + advisor findings + score). Run a Diagnostics pass first if the Reports screen shows no run.
