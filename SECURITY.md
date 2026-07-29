# Security Policy

## Reporting a vulnerability

This project handles financial data and user credentials. If you find a security vulnerability, **do not open a public issue**.

Email: [krtky@users.noreply.github.com](mailto:krtky@users.noreply.github.com)

You should receive a response within 48 hours.

## Known considerations

- Backup/restore JSON files **contain API keys and passwords** in plaintext. Delete backups when no longer needed.
- Gmail ingest is read-only: either an App Password (IMAP) or OAuth `gmail.readonly` (Gmail API). The app never sends email.
- All runtime secrets are stored in EncryptedSharedPreferences (AES-256 GCM) via `SecureStore`.
- Release signing materials (`*.jks`, `keystore.properties`) must stay **outside** version control (see `.gitignore`).
- Optional `.env` files are for local developer notes only and are gitignored; the Android app does not load them.

## What we scan for before publish

| Item | Status |
| --- | --- |
| Hardcoded API keys / tokens in source | Not present — user-entered at runtime |
| Keystore passwords in repo | `keystore.properties` + `*.jks` gitignored |
| `local.properties` (SDK paths) | Gitignored |
| Client secrets for Google | Not embedded — device account OAuth only |
